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
            if (minTotalEventCount > events.size())
                continue;
            // Absorbing waits can consume arbitrary events, so max is events.size().
            int effectiveMaxEventCount = hasAbsorbingWait ?
                    events.size() : Math.min(maxTotalEventCount, events.size());

            // Try consuming totalEventCount events from the end of the preparation,
            // starting with the most events (greediest match) first.
            for (int totalEventCount = effectiveMaxEventCount;
                 totalEventCount >= minTotalEventCount; totalEventCount--) {
                int regionBeginIndex = events.size() - totalEventCount;
                List<ResolvedKeyComboMove> matchedKeyMoves = new ArrayList<>();
                Map<String, Key> aliasBindings = new HashMap<>();
                Map<String, Key> negatedBindings = new HashMap<>();
                boolean[] lastEventAbsorbed = {false};
                int[] lastKeyMoveEventIndex = {-1};
                Set<Key> absorbedPressedKeys = new HashSet<>();
                if (tryAssignEventsToMoveSets(subMoveSets, 0, regionBeginIndex,
                        regionBeginIndex + totalEventCount, regionBeginIndex,
                        matchedKeyMoves, aliasBindings, negatedBindings,
                        lastEventAbsorbed, lastKeyMoveEventIndex,
                        absorbedPressedKeys)) {
                    boolean complete = (k == moveSets.size());
                    return new ComboSequenceMatch(List.copyOf(matchedKeyMoves), complete,
                            k, lastKeyMoveEventIndex[0], lastEventAbsorbed[0],
                            Set.copyOf(absorbedPressedKeys),
                            new AliasResolution(Map.copyOf(aliasBindings),
                                    Map.copyOf(negatedBindings)));
                }
            }
        }
        return ComboSequenceMatch.noMatch();
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
            Set<Key> absorbedPressedKeys) {
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
                    if (!waitMove.ignoredKeySet().isIgnored(events.get(eventIndex + i).key())) {
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
                            absorbedPressedKeys))
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
                        absorbedPressedKeys);
            }
        }

        KeyMoveSet keyMoveSet = (KeyMoveSet) moveSet;
        int remainingEventCount = eventEndIndex - eventIndex;
        int minEventCountForRemainingMoveSets = 0;
        for (int laterMoveSetIndex = moveSetIndex + 1;
             laterMoveSetIndex < moveSets.size(); laterMoveSetIndex++)
            minEventCountForRemainingMoveSets +=
                    moveSets.get(laterMoveSetIndex).minMoveCount();
        int maxEventCountForMoveSet = Math.min(keyMoveSet.maxMoveCount(),
                remainingEventCount - minEventCountForRemainingMoveSets);

        for (int eventCount = maxEventCountForMoveSet;
             eventCount >= keyMoveSet.minMoveCount(); eventCount--) {
            List<ResolvedKeyComboMove> moveSetMatchedKeyMoves = new ArrayList<>();
            Map<String, Key> savedAliasBindings = new HashMap<>(aliasBindings);
            Map<String, Key> savedNegatedBindings = new HashMap<>(negatedBindings);
            if (tryMatchMoveSetEvents(eventIndex, eventCount, keyMoveSet,
                    regionBeginIndex, matchedKeyMoves, moveSetMatchedKeyMoves,
                    aliasBindings, negatedBindings)) {
                int savedSize = matchedKeyMoves.size();
                int savedLastKeyMoveEventIndex = lastKeyMoveEventIndex[0];
                matchedKeyMoves.addAll(moveSetMatchedKeyMoves);
                lastKeyMoveEventIndex[0] = eventIndex + eventCount - 1;
                if (tryAssignEventsToMoveSets(moveSets, moveSetIndex + 1,
                        eventIndex + eventCount, eventEndIndex,
                        regionBeginIndex, matchedKeyMoves, aliasBindings,
                        negatedBindings, lastEventAbsorbed, lastKeyMoveEventIndex,
                        absorbedPressedKeys))
                    return true;
                lastKeyMoveEventIndex[0] = savedLastKeyMoveEventIndex;
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
     */
    private boolean tryMatchMoveSetEvents(
            int eventBeginIndex, int eventCount, KeyMoveSet keyMoveSet,
            int regionBeginIndex, List<ResolvedKeyComboMove> previousMatchedKeyMoves,
            List<ResolvedKeyComboMove> matchedKeyMoves, Map<String, Key> aliasBindings,
            Map<String, Key> negatedBindings) {
        if (eventCount == 0)
            return true;
        List<KeyComboMove> required = keyMoveSet.requiredMoves();
        List<KeyComboMove> optional = keyMoveSet.optionalMoves();
        int optionalToUseCount = eventCount - required.size();
        if (optionalToUseCount < 0 || optionalToUseCount > optional.size())
            return false;

        // Fast path: singleton MoveSet (one required, no optionals).
        if (eventCount == 1 && required.size() == 1 && optional.isEmpty()) {
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
        return tryOptionalSubsets(eventBeginIndex, eventCount, optional,
                optionalToUseCount, 0, new ArrayList<>(required),
                regionBeginIndex, previousMatchedKeyMoves, matchedKeyMoves,
                aliasBindings, negatedBindings);
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

    private static boolean anyMoveCouldMatchEvent(KeyMoveSet keyMoveSet, KeyEvent event,
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
