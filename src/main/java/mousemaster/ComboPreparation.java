package mousemaster;

import mousemaster.ComboMove.KeyComboMove;
import mousemaster.ComboMove.PressComboMove;
import mousemaster.ComboMove.ReleaseComboMove;
import mousemaster.ComboMove.TapComboMove;
import mousemaster.ComboMove.WaitComboMove;
import mousemaster.MoveSet.KeyMoveSet;
import mousemaster.MoveSet.WaitMoveSet;
import mousemaster.ResolvedKeyComboMove.ResolvedPressComboMove;
import mousemaster.ResolvedKeyComboMove.ResolvedReleaseComboMove;

import java.time.Duration;
import java.util.*;

public record ComboPreparation(List<KeyEvent> events) {

    public static ComboPreparation empty() {
        return new ComboPreparation(new ArrayList<>());
    }

    /**
     * Matches the most recent events in this preparation against a combo sequence.
     * A combo sequence is an ordered list of MoveSets. Each MoveSet contains required
     * and optional moves that can be matched in any order.
     * <p>
     * Example: sequence [{+a #b?} +c] with events [+a +c] matches the first MoveSet
     * with just +a (skipping optional #b), then the second MoveSet with +c.
     * Alias moves are matched against any key in the alias's key set, with consistency:
     * once an alias is bound to a key, all other moves using the same alias must match
     * that same key.
     * <p>
     * The match is "complete" if all MoveSets are matched, "partial" if only a prefix is.
     * We try the longest (most MoveSets) match first, and for each, the most events first.
     * The matched events are always a suffix of the preparation (most recent events).
     */
    public ComboSequenceMatch match(ComboSequence sequence) {
        List<MoveSet> moveSets = sequence.moveSets();
        if (moveSets.isEmpty())
            return new ComboSequenceMatch(List.of(), true, 0, -1, false, Set.of(), new AliasResolution(Map.of(), Map.of()));
        // A sequence that is only wait moves (e.g. "wait-2000") has no event-based moves.
        // It is "complete" with 0 matched events: ComboWatcher handles the wait duration.
        boolean allWait = moveSets.stream().allMatch(ms -> ms instanceof WaitMoveSet);
        if (allWait)
            return new ComboSequenceMatch(List.of(), true, moveSets.size(), -1, false, Set.of(), new AliasResolution(Map.of(), Map.of()));
        if (events.isEmpty())
            return ComboSequenceMatch.noMatch();

        // Try matching the first K moveSets, starting with K = all (complete match)
        // and decreasing to K = 1 (partial match of just the first MoveSet).
        for (int k = moveSets.size(); k >= 1; k--) {
            List<MoveSet> subMoveSets = moveSets.subList(0, k);
            // Skip if all subMoveSets are wait-only (no events to match).
            if (subMoveSets.stream().allMatch(ms -> ms instanceof WaitMoveSet))
                continue;
            int minTotalEventCount = 0, maxTotalEventCount = 0;
            boolean hasAbsorbingWait = false;
            for (MoveSet moveSet : subMoveSets) {
                minTotalEventCount += moveSet.minMoveCount();
                maxTotalEventCount += moveSet.maxMoveCount();
                if (moveSet.canAbsorbEvents())
                    hasAbsorbingWait = true;
            }
            // Compute partial minimum: allow the last KeyMoveSet to match with
            // fewer events (down to 1) so that partial matches are detected.
            int partialMinTotalEventCount = minTotalEventCount;
            MoveSet lastSubMoveSet = subMoveSets.getLast();
            if (lastSubMoveSet instanceof KeyMoveSet lastKms && lastKms.minMoveCount() > 1)
                partialMinTotalEventCount = minTotalEventCount - lastKms.minMoveCount() + 1;
            if (partialMinTotalEventCount > events.size())
                continue;
            // Absorbing waits can consume arbitrary events, so max is events.size().
            // But only if the preparation actually contains events that could be
            // absorbed (i.e. keys in the ignored key set). If no event can be
            // absorbed, the absorption is a no-op and max stays at maxTotalEventCount.
            boolean canAbsorbPreparationEvents = false;
            if (hasAbsorbingWait) {
                for (MoveSet moveSet : subMoveSets) {
                    if (!moveSet.canAbsorbEvents())
                        continue;
                    WaitComboMove waitMove = moveSet instanceof WaitMoveSet wms ?
                            wms.waitMove() :
                            ((KeyMoveSet) moveSet).waitMove();
                    for (KeyEvent event : events) {
                        if (waitMove.matchesEvent(event)) {
                            canAbsorbPreparationEvents = true;
                            break;
                        }
                    }
                    if (canAbsorbPreparationEvents)
                        break;
                }
            }
            int effectiveMaxEventCount = canAbsorbPreparationEvents ?
                    events.size() : Math.min(maxTotalEventCount, events.size());

            // Quick-reject for absorbing combos: check that the preparation
            // contains at least one event matching a required move of each
            // KeyMoveSet. Without such events, no suffix can produce a match.
            if (hasAbsorbingWait && !preparationContainsRequiredKeyMoves(subMoveSets))
                continue;

            // For combos starting with a WaitMoveSet that absorbs all keys (#{*}),
            // only the longest suffix needs to be tried. The wait's absorption
            // loop already tries every position within the suffix, so shorter
            // suffixes are redundant.
            if (subMoveSets.getFirst() instanceof WaitMoveSet wms
                    && wms.waitMove() instanceof WaitComboMove.KeyWaitComboMove kwm
                    && kwm.ignoredKeySet().equals(KeySet.ALL)) {
                partialMinTotalEventCount = effectiveMaxEventCount;
            }

            // Try consuming totalEventCount events from the end of the preparation,
            // starting with the most events (greediest match) first.
            // Full matches (totalEventCount >= minTotalEventCount) are tried before
            // partial matches (totalEventCount < minTotalEventCount).
            for (int totalEventCount = effectiveMaxEventCount;
                 totalEventCount >= partialMinTotalEventCount; totalEventCount--) {
                boolean isPartial = totalEventCount < minTotalEventCount;
                int regionBeginIndex = events.size() - totalEventCount;
                List<ResolvedKeyComboMove> matchedKeyMoves = new ArrayList<>();
                Map<String, Key> aliasBindings = new HashMap<>();
                Map<String, Key> negatedBindings = new HashMap<>();
                Map<String, List<Key>> tapExpandedFromAliasBindings = new HashMap<>();
                boolean[] lastEventAbsorbed = {false};
                int[] lastKeyMoveEventIndex = {-1};
                Set<Key> absorbedPressedKeys = new HashSet<>();
                boolean hasFollowingMoveSets = k < moveSets.size();
                boolean[] hasDanglingTapPress = {false};
                boolean assigned = tryAssignEventsToMoveSets(subMoveSets, 0, regionBeginIndex,
                        regionBeginIndex + totalEventCount, regionBeginIndex,
                        matchedKeyMoves, aliasBindings, negatedBindings,
                        tapExpandedFromAliasBindings,
                        lastEventAbsorbed, lastKeyMoveEventIndex,
                        absorbedPressedKeys, isPartial, hasFollowingMoveSets,
                        hasDanglingTapPress);
                if (assigned) {
                    boolean complete = !isPartial && (k == moveSets.size())
                                       && !hasDanglingTapPress[0];
                    if (matchedKeyMoves.isEmpty() && !complete) {
                        // Pure absorption: all events absorbed, no key moves
                        // matched, and the match is incomplete. Try smaller
                        // suffixes that might produce actual key moves (e.g.
                        // a suffix containing just the press of a
                        // tap-matching key).
                        continue;
                    }
                    int moveSetCount = isPartial ? k - 1 : k;
                    return new ComboSequenceMatch(List.copyOf(matchedKeyMoves), complete,
                            moveSetCount, lastKeyMoveEventIndex[0], lastEventAbsorbed[0],
                            Set.copyOf(absorbedPressedKeys),
                            new AliasResolution(Map.copyOf(aliasBindings),
                                    Map.copyOf(negatedBindings),
                                    deepCopyOf(tapExpandedFromAliasBindings)));
                }
            }
        }
        return ComboSequenceMatch.noMatch();
    }

