package mousemaster;

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
            return new ComboSequenceMatch(List.of(), true, 0, new AliasResolution(Map.of()));
        // A sequence that is only wait moves (e.g. "wait-2000") has no event-based moves.
        // It is "complete" with 0 matched events: ComboWatcher handles the wait duration.
        boolean allWait = moveSets.stream().allMatch(MoveSet::isWaitMoveSet);
        if (allWait)
            return new ComboSequenceMatch(List.of(), true, moveSets.size(), new AliasResolution(Map.of()));
        if (events.isEmpty())
            return ComboSequenceMatch.noMatch();

        // Try matching the first K moveSets, starting with K = all (complete match)
        // and decreasing to K = 1 (partial match of just the first MoveSet).
        for (int k = moveSets.size(); k >= 1; k--) {
            List<MoveSet> subMoveSets = moveSets.subList(0, k);
            // Skip if all subMoveSets are wait-only (no events to match).
            if (subMoveSets.stream().allMatch(MoveSet::isWaitMoveSet))
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
                List<ResolvedComboMove> matchedMoves = new ArrayList<>();
                Map<String, Key> aliasBindings = new HashMap<>();
                if (tryAssignEventsToMoveSets(subMoveSets, 0, regionBeginIndex,
                        regionBeginIndex + totalEventCount, regionBeginIndex,
                        matchedMoves, aliasBindings)) {
                    boolean complete = (k == moveSets.size());
                    return new ComboSequenceMatch(List.copyOf(matchedMoves), complete,
                            k, new AliasResolution(Map.copyOf(aliasBindings)));
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
            List<ResolvedComboMove> matchedMoves, Map<String, Key> aliasBindings) {
        if (moveSetIndex == moveSets.size())
            return eventIndex == eventEndIndex;

        MoveSet moveSet = moveSets.get(moveSetIndex);

        // Wait MoveSets: absorb non-breaking events, then skip to next MoveSet.
        if (moveSet.isWaitMoveSet()) {
            ComboMove.WaitComboMove waitMove = (ComboMove.WaitComboMove) moveSet.requiredMoves().getFirst();
            if (moveSet.canAbsorbEvents()) {
                // Absorbing wait: try consuming 0..maxAbsorb events.
                int minForRemaining = 0;
                for (int later = moveSetIndex + 1; later < moveSets.size(); later++)
                    minForRemaining += moveSets.get(later).minMoveCount();
                int maxAbsorb = (eventEndIndex - eventIndex) - minForRemaining;
                for (int absorb = maxAbsorb; absorb >= 0; absorb--) {
                    // Check all absorbed events are non-breaking.
                    boolean allNonBreaking = true;
                    for (int i = 0; i < absorb; i++) {
                        if (waitMove.keyBreaksWait(events.get(eventIndex + i).key())) {
                            allNonBreaking = false;
                            break;
                        }
                    }
                    if (!allNonBreaking)
                        continue;
                    int nextEventIndex = eventIndex + absorb;
                    // For mid-sequence wait: check time gap from pre-wait event
                    // to first post-wait event.
                    if (eventIndex > regionBeginIndex && nextEventIndex < eventEndIndex) {
                        KeyEvent previousEvent = events.get(eventIndex - 1);
                        KeyEvent nextEvent = events.get(nextEventIndex);
                        if (!waitMove.duration().satisfied(previousEvent.time(), nextEvent.time()))
                            continue;
                    }
                    // Pass nextEventIndex as regionBeginIndex so the first event
                    // after the wait skips the per-move duration check (the wait
                    // already validated the time gap).
                    if (tryAssignEventsToMoveSets(moveSets, moveSetIndex + 1,
                            nextEventIndex, eventEndIndex, nextEventIndex,
                            matchedMoves, aliasBindings))
                        return true;
                }
                return false;
            }
            else {
                // Non-absorbing wait (plain wait: all keys break): consume 0 events.
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
                        matchedMoves, aliasBindings);
            }
        }

        int remainingEventCount = eventEndIndex - eventIndex;
        int minEventCountForRemainingMoveSets = 0;
        for (int laterMoveSetIndex = moveSetIndex + 1;
             laterMoveSetIndex < moveSets.size(); laterMoveSetIndex++)
            minEventCountForRemainingMoveSets +=
                    moveSets.get(laterMoveSetIndex).minMoveCount();
        int maxEventCountForMoveSet = Math.min(moveSet.maxMoveCount(),
                remainingEventCount - minEventCountForRemainingMoveSets);

        for (int eventCount = maxEventCountForMoveSet;
             eventCount >= moveSet.minMoveCount(); eventCount--) {
            List<ResolvedComboMove> moveSetMatchedMoves = new ArrayList<>();
            Map<String, Key> savedAliasBindings = new HashMap<>(aliasBindings);
            if (tryMatchMoveSetEvents(eventIndex, eventCount, moveSet,
                    regionBeginIndex, matchedMoves, moveSetMatchedMoves, aliasBindings)) {
                int savedSize = matchedMoves.size();
                matchedMoves.addAll(moveSetMatchedMoves);
                if (tryAssignEventsToMoveSets(moveSets, moveSetIndex + 1,
                        eventIndex + eventCount, eventEndIndex,
                        regionBeginIndex, matchedMoves, aliasBindings))
                    return true;
                while (matchedMoves.size() > savedSize)
                    matchedMoves.removeLast();
            }
            // Restore aliasBindings on backtrack.
            aliasBindings.clear();
            aliasBindings.putAll(savedAliasBindings);
        }
        return false;
    }

    /**
     * Tries to match eventCount consecutive events (starting at eventBeginIndex)
     * against a single MoveSet. All required moves must be matched. The remaining
     * event slots (eventCount - requiredCount) are filled by selecting that many
     * optional moves.
     * <p>
     * Duration constraint: the time between consecutive events must satisfy the
     * duration of the previous matched move. The first event in the matched region
     * has no duration constraint.
     */
    private boolean tryMatchMoveSetEvents(
            int eventBeginIndex, int eventCount, MoveSet moveSet,
            int regionBeginIndex, List<ResolvedComboMove> previousMatchedMoves,
            List<ResolvedComboMove> matchedMoves, Map<String, Key> aliasBindings) {
        if (eventCount == 0)
            return true;
        List<ComboMove> required = moveSet.requiredMoves();
        List<ComboMove> optional = moveSet.optionalMoves();
        int optionalToUseCount = eventCount - required.size();
        if (optionalToUseCount < 0 || optionalToUseCount > optional.size())
            return false;

        // Fast path: singleton MoveSet (one required, no optionals).
        if (eventCount == 1 && required.size() == 1 && optional.isEmpty()) {
            ComboMove move = required.getFirst();
            KeyEvent event = events.get(eventBeginIndex);
            if (!moveMatchesEvent(move, event, aliasBindings))
                return false;
            if (eventBeginIndex > regionBeginIndex) {
                KeyEvent previousEvent = events.get(eventBeginIndex - 1);
                ResolvedComboMove previousMove = previousMatchedMoves.getLast();
                if (!previousMove.duration().satisfied(previousEvent.time(), event.time()))
                    return false;
            }
            bindAlias(move, event.key(), aliasBindings);
            matchedMoves.add(resolvedMove(move, event.key()));
            return true;
        }

        // General path: try subsets of optional moves, then bipartite match.
        return tryOptionalSubsets(eventBeginIndex, eventCount, optional,
                optionalToUseCount, 0, new ArrayList<>(required),
                regionBeginIndex, previousMatchedMoves, matchedMoves, aliasBindings);
    }

    /**
     * Recursively selects optionalToUseCount optional moves to include alongside
     * the required moves. Once enough optionals are selected, attempts a bipartite
     * match of the combined moves against the events.
     */
    private boolean tryOptionalSubsets(
            int eventBeginIndex, int eventCount,
            List<ComboMove> optional,
            int optionalToUseCount, int optionalIndex,
            List<ComboMove> moves,
            int regionBeginIndex, List<ResolvedComboMove> previousMatchedMoves,
            List<ResolvedComboMove> matchedMoves, Map<String, Key> aliasBindings) {
        if (optionalToUseCount == 0) {
            return tryBipartiteMatch(eventBeginIndex, eventCount, moves,
                    regionBeginIndex, previousMatchedMoves, matchedMoves, aliasBindings);
        }
        int remainingOptionalCount = optional.size() - optionalIndex;
        if (remainingOptionalCount < optionalToUseCount)
            return false;

        // Include optional[optionalIndex].
        moves.add(optional.get(optionalIndex));
        if (tryOptionalSubsets(eventBeginIndex, eventCount, optional,
                optionalToUseCount - 1, optionalIndex + 1, moves,
                regionBeginIndex, previousMatchedMoves, matchedMoves, aliasBindings))
            return true;
        moves.removeLast();

        // Skip optional[optionalIndex].
        return tryOptionalSubsets(eventBeginIndex, eventCount, optional,
                optionalToUseCount, optionalIndex + 1, moves,
                regionBeginIndex, previousMatchedMoves, matchedMoves, aliasBindings);
    }

    /**
     * Tries to find a one-to-one assignment between moves and events.
     * Moves within a MoveSet are unordered: {+a #b} can match events [+a #b] or [#b +a].
     * Each assignment must satisfy key/press-release matching and duration constraints.
     */
    private boolean tryBipartiteMatch(
            int eventBeginIndex, int eventCount, List<ComboMove> moves,
            int regionBeginIndex, List<ResolvedComboMove> previousMatchedMoves,
            List<ResolvedComboMove> matchedMoves, Map<String, Key> aliasBindings) {
        boolean[] moveUsed = new boolean[moves.size()];
        ResolvedComboMove[] assignedMoves = new ResolvedComboMove[eventCount];
        Map<String, Key> savedAliasBindings = new HashMap<>(aliasBindings);
        if (assignEvents(eventBeginIndex, 0, eventCount, moves, moveUsed,
                assignedMoves, regionBeginIndex, previousMatchedMoves, aliasBindings)) {
            matchedMoves.clear();
            Collections.addAll(matchedMoves, assignedMoves);
            return true;
        }
        // Restore aliasBindings on failure.
        aliasBindings.clear();
        aliasBindings.putAll(savedAliasBindings);
        return false;
    }

    /**
     * Recursively assigns moves to events one at a time. For each event, tries
     * every unused move that matches the event's key and press/release. Backtracks
     * if no valid assignment is found for subsequent events.
     */
    private boolean assignEvents(
            int eventBeginIndex, int eventOffset, int eventCount,
            List<ComboMove> moves, boolean[] moveUsed,
            ResolvedComboMove[] assignedMoves,
            int regionBeginIndex, List<ResolvedComboMove> previousMatchedMoves,
            Map<String, Key> aliasBindings) {
        if (eventOffset == eventCount)
            return true;

        int globalEventIndex = eventBeginIndex + eventOffset;
        KeyEvent event = events.get(globalEventIndex);

        // Duration check: skip for the first event in the matched region.
        if (globalEventIndex > regionBeginIndex) {
            KeyEvent previousEvent = events.get(globalEventIndex - 1);
            ResolvedComboMove previousMove;
            if (eventOffset > 0)
                previousMove = assignedMoves[eventOffset - 1];
            else
                previousMove = previousMatchedMoves.getLast();
            if (!previousMove.duration().satisfied(previousEvent.time(), event.time()))
                return false;
        }

        for (int moveIndex = 0; moveIndex < moves.size(); moveIndex++) {
            if (moveUsed[moveIndex]) continue;
            ComboMove move = moves.get(moveIndex);
            if (moveMatchesEvent(move, event, aliasBindings)) {
                moveUsed[moveIndex] = true;
                Map<String, Key> savedAliasBindings = new HashMap<>(aliasBindings);
                bindAlias(move, event.key(), aliasBindings);
                assignedMoves[eventOffset] = resolvedMove(move, event.key());
                if (assignEvents(eventBeginIndex, eventOffset + 1, eventCount,
                        moves, moveUsed, assignedMoves, regionBeginIndex,
                        previousMatchedMoves, aliasBindings))
                    return true;
                moveUsed[moveIndex] = false;
                // Restore aliasBindings on backtrack.
                aliasBindings.clear();
                aliasBindings.putAll(savedAliasBindings);
            }
        }
        return false;
    }

    /**
     * Checks whether a move matches an event, considering alias aliasBindings.
     * For alias moves: if the alias is already bound, the event key must equal
     * the bound key. If unbound, the event key must be in the alias's key set.
     * For regular key moves: direct key equality.
     */
    private static boolean moveMatchesEvent(ComboMove move, KeyEvent event,
                                            Map<String, Key> aliasBindings) {
        if (move instanceof ComboMove.WaitComboMove)
            return false; // Wait moves don't match events.
        if (event.isPress() != move.isPress())
            return false;
        KeyOrAlias keyOrAlias = move.keyOrAlias();
        if (keyOrAlias.isAlias()) {
            Key bound = aliasBindings.get(keyOrAlias.aliasName());
            if (bound != null)
                return event.key().equals(bound);
            return keyOrAlias.matchesKey(event.key());
        }
        return event.key().equals(keyOrAlias.key());
    }

    /**
     * Binds an alias to a key if the move is an alias move.
     */
    private static void bindAlias(ComboMove move, Key key,
                                  Map<String, Key> aliasBindings) {
        KeyOrAlias keyOrAlias = move.keyOrAlias();
        if (keyOrAlias.isAlias())
            aliasBindings.put(keyOrAlias.aliasName(), key);
    }

    /**
     * Returns a ResolvedComboMove with the concrete key from the matched event.
     */
    private static ResolvedComboMove resolvedMove(ComboMove move, Key matchedKey) {
        return switch (move) {
            case ComboMove.PressComboMove p ->
                    new ResolvedComboMove.ResolvedPressComboMove(
                            matchedKey, p.eventMustBeEaten(), p.duration());
            case ComboMove.ReleaseComboMove r ->
                    new ResolvedComboMove.ResolvedReleaseComboMove(
                            matchedKey, r.duration());
            case ComboMove.WaitComboMove w -> throw new IllegalStateException();
        };
    }

    @Override
    public String toString() {
        return events.toString();
    }
}
