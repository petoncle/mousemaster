package mousemaster;

import mousemaster.ComboMove.KeyComboMove;
import mousemaster.ComboMove.PressComboMove;
import mousemaster.ComboMove.ReleaseComboMove;
import mousemaster.ComboMove.WaitComboMove;
import mousemaster.MoveSet.KeyMoveSet;
import mousemaster.MoveSet.WaitMoveSet;
import mousemaster.ResolvedKeyComboMove.ResolvedPressComboMove;
import mousemaster.ResolvedKeyComboMove.ResolvedReleaseComboMove;

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
                    KeySet ignoredKeySet = moveSet instanceof WaitMoveSet wms ?
                            wms.waitMove().ignoredKeySet() :
                            ((KeyMoveSet) moveSet).waitMove().ignoredKeySet();
                    for (KeyEvent event : events) {
                        if (ignoredKeySet.contains(event.key())) {
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
                    && wms.waitMove().ignoredKeySet().equals(KeySet.ALL)) {
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
                boolean[] lastEventAbsorbed = {false};
                int[] lastKeyMoveEventIndex = {-1};
                Set<Key> absorbedPressedKeys = new HashSet<>();
                boolean hasFollowingMoveSets = k < moveSets.size();
                if (tryAssignEventsToMoveSets(subMoveSets, 0, regionBeginIndex,
                        regionBeginIndex + totalEventCount, regionBeginIndex,
                        matchedKeyMoves, aliasBindings, negatedBindings,
                        lastEventAbsorbed, lastKeyMoveEventIndex,
                        absorbedPressedKeys, isPartial, hasFollowingMoveSets)) {
                    boolean complete = !isPartial && (k == moveSets.size());
                    int moveSetCount = isPartial ? k - 1 : k;
                    return new ComboSequenceMatch(List.copyOf(matchedKeyMoves), complete,
                            moveSetCount, lastKeyMoveEventIndex[0], lastEventAbsorbed[0],
                            Set.copyOf(absorbedPressedKeys),
                            new AliasResolution(Map.copyOf(aliasBindings),
                                    Map.copyOf(negatedBindings)));
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
                        if (moveMatchesEvent(move, event, Map.of(), Map.of())) {
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
            boolean[] lastEventAbsorbed, int[] lastKeyMoveEventIndex,
            Set<Key> absorbedPressedKeys, boolean allowPartialLastMoveSet,
            boolean hasFollowingMoveSets) {
        if (moveSetIndex == moveSets.size())
            return eventIndex == eventEndIndex;

        MoveSet moveSet = moveSets.get(moveSetIndex);

        // Wait MoveSets: absorb ignored events, then skip to next MoveSet.
        if (moveSet instanceof WaitMoveSet waitMoveSet) {
            WaitComboMove waitMove = waitMoveSet.waitMove();
            if (waitMoveSet.canAbsorbEvents()) {
                // Absorbing wait: try consuming 0..maxAbsorb events.
                int minForRemaining = 0;
                for (int later = moveSetIndex + 1; later < moveSets.size(); later++)
                    minForRemaining += moveSets.get(later).minMoveCount();
                int maxAbsorb = (eventEndIndex - eventIndex) - minForRemaining;
                // Can't absorb past a non-ignored event.
                int firstNonIgnoredOffset = maxAbsorb;
                for (int i = 0; i < maxAbsorb; i++) {
                    if (!waitMove.ignoredKeySet().contains(events.get(eventIndex + i).key())) {
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
                    // For mid-sequence wait: check time gap from pre-wait event
                    // to first post-wait event.
                    if (eventIndex > regionBeginIndex && nextEventIndex < eventEndIndex) {
                        KeyEvent previousEvent = events.get(eventIndex - 1);
                        KeyEvent nextEvent = events.get(nextEventIndex);
                        if (!waitMove.duration().satisfied(previousEvent.time(), nextEvent.time()))
                            continue;
                    }
                    if (peekMoveSet != null && nextEventIndex < eventEndIndex) {
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
                            lastEventAbsorbed, lastKeyMoveEventIndex,
                            absorbedPressedKeys, allowPartialLastMoveSet,
                            hasFollowingMoveSets))
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
                        lastEventAbsorbed, lastKeyMoveEventIndex,
                        absorbedPressedKeys, allowPartialLastMoveSet,
                        hasFollowingMoveSets);
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
            if (tryMatchMoveSetEvents(eventIndex, eventCount, keyMoveSet,
                    regionBeginIndex, matchedKeyMoves, moveSetMatchedKeyMoves,
                    aliasBindings, negatedBindings,
                    moveSetLastEventAbsorbed, moveSetAbsorbedPressedKeys,
                    allowLeadingIgnored, allowTrailingIgnored)) {
                int savedSize = matchedKeyMoves.size();
                int savedLastKeyMoveEventIndex = lastKeyMoveEventIndex[0];
                boolean savedLastEventAbsorbed = lastEventAbsorbed[0];
                matchedKeyMoves.addAll(moveSetMatchedKeyMoves);
                lastKeyMoveEventIndex[0] = eventIndex + eventCount - 1;
                if (moveSetLastEventAbsorbed[0])
                    lastEventAbsorbed[0] = true;
                absorbedPressedKeys.addAll(moveSetAbsorbedPressedKeys);
                if (tryAssignEventsToMoveSets(moveSets, moveSetIndex + 1,
                        eventIndex + eventCount, eventEndIndex,
                        regionBeginIndex, matchedKeyMoves, aliasBindings,
                        negatedBindings, lastEventAbsorbed, lastKeyMoveEventIndex,
                        absorbedPressedKeys, allowPartialLastMoveSet,
                        hasFollowingMoveSets))
                    return true;
                lastKeyMoveEventIndex[0] = savedLastKeyMoveEventIndex;
                lastEventAbsorbed[0] = savedLastEventAbsorbed;
                absorbedPressedKeys.removeAll(moveSetAbsorbedPressedKeys);
                while (matchedKeyMoves.size() > savedSize)
                    matchedKeyMoves.removeLast();
            }
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
            boolean[] lastEventAbsorbed, Set<Key> absorbedPressedKeys,
            boolean allowLeadingIgnored, boolean allowTrailingIgnored) {
        if (eventCount == 0)
            return true;

        List<KeyComboMove> required = keyMoveSet.requiredMoves();
        List<KeyComboMove> optional = keyMoveSet.optionalMoves();
        int optionalToUseCount = eventCount - required.size();
        if (optionalToUseCount < 0) {
            if (keyMoveSet.canAbsorbEvents()) {
                // Partial matching with absorbed events: some events match key
                // moves, others are absorbed. Not all required moves need to
                // be assigned (requiredCount=0).
                return tryMatchMoveSetEventsWithIgnoredKeys(
                        eventBeginIndex, eventCount, keyMoveSet, 0,
                        regionBeginIndex, previousMatchedKeyMoves,
                        matchedKeyMoves, aliasBindings, negatedBindings,
                        lastEventAbsorbed, absorbedPressedKeys,
                        allowLeadingIgnored, allowTrailingIgnored);
            }
            // Partial matching: select eventCount moves from all moves.
            List<KeyComboMove> allMoves = new ArrayList<>(required);
            allMoves.addAll(optional);
            return tryOptionalSubsets(eventBeginIndex, eventCount,
                    allMoves, eventCount, 0, new ArrayList<>(),
                    regionBeginIndex, previousMatchedKeyMoves, matchedKeyMoves,
                    aliasBindings, negatedBindings);
        }
        if (optionalToUseCount > optional.size()) {
            // More events than moves: if absorbing, try the absorbing path.
            if (keyMoveSet.canAbsorbEvents()) {
                return tryMatchMoveSetEventsWithIgnoredKeys(
                        eventBeginIndex, eventCount, keyMoveSet,
                        keyMoveSet.requiredMoves().size(),
                        regionBeginIndex, previousMatchedKeyMoves,
                        matchedKeyMoves, aliasBindings, negatedBindings,
                        lastEventAbsorbed, absorbedPressedKeys,
                        allowLeadingIgnored, allowTrailingIgnored);
            }
            return false;
        }

        // Fast path: singleton MoveSet (one required, no optionals).
        if (eventCount == 1 && required.size() == 1 && optional.isEmpty() &&
            !keyMoveSet.canAbsorbEvents()) {
            KeyComboMove move = required.getFirst();
            KeyEvent event = events.get(eventBeginIndex);
            if (!moveMatchesEvent(move, event, aliasBindings, negatedBindings))
                return false;
            if (eventBeginIndex > regionBeginIndex) {
                KeyEvent previousEvent = events.get(eventBeginIndex - 1);
                ResolvedKeyComboMove previousMove = previousMatchedKeyMoves.getLast();
                if (!previousMove.duration().satisfied(previousEvent.time(), event.time()))
                    return false;
            }
            bindAlias(move, event.key(), aliasBindings, negatedBindings);
            matchedKeyMoves.add(resolvedMove(move, event.key()));
            return true;
        }

        // General path: try subsets of optional moves, then bipartite match.
        if (tryOptionalSubsets(eventBeginIndex, eventCount, optional,
                optionalToUseCount, 0, new ArrayList<>(required),
                regionBeginIndex, previousMatchedKeyMoves, matchedKeyMoves,
                aliasBindings, negatedBindings))
            return true;
        // Normal matching failed. If this MoveSet can absorb events, some
        // events may be absorbable rather than matching moves. Try the
        // absorbing path (e.g. events [+a, -a, +d, +b] against
        // {+a -a +b -b +{d}} where +d is absorbed, not a key move).
        if (keyMoveSet.canAbsorbEvents()) {
            return tryMatchMoveSetEventsWithIgnoredKeys(
                    eventBeginIndex, eventCount, keyMoveSet,
                    keyMoveSet.requiredMoves().size(),
                    regionBeginIndex, previousMatchedKeyMoves,
                    matchedKeyMoves, aliasBindings, negatedBindings,
                    lastEventAbsorbed, absorbedPressedKeys,
                    allowLeadingIgnored, allowTrailingIgnored);
        }
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
            boolean[] lastEventAbsorbed, Set<Key> absorbedPressedKeys,
            boolean allowLeadingIgnored, boolean allowTrailingIgnored) {
        List<KeyComboMove> required = keyMoveSet.requiredMoves();
        List<KeyComboMove> optional = keyMoveSet.optionalMoves();
        List<KeyComboMove> allMoves = new ArrayList<>(required);
        allMoves.addAll(optional);
        boolean[] moveUsed = new boolean[allMoves.size()];
        ResolvedKeyComboMove[] assignedMovesForEvents = new ResolvedKeyComboMove[eventCount];
        boolean[] eventIsIgnored = new boolean[eventCount];
        ComboMove.WaitComboMove wm = keyMoveSet.waitMove();
        if (assignEventsWithIgnoredKeys(eventBeginIndex, 0, eventCount, allMoves,
                minimumRequiredCount, moveUsed, assignedMovesForEvents, eventIsIgnored,
                wm.ignoredKeySet(), wm.duration(),
                regionBeginIndex, previousMatchedKeyMoves,
                aliasBindings, negatedBindings,
                allowLeadingIgnored, allowTrailingIgnored)) {
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
     * 1. Assigning to an unused move (if it matches).
     * 2. Ignoring the event (if the key is in the ignoredKeySet).
     * Terminates when all events are processed and all required moves are assigned.
     */
    private boolean assignEventsWithIgnoredKeys(
            int eventBeginIndex, int eventOffset, int eventCount,
            List<KeyComboMove> allMoves, int requiredCount,
            boolean[] moveUsed, ResolvedKeyComboMove[] assignedMoves,
            boolean[] eventIsIgnored,
            KeySet ignoredKeySet, ComboMoveDuration ignoredKeysDuration,
            int regionBeginIndex, List<ResolvedKeyComboMove> previousMatchedKeyMoves,
            Map<String, Key> aliasBindings, Map<String, Key> negatedBindings,
            boolean allowLeadingIgnored, boolean allowTrailingIgnored) {
        if (eventOffset == eventCount) {
            // All events processed. Check that all required moves are assigned.
            for (int i = 0; i < requiredCount; i++)
                if (!moveUsed[i])
                    return false;
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
                // Previous event was ignored: check ignoredKeysDuration if set.
                if (ignoredKeysDuration != null &&
                    !ignoredKeysDuration.satisfied(previousEvent.time(), event.time()))
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

        // Try assigning to an unused move.
        for (int moveIndex = 0; moveIndex < allMoves.size(); moveIndex++) {
            if (moveUsed[moveIndex]) continue;
            KeyComboMove move = allMoves.get(moveIndex);
            if (moveMatchesEvent(move, event, aliasBindings, negatedBindings)) {
                moveUsed[moveIndex] = true;
                eventIsIgnored[eventOffset] = false;
                Map<String, Key> savedAliasBindings = new HashMap<>(aliasBindings);
                Map<String, Key> savedNegatedBindings = new HashMap<>(negatedBindings);
                bindAlias(move, event.key(), aliasBindings, negatedBindings);
                assignedMoves[eventOffset] = resolvedMove(move, event.key());
                if (assignEventsWithIgnoredKeys(eventBeginIndex, eventOffset + 1,
                        eventCount, allMoves, requiredCount, moveUsed,
                        assignedMoves, eventIsIgnored, ignoredKeySet,
                        ignoredKeysDuration, regionBeginIndex,
                        previousMatchedKeyMoves, aliasBindings, negatedBindings,
                        allowLeadingIgnored, allowTrailingIgnored))
                    return true;
                moveUsed[moveIndex] = false;
                aliasBindings.clear();
                aliasBindings.putAll(savedAliasBindings);
                negatedBindings.clear();
                negatedBindings.putAll(savedNegatedBindings);
            }
        }

        // Try ignoring this event.
        if (ignoredKeySet.contains(event.key())) {
            eventIsIgnored[eventOffset] = true;
            assignedMoves[eventOffset] = null;
            if (assignEventsWithIgnoredKeys(eventBeginIndex, eventOffset + 1,
                    eventCount, allMoves, requiredCount, moveUsed,
                    assignedMoves, eventIsIgnored, ignoredKeySet,
                    ignoredKeysDuration, regionBeginIndex,
                    previousMatchedKeyMoves, aliasBindings, negatedBindings,
                    allowLeadingIgnored, allowTrailingIgnored))
                return true;
        }

        return false;
    }

    /**
     * Recursively selects optionalToUseCount optional moves to include alongside
     * the required moves. Once enough optionals are selected, attempts a bipartite
     * match of the combined moves against the events.
     */
    private boolean tryOptionalSubsets(
            int eventBeginIndex, int eventCount,
            List<KeyComboMove> optional,
            int optionalToUseCount, int optionalIndex,
            List<KeyComboMove> moves,
            int regionBeginIndex, List<ResolvedKeyComboMove> previousMatchedKeyMoves,
            List<ResolvedKeyComboMove> matchedKeyMoves, Map<String, Key> aliasBindings,
            Map<String, Key> negatedBindings) {
        if (optionalToUseCount == 0) {
            return tryBipartiteMatch(eventBeginIndex, eventCount, moves,
                    regionBeginIndex, previousMatchedKeyMoves, matchedKeyMoves,
                    aliasBindings, negatedBindings);
        }
        int remainingOptionalCount = optional.size() - optionalIndex;
        if (remainingOptionalCount < optionalToUseCount)
            return false;

        // Include optional[optionalIndex].
        moves.add(optional.get(optionalIndex));
        if (tryOptionalSubsets(eventBeginIndex, eventCount, optional,
                optionalToUseCount - 1, optionalIndex + 1, moves,
                regionBeginIndex, previousMatchedKeyMoves, matchedKeyMoves,
                aliasBindings, negatedBindings))
            return true;
        moves.removeLast();

        // Skip optional[optionalIndex].
        return tryOptionalSubsets(eventBeginIndex, eventCount, optional,
                optionalToUseCount, optionalIndex + 1, moves,
                regionBeginIndex, previousMatchedKeyMoves, matchedKeyMoves,
                aliasBindings, negatedBindings);
    }

    /**
     * Tries to find a one-to-one assignment between moves and events.
     * Moves within a MoveSet are unordered: {+a #b} can match events [+a #b] or [#b +a].
     * Each assignment must satisfy key/press-release matching and duration constraints.
     */
    private boolean tryBipartiteMatch(
            int eventBeginIndex, int eventCount, List<KeyComboMove> moves,
            int regionBeginIndex, List<ResolvedKeyComboMove> previousMatchedKeyMoves,
            List<ResolvedKeyComboMove> matchedKeyMoves, Map<String, Key> aliasBindings,
            Map<String, Key> negatedBindings) {
        boolean[] moveUsed = new boolean[moves.size()];
        ResolvedKeyComboMove[] assignedMoves = new ResolvedKeyComboMove[eventCount];
        Map<String, Key> savedAliasBindings = new HashMap<>(aliasBindings);
        Map<String, Key> savedNegatedBindings = new HashMap<>(negatedBindings);
        if (assignEvents(eventBeginIndex, 0, eventCount, moves, moveUsed,
                assignedMoves, regionBeginIndex, previousMatchedKeyMoves,
                aliasBindings, negatedBindings)) {
            matchedKeyMoves.clear();
            Collections.addAll(matchedKeyMoves, assignedMoves);
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
     * Recursively assigns moves to events one at a time. For each event, tries
     * every unused move that matches the event's key and press/release. Backtracks
     * if no valid assignment is found for subsequent events.
     */
    private boolean assignEvents(
            int eventBeginIndex, int eventOffset, int eventCount,
            List<KeyComboMove> moves, boolean[] moveUsed,
            ResolvedKeyComboMove[] assignedMoves,
            int regionBeginIndex, List<ResolvedKeyComboMove> previousMatchedKeyMoves,
            Map<String, Key> aliasBindings, Map<String, Key> negatedBindings) {
        if (eventOffset == eventCount)
            return true;

        int globalEventIndex = eventBeginIndex + eventOffset;
        KeyEvent event = events.get(globalEventIndex);

        // Duration check: skip for the first event in the matched region.
        if (globalEventIndex > regionBeginIndex) {
            KeyEvent previousEvent = events.get(globalEventIndex - 1);
            ResolvedKeyComboMove previousMove;
            if (eventOffset > 0)
                previousMove = assignedMoves[eventOffset - 1];
            else
                previousMove = previousMatchedKeyMoves.getLast();
            if (!previousMove.duration().satisfied(previousEvent.time(), event.time()))
                return false;
        }

        for (int moveIndex = 0; moveIndex < moves.size(); moveIndex++) {
            if (moveUsed[moveIndex]) continue;
            KeyComboMove move = moves.get(moveIndex);
            if (moveMatchesEvent(move, event, aliasBindings, negatedBindings)) {
                moveUsed[moveIndex] = true;
                Map<String, Key> savedAliasBindings = new HashMap<>(aliasBindings);
                Map<String, Key> savedNegatedBindings = new HashMap<>(negatedBindings);
                bindAlias(move, event.key(), aliasBindings, negatedBindings);
                assignedMoves[eventOffset] = resolvedMove(move, event.key());
                if (assignEvents(eventBeginIndex, eventOffset + 1, eventCount,
                        moves, moveUsed, assignedMoves, regionBeginIndex,
                        previousMatchedKeyMoves, aliasBindings, negatedBindings))
                    return true;
                moveUsed[moveIndex] = false;
                // Restore aliasBindings and negatedBindings on backtrack.
                aliasBindings.clear();
                aliasBindings.putAll(savedAliasBindings);
                negatedBindings.clear();
                negatedBindings.putAll(savedNegatedBindings);
            }
        }
        return false;
    }

    static boolean anyMoveCouldMatchEvent(KeyMoveSet keyMoveSet, KeyEvent event,
                                                  Map<String, Key> aliasBindings,
                                                  Map<String, Key> negatedBindings) {
        for (KeyComboMove m : keyMoveSet.requiredMoves())
            if (moveMatchesEvent(m, event, aliasBindings, negatedBindings))
                return true;
        for (KeyComboMove m : keyMoveSet.optionalMoves())
            if (moveMatchesEvent(m, event, aliasBindings, negatedBindings))
                return true;
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
    private static boolean moveMatchesEvent(KeyComboMove move, KeyEvent event,
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
    private static ResolvedKeyComboMove resolvedMove(KeyComboMove move, Key matchedKey) {
        return switch (move) {
            case PressComboMove p ->
                    new ResolvedPressComboMove(
                            matchedKey, p.eventMustBeEaten(), p.duration());
            case ReleaseComboMove r ->
                    new ResolvedReleaseComboMove(
                            matchedKey, r.duration());
        };
    }

    @Override
    public String toString() {
        return events.toString();
    }
}