    /**
     * Checks that for each KeyMoveSet in the list, the preparation contains at
     * least one event that could match one of its required moves. Uses empty
     * alias/negated bindings (permissive: no false negatives for alias moves).
     */
    private boolean preparationContainsRequiredKeyMoves(List<MoveSet> moveSets) {
        for (MoveSet ms : moveSets) {
            if (ms instanceof KeyMoveSet kms && !kms.requiredMoves().isEmpty()) {
                boolean found = false;
                for (KeyEvent event : events) {
                    for (KeyComboMove move : kms.requiredMoves()) {
                        if (move instanceof TapComboMove tap) {
                            if (tap.keyOrAlias().matchesKey(event.key())) {
                                found = true;
                                break;
                            }
                        }
                        else if (pressOrReleaseMoveMatchesEvent(move, event, Map.of(), Map.of())) {
                            found = true;
                            break;
                        }
                    }
                    if (found)
                        break;
                }
                if (!found)
                    return false;
            }
        }
        return true;
    }

    /**
     * Recursively partitions a contiguous range of events among consecutive MoveSets.
     * Each MoveSet consumes between minMoveCount and maxMoveCount events.
     * The events assigned to each MoveSet must match that MoveSet (checked by
     * tryMatchMoveSetEvents). Tries the greediest partition first (give as many
     * events as possible to the current MoveSet).
     */
    private boolean tryAssignEventsToMoveSets(
            List<MoveSet> moveSets, int moveSetIndex, int eventIndex,
            int eventEndIndex, int regionBeginIndex,
            List<ResolvedKeyComboMove> matchedKeyMoves, Map<String, Key> aliasBindings,
            Map<String, Key> negatedBindings,
            Map<String, List<Key>> tapExpandedFromAliasBindings,
            boolean[] lastEventAbsorbed, int[] lastKeyMoveEventIndex,
            Set<Key> absorbedPressedKeys, boolean allowPartialLastMoveSet,
            boolean hasFollowingMoveSets, boolean[] hasDanglingTapPress) {
        if (moveSetIndex == moveSets.size())
            return eventIndex == eventEndIndex;

        MoveSet moveSet = moveSets.get(moveSetIndex);

        // Wait MoveSets: absorb ignored events, then skip to next MoveSet.
        if (moveSet instanceof WaitMoveSet waitMoveSet) {
            WaitComboMove waitMove = waitMoveSet.waitMove();
            if (waitMoveSet.canAbsorbEvents()) {
                // Absorbing wait: try consuming 0..maxAbsorb events.
                int minForRemaining = 0;
                for (int later = moveSetIndex + 1; later < moveSets.size(); later++) {
                    MoveSet laterMoveSet = moveSets.get(later);
                    if (allowPartialLastMoveSet && later == moveSets.size() - 1
                        && laterMoveSet instanceof KeyMoveSet kms && kms.minMoveCount() > 1)
                        minForRemaining += 1;
                    else
                        minForRemaining += laterMoveSet.minMoveCount();
                }
                int maxAbsorb = (eventEndIndex - eventIndex) - minForRemaining;
                // Can't absorb past a non-ignored event.
                int firstNonIgnoredOffset = maxAbsorb;
                for (int i = 0; i < maxAbsorb; i++) {
                    if (!waitMove.matchesEvent(events.get(eventIndex + i))) {
                        firstNonIgnoredOffset = i;
                        break;
                    }
                }
                // Find the next KeyMoveSet to skip absorbed events
                // where the first event after the wait can't match the KeyMoveSet.
                KeyMoveSet peekMoveSet = null;
                for (int p = moveSetIndex + 1; p < moveSets.size(); p++) {
                    if (moveSets.get(p) instanceof KeyMoveSet kms) {
                        peekMoveSet = kms;
                        break;
                    }
                }
                for (int absorb = firstNonIgnoredOffset; absorb >= 0; absorb--) {
                    int nextEventIndex = eventIndex + absorb;
                    // Check time gap from pre-wait event to first post-wait event.
                    // For mid-sequence waits, the pre-wait event is the one before
                    // this MoveSet. For leading waits, use the event just before the
                    // matched suffix (if any) to enforce the wait's min duration.
                    if (nextEventIndex < eventEndIndex) {
                        int previousEventIndex = eventIndex > regionBeginIndex ?
                                eventIndex - 1 : regionBeginIndex - 1;
                        if (previousEventIndex >= 0) {
                            KeyEvent previousEvent = events.get(previousEventIndex);
                            KeyEvent nextEvent = events.get(nextEventIndex);
                            if (!waitMove.duration().satisfied(previousEvent.time(), nextEvent.time()))
                                continue;
                        }
                    }
                    if (peekMoveSet != null && peekMoveSet.minMoveCount() > 0 &&
                            nextEventIndex < eventEndIndex) {
                        KeyEvent peekEvent = events.get(nextEventIndex);
                        if (!anyMoveCouldMatchEvent(peekMoveSet, peekEvent, aliasBindings,
                                negatedBindings))
                            continue;
                    }
                    // Track whether the last event in the preparation is absorbed.
                    int lastEventIndex = events.size() - 1;
                    boolean absorbsLastEvent =
                            absorb > 0 && lastEventIndex >= eventIndex &&
                            lastEventIndex < nextEventIndex;
                    boolean savedLastEventAbsorbed = lastEventAbsorbed[0];
                    if (absorbsLastEvent)
                        lastEventAbsorbed[0] = true;
                    // Collect absorbed press keys.
                    Set<Key> addedAbsorbedKeys = new HashSet<>();
                    for (int i = 0; i < absorb; i++) {
                        KeyEvent absorbed = events.get(eventIndex + i);
                        if (absorbed.isPress() && absorbedPressedKeys.add(absorbed.key()))
                            addedAbsorbedKeys.add(absorbed.key());
                    }
                    // Pass nextEventIndex as regionBeginIndex so the first event
                    // after the wait skips the per-move duration check (the wait
                    // already validated the time gap).
                    if (tryAssignEventsToMoveSets(moveSets, moveSetIndex + 1,
                            nextEventIndex, eventEndIndex, nextEventIndex,
                            matchedKeyMoves, aliasBindings, negatedBindings,
                            tapExpandedFromAliasBindings,
                            lastEventAbsorbed, lastKeyMoveEventIndex,
                            absorbedPressedKeys, allowPartialLastMoveSet,
                            hasFollowingMoveSets, hasDanglingTapPress))
                        return true;
                    lastEventAbsorbed[0] = savedLastEventAbsorbed;
                    absorbedPressedKeys.removeAll(addedAbsorbedKeys);
                }
                return false;
            }
            else {
                // Non-absorbing wait (plain wait, no key is ignored): consume 0 events.
                if (eventIndex > regionBeginIndex && eventIndex < eventEndIndex) {
                    KeyEvent previousEvent = events.get(eventIndex - 1);
                    KeyEvent nextEvent = events.get(eventIndex);
                    if (!waitMove.duration().satisfied(previousEvent.time(), nextEvent.time()))
                        return false;
                }
                // Pass eventIndex as regionBeginIndex so the first event
                // after the wait skips the per-move duration check.
                return tryAssignEventsToMoveSets(moveSets, moveSetIndex + 1,
                        eventIndex, eventEndIndex, eventIndex,
                        matchedKeyMoves, aliasBindings, negatedBindings,
                        tapExpandedFromAliasBindings,
                        lastEventAbsorbed, lastKeyMoveEventIndex,
                        absorbedPressedKeys, allowPartialLastMoveSet,
                        hasFollowingMoveSets, hasDanglingTapPress);
            }
        }

        KeyMoveSet keyMoveSet = (KeyMoveSet) moveSet;
        int remainingEventCount = eventEndIndex - eventIndex;
        int minEventCountForRemainingMoveSets = 0;
        for (int later = moveSetIndex + 1; later < moveSets.size(); later++) {
            MoveSet laterMoveSet = moveSets.get(later);
            if (allowPartialLastMoveSet && later == moveSets.size() - 1
                && laterMoveSet instanceof KeyMoveSet kms && kms.minMoveCount() > 1)
                minEventCountForRemainingMoveSets += 1;
            else
                minEventCountForRemainingMoveSets += laterMoveSet.minMoveCount();
        }
        int maxEventCountForMoveSet;
        if (keyMoveSet.canAbsorbEvents()) {
            maxEventCountForMoveSet = remainingEventCount - minEventCountForRemainingMoveSets;
        }
        else {
            maxEventCountForMoveSet = Math.min(keyMoveSet.maxMoveCount(),
                    remainingEventCount - minEventCountForRemainingMoveSets);
        }

        // For the last MoveSet, allow eventCount down to 1 if partial is allowed.
        int minEventCount = (allowPartialLastMoveSet
            && moveSetIndex == moveSets.size() - 1
            && keyMoveSet.minMoveCount() > 1) ? 1 : keyMoveSet.minMoveCount();
        for (int eventCount = maxEventCountForMoveSet;
             eventCount >= minEventCount; eventCount--) {
            List<ResolvedKeyComboMove> moveSetMatchedKeyMoves = new ArrayList<>();
            Map<String, Key> savedAliasBindings = new HashMap<>(aliasBindings);
            Map<String, Key> savedNegatedBindings = new HashMap<>(negatedBindings);
            boolean[] moveSetLastEventAbsorbed = {false};
            Set<Key> moveSetAbsorbedPressedKeys = new HashSet<>();
            boolean allowLeadingIgnored = true;
            boolean allowTrailingIgnored = moveSetIndex + 1 < moveSets.size()
                    || hasFollowingMoveSets;
            boolean savedHasDanglingTapPress = hasDanglingTapPress[0];
            if (tryMatchMoveSetEvents(eventIndex, eventCount, keyMoveSet,
                    regionBeginIndex, matchedKeyMoves, moveSetMatchedKeyMoves,
                    aliasBindings, negatedBindings, tapExpandedFromAliasBindings,
                    moveSetLastEventAbsorbed, moveSetAbsorbedPressedKeys,
                    allowLeadingIgnored, allowTrailingIgnored,
                    hasDanglingTapPress)) {
                int savedSize = matchedKeyMoves.size();
                int savedLastKeyMoveEventIndex = lastKeyMoveEventIndex[0];
                boolean savedLastEventAbsorbed = lastEventAbsorbed[0];
                matchedKeyMoves.addAll(moveSetMatchedKeyMoves);
                lastKeyMoveEventIndex[0] = eventIndex + eventCount - 1;
                if (moveSetLastEventAbsorbed[0])
                    lastEventAbsorbed[0] = true;
                absorbedPressedKeys.addAll(moveSetAbsorbedPressedKeys);
                if (hasDanglingTapPress[0]) {
                    // Dangling tap press: the combo is waiting for the
                    // release. Don't assign remaining events to subsequent
                    // move sets: only succeed if all events are consumed.
                    if (eventIndex + eventCount == eventEndIndex)
                        return true;
                }
                else if (tryAssignEventsToMoveSets(moveSets, moveSetIndex + 1,
                        eventIndex + eventCount, eventEndIndex,
                        regionBeginIndex, matchedKeyMoves, aliasBindings,
                        negatedBindings, tapExpandedFromAliasBindings,
                        lastEventAbsorbed, lastKeyMoveEventIndex,
                        absorbedPressedKeys, allowPartialLastMoveSet,
                        hasFollowingMoveSets, hasDanglingTapPress))
                    return true;
                lastKeyMoveEventIndex[0] = savedLastKeyMoveEventIndex;
                lastEventAbsorbed[0] = savedLastEventAbsorbed;
                absorbedPressedKeys.removeAll(moveSetAbsorbedPressedKeys);
                while (matchedKeyMoves.size() > savedSize)
                    matchedKeyMoves.removeLast();
            }
            hasDanglingTapPress[0] = savedHasDanglingTapPress;
            // Restore aliasBindings and negatedBindings on backtrack.
            aliasBindings.clear();
            aliasBindings.putAll(savedAliasBindings);
            negatedBindings.clear();
            negatedBindings.putAll(savedNegatedBindings);
        }
        return false;
    }

