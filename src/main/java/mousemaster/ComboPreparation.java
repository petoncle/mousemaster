package mousemaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
     * <p>
     * The match is "complete" if all MoveSets are matched, "partial" if only a prefix is.
     * We try the longest (most MoveSets) match first, and for each, the most events first.
     * The matched events are always a suffix of the preparation (most recent events).
     */
    public ComboSequenceMatch match(ComboSequence sequence) {
        List<MoveSet> moveSets = sequence.moveSets();
        if (moveSets.isEmpty())
            return new ComboSequenceMatch(List.of(), true);
        if (events.isEmpty())
            return ComboSequenceMatch.noMatch();

        // Try matching the first K moveSets, starting with K = all (complete match)
        // and decreasing to K = 1 (partial match of just the first MoveSet).
        for (int k = moveSets.size(); k >= 1; k--) {
            List<MoveSet> subMoveSets = moveSets.subList(0, k);
            int minTotalEventCount = 0, maxTotalEventCount = 0;
            for (MoveSet moveSet : subMoveSets) {
                minTotalEventCount += moveSet.minMoveCount();
                maxTotalEventCount += moveSet.maxMoveCount();
            }
            if (minTotalEventCount > events.size())
                continue;
            int effectiveMaxEventCount = Math.min(maxTotalEventCount, events.size());

            // Try consuming totalEventCount events from the end of the preparation,
            // starting with the most events (greediest match) first.
            for (int totalEventCount = effectiveMaxEventCount;
                 totalEventCount >= minTotalEventCount; totalEventCount--) {
                int regionBeginIndex = events.size() - totalEventCount;
                List<ComboMove> matchedMoves = new ArrayList<>();
                if (tryAssignEventsToMoveSets(subMoveSets, 0, regionBeginIndex,
                        regionBeginIndex + totalEventCount, regionBeginIndex,
                        matchedMoves)) {
                    boolean complete = (k == moveSets.size());
                    return new ComboSequenceMatch(List.copyOf(matchedMoves), complete);
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
            List<ComboMove> matchedMoves) {
        if (moveSetIndex == moveSets.size())
            return eventIndex == eventEndIndex;

        MoveSet moveSet = moveSets.get(moveSetIndex);
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
            List<ComboMove> moveSetMatchedMoves = new ArrayList<>();
            if (tryMatchMoveSetEvents(eventIndex, eventCount, moveSet,
                    regionBeginIndex, matchedMoves, moveSetMatchedMoves)) {
                int savedSize = matchedMoves.size();
                matchedMoves.addAll(moveSetMatchedMoves);
                if (tryAssignEventsToMoveSets(moveSets, moveSetIndex + 1,
                        eventIndex + eventCount, eventEndIndex,
                        regionBeginIndex, matchedMoves))
                    return true;
                while (matchedMoves.size() > savedSize)
                    matchedMoves.removeLast();
            }
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
            int regionBeginIndex, List<ComboMove> previousMatchedMoves,
            List<ComboMove> matchedMoves) {
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
            if (!event.key().equals(move.key()) || event.isPress() != move.isPress())
                return false;
            if (eventBeginIndex > regionBeginIndex) {
                KeyEvent previousEvent = events.get(eventBeginIndex - 1);
                ComboMove previousMove = previousMatchedMoves.getLast();
                if (!previousMove.duration().satisfied(previousEvent.time(), event.time()))
                    return false;
            }
            matchedMoves.add(move);
            return true;
        }

        // General path: try subsets of optional moves, then bipartite match.
        return tryOptionalSubsets(eventBeginIndex, eventCount, optional,
                optionalToUseCount, 0, new ArrayList<>(required),
                regionBeginIndex, previousMatchedMoves, matchedMoves);
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
            int regionBeginIndex, List<ComboMove> previousMatchedMoves,
            List<ComboMove> matchedMoves) {
        if (optionalToUseCount == 0) {
            return tryBipartiteMatch(eventBeginIndex, eventCount, moves,
                    regionBeginIndex, previousMatchedMoves, matchedMoves);
        }
        int remainingOptionalCount = optional.size() - optionalIndex;
        if (remainingOptionalCount < optionalToUseCount)
            return false;

        // Include optional[optionalIndex].
        moves.add(optional.get(optionalIndex));
        if (tryOptionalSubsets(eventBeginIndex, eventCount, optional,
                optionalToUseCount - 1, optionalIndex + 1, moves,
                regionBeginIndex, previousMatchedMoves, matchedMoves))
            return true;
        moves.removeLast();

        // Skip optional[optionalIndex].
        return tryOptionalSubsets(eventBeginIndex, eventCount, optional,
                optionalToUseCount, optionalIndex + 1, moves,
                regionBeginIndex, previousMatchedMoves, matchedMoves);
    }

    /**
     * Tries to find a one-to-one assignment between moves and events.
     * Moves within a MoveSet are unordered: {+a #b} can match events [+a #b] or [#b +a].
     * Each assignment must satisfy key/press-release matching and duration constraints.
     */
    private boolean tryBipartiteMatch(
            int eventBeginIndex, int eventCount, List<ComboMove> moves,
            int regionBeginIndex, List<ComboMove> previousMatchedMoves,
            List<ComboMove> matchedMoves) {
        boolean[] moveUsed = new boolean[moves.size()];
        ComboMove[] assignedMoves = new ComboMove[eventCount];
        if (assignEvents(eventBeginIndex, 0, eventCount, moves, moveUsed,
                assignedMoves, regionBeginIndex, previousMatchedMoves)) {
            matchedMoves.clear();
            Collections.addAll(matchedMoves, assignedMoves);
            return true;
        }
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
            ComboMove[] assignedMoves,
            int regionBeginIndex, List<ComboMove> previousMatchedMoves) {
        if (eventOffset == eventCount)
            return true;

        int globalEventIndex = eventBeginIndex + eventOffset;
        KeyEvent event = events.get(globalEventIndex);

        // Duration check: skip for the first event in the matched region.
        if (globalEventIndex > regionBeginIndex) {
            KeyEvent previousEvent = events.get(globalEventIndex - 1);
            ComboMove previousMove;
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
            if (event.key().equals(move.key()) &&
                event.isPress() == move.isPress()) {
                moveUsed[moveIndex] = true;
                assignedMoves[eventOffset] = move;
                if (assignEvents(eventBeginIndex, eventOffset + 1, eventCount,
                        moves, moveUsed, assignedMoves, regionBeginIndex,
                        previousMatchedMoves))
                    return true;
                moveUsed[moveIndex] = false;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return events.toString();
    }
}