    /**
     * Tries to match eventCount consecutive events (starting at eventBeginIndex)
     * against a single KeyMoveSet. All required moves must be matched. The remaining
     * event slots (eventCount - requiredCount) are filled by selecting that many
     * optional moves.
     * <p>
     * Duration constraint: the time between consecutive events must satisfy the
     * duration of the previous matched key move. The first event in the matched region
     * has no duration constraint.
     * <p>
     * When the KeyMoveSet can absorb events (has ignored keys), events that don't match
     * any move can be ignored if they match the ignoredKeySet.
     */

    private boolean tryMatchMoveSetEvents(
            int eventBeginIndex, int eventCount, KeyMoveSet keyMoveSet,
            int regionBeginIndex, List<ResolvedKeyComboMove> previousMatchedKeyMoves,
            List<ResolvedKeyComboMove> matchedKeyMoves, Map<String, Key> aliasBindings,
            Map<String, Key> negatedBindings,
            Map<String, List<Key>> tapExpandedFromAliasBindings,
            boolean[] lastEventAbsorbed, Set<Key> absorbedPressedKeys,
            boolean allowLeadingIgnored, boolean allowTrailingIgnored,
            boolean[] hasDanglingTapPress) {
        if (eventCount == 0)
            return keyMoveSet.minMoveCount() == 0;

        List<KeyComboMove> required = keyMoveSet.requiredMoves();
        List<KeyComboMove> optional = keyMoveSet.optionalMoves();
        int requiredSlots = keyMoveSet.requiredMoveSlotCount();
        int optionalSlotsToFill = eventCount - requiredSlots;
        if (optionalSlotsToFill < 0) {
            // Partial matching: select eventCount slots from all moves.
            List<KeyComboMove> allMoves = new ArrayList<>(required);
            allMoves.addAll(optional);
            if (tryOptionalSubsets(eventBeginIndex, eventCount,
                    allMoves, eventCount, 0, new ArrayList<>(),
                    regionBeginIndex, previousMatchedKeyMoves, matchedKeyMoves,
                    aliasBindings, negatedBindings, tapExpandedFromAliasBindings))
                return true;
            // Tap-press partial match fallback (partial path: all moves optional).
            if (tryTapPressPartialMatch(eventBeginIndex, eventCount, allMoves,
                    0, regionBeginIndex, previousMatchedKeyMoves,
                    matchedKeyMoves, aliasBindings, negatedBindings,
                    tapExpandedFromAliasBindings)) {
                hasDanglingTapPress[0] = true;
                return true;
            }
            // Absorbing wait fallback: events that don't match any move can
            // be absorbed. Tried last so that key-move matches (including
            // dangling tap presses) are preferred over pure absorption.
            if (keyMoveSet.canAbsorbEvents()) {
                return tryMatchMoveSetEventsWithIgnoredKeys(
                        eventBeginIndex, eventCount, keyMoveSet, 0,
                        regionBeginIndex, previousMatchedKeyMoves,
                        matchedKeyMoves, aliasBindings, negatedBindings,
                        tapExpandedFromAliasBindings,
                        lastEventAbsorbed, absorbedPressedKeys,
                        allowLeadingIgnored, allowTrailingIgnored);
            }
            return false;
        }
        int maxOptionalSlots = keyMoveSet.maxMoveCount() - requiredSlots;
        if (optionalSlotsToFill > maxOptionalSlots) {
            if (keyMoveSet.canAbsorbEvents()) {
                return tryMatchMoveSetEventsWithIgnoredKeys(
                        eventBeginIndex, eventCount, keyMoveSet,
                        required.size(),
                        regionBeginIndex, previousMatchedKeyMoves,
                        matchedKeyMoves, aliasBindings, negatedBindings,
                        tapExpandedFromAliasBindings,
                        lastEventAbsorbed, absorbedPressedKeys,
                        allowLeadingIgnored, allowTrailingIgnored);
            }
            return false;
        }

        // Fast path: singleton MoveSet (one required non-tap, no optionals).
        if (eventCount == 1 && required.size() == 1 && optional.isEmpty() &&
            !(required.getFirst() instanceof TapComboMove) &&
            !keyMoveSet.canAbsorbEvents()) {
            KeyComboMove move = required.getFirst();
            KeyEvent event = events.get(eventBeginIndex);
            if (!pressOrReleaseMoveMatchesEvent(move, event, aliasBindings, negatedBindings))
                return false;
            if (eventBeginIndex > regionBeginIndex) {
                KeyEvent previousEvent = events.get(eventBeginIndex - 1);
                ResolvedKeyComboMove previousMove = previousMatchedKeyMoves.getLast();
                if (!previousMove.duration().satisfied(previousEvent.time(), event.time()))
                    return false;
            }
            bindAlias(move, event.key(), aliasBindings, negatedBindings);
            matchedKeyMoves.add(resolvedPressOrReleaseMove(move, event.key()));
            return true;
        }

        // General path: try subsets of optional moves, then bipartite match.
        if (tryOptionalSubsets(eventBeginIndex, eventCount, optional,
                optionalSlotsToFill, 0, new ArrayList<>(required),
                regionBeginIndex, previousMatchedKeyMoves, matchedKeyMoves,
                aliasBindings, negatedBindings, tapExpandedFromAliasBindings))
            return true;
        if (keyMoveSet.canAbsorbEvents()) {
            if (tryMatchMoveSetEventsWithIgnoredKeys(
                    eventBeginIndex, eventCount, keyMoveSet,
                    required.size(),
                    regionBeginIndex, previousMatchedKeyMoves,
                    matchedKeyMoves, aliasBindings, negatedBindings,
                    tapExpandedFromAliasBindings,
                    lastEventAbsorbed, absorbedPressedKeys,
                    allowLeadingIgnored, allowTrailingIgnored))
                return true;
        }
        // Tap-press partial match fallback: bipartite match that allows taps
        // to remain as dangling presses. Handles cases like [+a, +b] matching
        // tap(a) dangling + tap(b) dangling, or [+a, -a, +b] matching tap(a)
        // complete + tap(b) dangling.
        {
            List<KeyComboMove> allMoves = new ArrayList<>(required);
            allMoves.addAll(optional);
            if (tryTapPressPartialMatch(eventBeginIndex, eventCount, allMoves,
                    required.size(), regionBeginIndex, previousMatchedKeyMoves,
                    matchedKeyMoves, aliasBindings, negatedBindings,
                    tapExpandedFromAliasBindings)) {
                hasDanglingTapPress[0] = true;
                return true;
            }
        }
        // Partial absorption fallback: when absorbed events prevent all
        // required moves from matching, accept a partial match where at
        // least some required moves are fully matched.
        if (keyMoveSet.canAbsorbEvents()) {
            int prevSize = matchedKeyMoves.size();
            if (tryMatchMoveSetEventsWithIgnoredKeys(
                    eventBeginIndex, eventCount, keyMoveSet, 0,
                    regionBeginIndex, previousMatchedKeyMoves,
                    matchedKeyMoves, aliasBindings, negatedBindings,
                    tapExpandedFromAliasBindings,
                    lastEventAbsorbed, absorbedPressedKeys,
                    allowLeadingIgnored, allowTrailingIgnored)
                && matchedKeyMoves.size() > prevSize) {
                hasDanglingTapPress[0] = true;
                return true;
            }
        }
        return false;
    }

    /**
     * Fallback for partial matching when tryOptionalSubsets fails because tap
     * moves need 2 slots but only an odd number of event slots are available.
     * Uses a bipartite match that allows taps to remain in PRESS_MATCHED state
     * (dangling press). Every event must be assigned to a move.
     */
    private boolean tryTapPressPartialMatch(
            int eventBeginIndex, int eventCount, List<KeyComboMove> allMoves,
            int requiredMoveCount,
            int regionBeginIndex, List<ResolvedKeyComboMove> previousMatchedKeyMoves,
            List<ResolvedKeyComboMove> matchedKeyMoves,
            Map<String, Key> aliasBindings, Map<String, Key> negatedBindings,
            Map<String, List<Key>> tapExpandedFromAliasBindings) {
        int[] moveStates = new int[allMoves.size()];
        Key[] tapPressedKeys = new Key[allMoves.size()];
        ResolvedKeyComboMove[] assignedMoves = new ResolvedKeyComboMove[eventCount];
        boolean[] eventIsIgnored = new boolean[eventCount];
        Map<String, Key> savedAlias = new HashMap<>(aliasBindings);
        Map<String, Key> savedNegated = new HashMap<>(negatedBindings);
        if (assignEventsWithIgnoredKeys(eventBeginIndex, 0, eventCount, allMoves,
                requiredMoveCount, moveStates, tapPressedKeys, assignedMoves, eventIsIgnored,
                null,
                regionBeginIndex, previousMatchedKeyMoves,
                aliasBindings, negatedBindings, tapExpandedFromAliasBindings,
                true, true, true)) {
            // Collect matched key moves.
            for (ResolvedKeyComboMove m : assignedMoves)
                matchedKeyMoves.add(m);
            // Collect tap expandedFromAlias bindings.
            for (int i = 0; i < allMoves.size(); i++) {
                if (allMoves.get(i) instanceof TapComboMove tap && tap.expandedFromAlias() != null
                        && (moveStates[i] == FULLY_MATCHED || moveStates[i] == PRESS_MATCHED)) {
                    tapExpandedFromAliasBindings
                            .computeIfAbsent(tap.expandedFromAlias(), k -> new ArrayList<>())
                            .add(tapPressedKeys[i]);
                }
            }
            return true;
        }
        aliasBindings.clear();
        aliasBindings.putAll(savedAlias);
        negatedBindings.clear();
        negatedBindings.putAll(savedNegated);
        return false;
    }

    /**
     * Matches events against a KeyMoveSet that has ignored keys. Uses recursive
     * backtracking: for each event, try to assign it to an unused move (if it
     * matches), or ignore it (if the key is in the ignoredKeySet).
     * When minimumRequiredCount == required.size(), all required moves must be
     * assigned. When minimumRequiredCount == 0 (partial matching), no minimum
     * is enforced on the number of assigned required moves.
     */
    private boolean tryMatchMoveSetEventsWithIgnoredKeys(
            int eventBeginIndex, int eventCount, KeyMoveSet keyMoveSet,
            int minimumRequiredCount,
            int regionBeginIndex, List<ResolvedKeyComboMove> previousMatchedKeyMoves,
            List<ResolvedKeyComboMove> matchedKeyMoves, Map<String, Key> aliasBindings,
            Map<String, Key> negatedBindings,
            Map<String, List<Key>> tapExpandedFromAliasBindings,
            boolean[] lastEventAbsorbed, Set<Key> absorbedPressedKeys,
            boolean allowLeadingIgnored, boolean allowTrailingIgnored) {
        List<KeyComboMove> required = keyMoveSet.requiredMoves();
        List<KeyComboMove> optional = keyMoveSet.optionalMoves();
        List<KeyComboMove> allMoves = new ArrayList<>(required);
        allMoves.addAll(optional);
        int[] moveStates = new int[allMoves.size()];
        Key[] tapPressedKeys = new Key[allMoves.size()];
        ResolvedKeyComboMove[] assignedMovesForEvents = new ResolvedKeyComboMove[eventCount];
        boolean[] eventIsIgnored = new boolean[eventCount];
        ComboMove.WaitComboMove wm = keyMoveSet.waitMove();
        if (assignEventsWithIgnoredKeys(eventBeginIndex, 0, eventCount, allMoves,
                minimumRequiredCount, moveStates, tapPressedKeys,
                assignedMovesForEvents, eventIsIgnored,
                wm,
                regionBeginIndex, previousMatchedKeyMoves,
                aliasBindings, negatedBindings, tapExpandedFromAliasBindings,
                allowLeadingIgnored, allowTrailingIgnored, false)) {
            // Collect tap expandedFromAlias bindings.
            for (int i = 0; i < allMoves.size(); i++) {
                if (allMoves.get(i) instanceof TapComboMove tap
                        && moveStates[i] == FULLY_MATCHED && tap.expandedFromAlias() != null) {
                    tapExpandedFromAliasBindings
                            .computeIfAbsent(tap.expandedFromAlias(), k -> new ArrayList<>())
                            .add(tapPressedKeys[i]);
                }
            }
            // Collect matched key moves (non-ignored events).
            for (int i = 0; i < eventCount; i++) {
                if (!eventIsIgnored[i]) {
                    matchedKeyMoves.add(assignedMovesForEvents[i]);
                }
            }
            // Track absorbed (ignored) events.
            int lastEventIndex = events.size() - 1;
            for (int i = 0; i < eventCount; i++) {
                if (eventIsIgnored[i]) {
                    int globalIdx = eventBeginIndex + i;
                    if (globalIdx == lastEventIndex)
                        lastEventAbsorbed[0] = true;
                    KeyEvent absorbed = events.get(globalIdx);
                    if (absorbed.isPress())
                        absorbedPressedKeys.add(absorbed.key());
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Recursive backtracking for matching events against a KeyMoveSet with ignored keys.
     * For each event, tries:
     * 1. Assigning to an unused move (if it matches), including tap press/release.
     * 2. Ignoring the event (if the key is in the ignoredKeySet).
     * Terminates when all events are processed and all required moves are assigned.
     */
    private boolean assignEventsWithIgnoredKeys(
            int eventBeginIndex, int eventOffset, int eventCount,
            List<KeyComboMove> allMoves, int requiredCount,
            int[] moveStates, Key[] tapPressedKeys,
            ResolvedKeyComboMove[] assignedMoves,
            boolean[] eventIsIgnored,
            WaitComboMove waitMove,
            int regionBeginIndex, List<ResolvedKeyComboMove> previousMatchedKeyMoves,
            Map<String, Key> aliasBindings, Map<String, Key> negatedBindings,
            Map<String, List<Key>> tapExpandedFromAliasBindings,
            boolean allowLeadingIgnored, boolean allowTrailingIgnored,
            boolean allowDanglingTaps) {
        if (eventOffset == eventCount) {
            // All events processed. Check that all required moves are assigned
            // and no tap has a dangling press (unless dangling taps are allowed).
            for (int i = 0; i < requiredCount; i++) {
                if (moveStates[i] != FULLY_MATCHED)
                    return false;
            }
            if (!allowDanglingTaps) {
                for (int i = requiredCount; i < allMoves.size(); i++) {
                    if (allMoves.get(i) instanceof TapComboMove && moveStates[i] == PRESS_MATCHED)
                        return false;
                }
            }
            // For non-partial matching (requiredCount > 0), absorbed events must
            // be interleaved between key moves unless leading/trailing is allowed.
            if (requiredCount > 0 && eventCount > 0) {
                if (!allowLeadingIgnored && eventIsIgnored[0])
                    return false;
                if (!allowTrailingIgnored && eventIsIgnored[eventCount - 1])
                    return false;
            }
            return true;
        }

        int globalEventIndex = eventBeginIndex + eventOffset;
        KeyEvent event = events.get(globalEventIndex);

        // Duration check between consecutive events.
        if (globalEventIndex > regionBeginIndex && eventOffset > 0) {
            KeyEvent previousEvent = events.get(globalEventIndex - 1);
            if (eventIsIgnored[eventOffset - 1]) {
                // Previous event was ignored: check wait move's duration if set.
                if (waitMove != null &&
                    !waitMove.duration().satisfied(previousEvent.time(), event.time()))
                    return false;
            }
            else {
                // Previous event was a matched move: check its move duration.
                ResolvedKeyComboMove previousMove = assignedMoves[eventOffset - 1];
                if (!previousMove.duration().satisfied(previousEvent.time(), event.time()))
                    return false;
            }
        }
        else if (globalEventIndex > regionBeginIndex && eventOffset == 0) {
            // First event of this MoveSet but not first in region:
            // check duration from previous region event.
            KeyEvent previousEvent = events.get(globalEventIndex - 1);
            ResolvedKeyComboMove previousMove = previousMatchedKeyMoves.getLast();
            if (!previousMove.duration().satisfied(previousEvent.time(), event.time()))
                return false;
        }

        // Try assigning to a move.
        for (int moveIndex = 0; moveIndex < allMoves.size(); moveIndex++) {
            KeyComboMove move = allMoves.get(moveIndex);
            if (move instanceof TapComboMove tap) {
                // Try matching as tap press.
                if (moveStates[moveIndex] == UNUSED && event.isPress()
                        && tapMatchesKey(tap, event.key(), aliasBindings)) {
                    moveStates[moveIndex] = PRESS_MATCHED;
                    tapPressedKeys[moveIndex] = event.key();
                    eventIsIgnored[eventOffset] = false;
                    Map<String, Key> savedAliasBindings = new HashMap<>(aliasBindings);
                    Map<String, Key> savedNegatedBindings = new HashMap<>(negatedBindings);
                    bindAlias(tap, event.key(), aliasBindings, negatedBindings);
                    assignedMoves[eventOffset] = resolvedTapPress(tap, event.key());
                    if (assignEventsWithIgnoredKeys(eventBeginIndex, eventOffset + 1,
                            eventCount, allMoves, requiredCount, moveStates, tapPressedKeys,
                            assignedMoves, eventIsIgnored, waitMove,
                            regionBeginIndex,
                            previousMatchedKeyMoves, aliasBindings, negatedBindings,
                            tapExpandedFromAliasBindings,
                            allowLeadingIgnored, allowTrailingIgnored, allowDanglingTaps))
                        return true;
                    moveStates[moveIndex] = UNUSED;
                    tapPressedKeys[moveIndex] = null;
                    aliasBindings.clear();
                    aliasBindings.putAll(savedAliasBindings);
                    negatedBindings.clear();
                    negatedBindings.putAll(savedNegatedBindings);
                }
                // Try matching as tap release.
                if (moveStates[moveIndex] == PRESS_MATCHED && event.isRelease()
                        && event.key().equals(tapPressedKeys[moveIndex])) {
                    moveStates[moveIndex] = FULLY_MATCHED;
                    eventIsIgnored[eventOffset] = false;
                    assignedMoves[eventOffset] = resolvedTapRelease(tap, event.key());
                    if (assignEventsWithIgnoredKeys(eventBeginIndex, eventOffset + 1,
                            eventCount, allMoves, requiredCount, moveStates, tapPressedKeys,
                            assignedMoves, eventIsIgnored, waitMove,
                            regionBeginIndex,
                            previousMatchedKeyMoves, aliasBindings, negatedBindings,
                            tapExpandedFromAliasBindings,
                            allowLeadingIgnored, allowTrailingIgnored, allowDanglingTaps))
                        return true;
                    moveStates[moveIndex] = PRESS_MATCHED;
                }
            }
            else {
                if (moveStates[moveIndex] != UNUSED) continue;
                if (pressOrReleaseMoveMatchesEvent(move, event, aliasBindings, negatedBindings)) {
                    moveStates[moveIndex] = FULLY_MATCHED;
                    eventIsIgnored[eventOffset] = false;
                    Map<String, Key> savedAliasBindings = new HashMap<>(aliasBindings);
                    Map<String, Key> savedNegatedBindings = new HashMap<>(negatedBindings);
                    bindAlias(move, event.key(), aliasBindings, negatedBindings);
                    assignedMoves[eventOffset] = resolvedPressOrReleaseMove(move, event.key());
                    if (assignEventsWithIgnoredKeys(eventBeginIndex, eventOffset + 1,
                            eventCount, allMoves, requiredCount, moveStates, tapPressedKeys,
                            assignedMoves, eventIsIgnored, waitMove,
                            regionBeginIndex,
                            previousMatchedKeyMoves, aliasBindings, negatedBindings,
                            tapExpandedFromAliasBindings,
                            allowLeadingIgnored, allowTrailingIgnored, allowDanglingTaps))
                        return true;
                    moveStates[moveIndex] = UNUSED;
                    aliasBindings.clear();
                    aliasBindings.putAll(savedAliasBindings);
                    negatedBindings.clear();
                    negatedBindings.putAll(savedNegatedBindings);
                }
            }
        }

        // Try ignoring this event.
        if (waitMove != null && waitMove.matchesEvent(event)) {
            eventIsIgnored[eventOffset] = true;
            assignedMoves[eventOffset] = null;
            if (assignEventsWithIgnoredKeys(eventBeginIndex, eventOffset + 1,
                    eventCount, allMoves, requiredCount, moveStates, tapPressedKeys,
                    assignedMoves, eventIsIgnored, waitMove,
                    regionBeginIndex,
                    previousMatchedKeyMoves, aliasBindings, negatedBindings,
                    tapExpandedFromAliasBindings,
                    allowLeadingIgnored, allowTrailingIgnored, allowDanglingTaps))
                return true;
        }

        return false;
    }

    /**
     * Recursively selects optional moves to include alongside the required moves.
     * Uses remainingSlots (event slots, not move count) to account for taps needing
     * 2 slots each. Once enough slots are filled, attempts a bipartite match.
     */
    private boolean tryOptionalSubsets(
            int eventBeginIndex, int eventCount,
            List<KeyComboMove> optional,
            int remainingSlots, int optionalIndex,
            List<KeyComboMove> moves,
            int regionBeginIndex, List<ResolvedKeyComboMove> previousMatchedKeyMoves,
            List<ResolvedKeyComboMove> matchedKeyMoves, Map<String, Key> aliasBindings,
            Map<String, Key> negatedBindings,
            Map<String, List<Key>> tapExpandedFromAliasBindings) {
        if (remainingSlots == 0) {
            return tryBipartiteMatch(eventBeginIndex, eventCount, moves,
                    regionBeginIndex, previousMatchedKeyMoves, matchedKeyMoves,
                    aliasBindings, negatedBindings, tapExpandedFromAliasBindings);
        }
        if (optionalIndex >= optional.size())
            return false;
        // Prune: check if remaining optionals can contribute enough slots.
        int availableSlots = 0;
        for (int i = optionalIndex; i < optional.size(); i++)
            availableSlots += (optional.get(i) instanceof TapComboMove) ? 2 : 1;
        if (availableSlots < remainingSlots)
            return false;

        // Include optional[optionalIndex].
        int slots = (optional.get(optionalIndex) instanceof TapComboMove) ? 2 : 1;
        if (slots <= remainingSlots) {
            moves.add(optional.get(optionalIndex));
            if (tryOptionalSubsets(eventBeginIndex, eventCount, optional,
                    remainingSlots - slots, optionalIndex + 1, moves,
                    regionBeginIndex, previousMatchedKeyMoves, matchedKeyMoves,
                    aliasBindings, negatedBindings, tapExpandedFromAliasBindings))
                return true;
            moves.removeLast();
        }

        // Skip optional[optionalIndex].
        return tryOptionalSubsets(eventBeginIndex, eventCount, optional,
                remainingSlots, optionalIndex + 1, moves,
                regionBeginIndex, previousMatchedKeyMoves, matchedKeyMoves,
                aliasBindings, negatedBindings, tapExpandedFromAliasBindings);
    }

    // Tap move states.
    private static final int UNUSED = 0;
    private static final int PRESS_MATCHED = 1;
    private static final int FULLY_MATCHED = 2;

    /**
     * Tries to find a one-to-one assignment between moves and events.
     * Moves within a MoveSet are unordered: {+a #b} can match events [+a #b] or [#b +a].
     * Each assignment must satisfy key/press-release matching and duration constraints.
     */
    private boolean tryBipartiteMatch(
            int eventBeginIndex, int eventCount, List<KeyComboMove> moves,
            int regionBeginIndex, List<ResolvedKeyComboMove> previousMatchedKeyMoves,
            List<ResolvedKeyComboMove> matchedKeyMoves, Map<String, Key> aliasBindings,
            Map<String, Key> negatedBindings,
            Map<String, List<Key>> tapExpandedFromAliasBindings) {
        int[] moveStates = new int[moves.size()];
        Key[] tapPressedKeys = new Key[moves.size()];
        ResolvedKeyComboMove[] assignedMoves = new ResolvedKeyComboMove[eventCount];
        boolean[] eventIsIgnored = new boolean[eventCount];
        Map<String, Key> savedAliasBindings = new HashMap<>(aliasBindings);
        Map<String, Key> savedNegatedBindings = new HashMap<>(negatedBindings);
        if (assignEventsWithIgnoredKeys(eventBeginIndex, 0, eventCount, moves,
                moves.size(), moveStates, tapPressedKeys, assignedMoves, eventIsIgnored,
                null,
                regionBeginIndex, previousMatchedKeyMoves,
                aliasBindings, negatedBindings, tapExpandedFromAliasBindings,
                true, true, false)) {
            matchedKeyMoves.clear();
            Collections.addAll(matchedKeyMoves, assignedMoves);
            // Collect tap expandedFromAlias bindings.
            for (int i = 0; i < moves.size(); i++) {
                if (moves.get(i) instanceof TapComboMove tap
                        && moveStates[i] == FULLY_MATCHED && tap.expandedFromAlias() != null) {
                    tapExpandedFromAliasBindings
                            .computeIfAbsent(tap.expandedFromAlias(), k -> new ArrayList<>())
                            .add(tapPressedKeys[i]);
                }
            }
            return true;
        }
        // Restore aliasBindings and negatedBindings on failure.
        aliasBindings.clear();
        aliasBindings.putAll(savedAliasBindings);
        negatedBindings.clear();
        negatedBindings.putAll(savedNegatedBindings);
        return false;
    }

    /**
     * Checks if a tap move's key/alias matches the given key, considering alias bindings.
     */
    private static boolean tapMatchesKey(TapComboMove tap, Key key,
                                          Map<String, Key> aliasBindings) {
        KeyOrAlias keyOrAlias = tap.keyOrAlias();
        if (keyOrAlias.isAlias()) {
            Key bound = aliasBindings.get(keyOrAlias.aliasName());
            if (bound != null)
                return key.equals(bound);
            return keyOrAlias.matchesKey(key);
        }
        return key.equals(keyOrAlias.key());
    }

    static boolean anyMoveCouldMatchEvent(KeyMoveSet keyMoveSet, KeyEvent event,
                                          Map<String, Key> aliasBindings,
                                          Map<String, Key> negatedBindings) {
        for (KeyComboMove m : keyMoveSet.requiredMoves()) {
            if (m instanceof TapComboMove tap) {
                if (tap.keyOrAlias().matchesKey(event.key()))
                    return true;
            }
            else if (pressOrReleaseMoveMatchesEvent(m, event, aliasBindings, negatedBindings))
                return true;
        }
        for (KeyComboMove m : keyMoveSet.optionalMoves()) {
            if (m instanceof TapComboMove tap) {
                if (tap.keyOrAlias().matchesKey(event.key()))
                    return true;
            }
            else if (pressOrReleaseMoveMatchesEvent(m, event, aliasBindings, negatedBindings))
                return true;
        }
        return false;
    }

    /**
     * Checks whether a key move matches an event, considering alias and negated bindings.
     * For negated moves: if already bound, the event key must equal the bound key.
     * If unbound, the event key must NOT match the move's key/alias.
     * For alias moves: if the alias is already bound, the event key must equal
     * the bound key. If unbound, the event key must be in the alias's key set.
     * For regular key moves: direct key equality.
     */
    private static boolean pressOrReleaseMoveMatchesEvent(KeyComboMove move, KeyEvent event,
                                            Map<String, Key> aliasBindings,
                                            Map<String, Key> negatedBindings) {
        if (event.isPress() != move.isPress())
            return false;
        KeyOrAlias keyOrAlias = move.keyOrAlias();
        if (move.negated()) {
            String name = keyOrAlias.isAlias() ? keyOrAlias.aliasName() : keyOrAlias.key().name();
            Key bound = negatedBindings.get(name);
            if (bound != null)
                return event.key().equals(bound);
            return !keyOrAlias.matchesKey(event.key());
        }
        if (keyOrAlias.isAlias()) {
            Key bound = aliasBindings.get(keyOrAlias.aliasName());
            if (bound != null)
                return event.key().equals(bound);
            return keyOrAlias.matchesKey(event.key());
        }
        return event.key().equals(keyOrAlias.key());
    }

    /**
     * Binds an alias or negated name to a key.
     */
    private static void bindAlias(KeyComboMove move, Key key,
                                  Map<String, Key> aliasBindings,
                                  Map<String, Key> negatedBindings) {
        KeyOrAlias keyOrAlias = move.keyOrAlias();
        if (move.negated()) {
            String name = keyOrAlias.isAlias() ? keyOrAlias.aliasName() : keyOrAlias.key().name();
            negatedBindings.put(name, key);
        }
        else if (keyOrAlias.isAlias())
            aliasBindings.put(keyOrAlias.aliasName(), key);
    }

    /**
     * Returns a ResolvedKeyComboMove with the concrete key from the matched event.
     */
    private static ResolvedKeyComboMove resolvedPressOrReleaseMove(KeyComboMove move, Key matchedKey) {
        return switch (move) {
            case PressComboMove p ->
                    new ResolvedPressComboMove(
                            matchedKey, p.eventMustBeEaten(), p.duration());
            case ReleaseComboMove r ->
                    new ResolvedReleaseComboMove(
                            matchedKey, r.duration());
            case TapComboMove t ->
                    throw new IllegalStateException();
        };
    }

    private static ResolvedKeyComboMove resolvedTapPress(TapComboMove tap, Key matchedKey) {
        return new ResolvedPressComboMove(matchedKey, true, tap.duration());
    }

    private static ResolvedKeyComboMove resolvedTapRelease(TapComboMove tap, Key matchedKey) {
        return new ResolvedReleaseComboMove(matchedKey, tap.duration());
    }

    /**
     * Returns true if some subset of moves can fill exactly {@code eventCount}
     * event slots. Taps need 2 slots (press + release), press/release moves
     * need 1 slot.
     */
    private static boolean canFillEventSlots(List<KeyComboMove> moves, int eventCount) {
        if (eventCount == 0)
            return true;
        boolean[] canFill = new boolean[eventCount + 1];
        canFill[0] = true;
        for (KeyComboMove move : moves) {
            int slots = (move instanceof TapComboMove) ? 2 : 1;
            for (int i = eventCount; i >= slots; i--)
                if (canFill[i - slots])
                    canFill[i] = true;
        }
        return canFill[eventCount];
    }

    private static Map<String, List<Key>> deepCopyOf(Map<String, List<Key>> map) {
        Map<String, List<Key>> copy = new HashMap<>();
        for (var entry : map.entrySet())
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        return Map.copyOf(copy);
    }

    @Override
    public String toString() {
        if (events.isEmpty())
            return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) {
                long ms = Duration.between(events.get(i - 1).time(),
                        events.get(i).time()).toMillis();
                sb.append("-").append(ms).append(" ");
            }
            sb.append(events.get(i));
        }
        sb.append("]");
        return sb.toString();
    }
}
