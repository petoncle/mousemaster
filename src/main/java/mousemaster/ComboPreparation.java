package mousemaster;

import java.util.ArrayList;
import java.util.List;

public record ComboPreparation(List<KeyEvent> events) {

    public static ComboPreparation empty() {
        return new ComboPreparation(new ArrayList<>());
    }

    public MatchResult match(ComboSequence sequence) {
        List<MoveSet> moveSets = sequence.moveSets();
        if (moveSets.isEmpty() || events.isEmpty())
            return MatchResult.noMatch();

        // Try first K moveSets (K from total down to 1).
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

            // Try total event counts from max down to min.
            for (int totalEventCount = effectiveMaxEventCount;
                 totalEventCount >= minTotalEventCount; totalEventCount--) {
                int regionBeginIndex = events.size() - totalEventCount;
                List<ComboMove> matchedMoves = new ArrayList<>();
                if (tryAssignEventsToMoveSets(subMoveSets, 0, regionBeginIndex,
                        regionBeginIndex + totalEventCount, regionBeginIndex,
                        matchedMoves)) {
                    boolean complete = (k == moveSets.size());
                    return new MatchResult(totalEventCount, complete,
                            List.copyOf(matchedMoves));
                }
            }
        }
        return MatchResult.noMatch();
    }

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

    private boolean tryBipartiteMatch(
            int eventBeginIndex, int eventCount, List<ComboMove> moves,
            int regionBeginIndex, List<ComboMove> previousMatchedMoves,
            List<ComboMove> matchedMoves) {
        boolean[] moveUsed = new boolean[moves.size()];
        ComboMove[] assignedMoves = new ComboMove[eventCount];
        if (assignEvents(eventBeginIndex, 0, eventCount, moves, moveUsed,
                assignedMoves, regionBeginIndex, previousMatchedMoves)) {
            matchedMoves.clear();
            for (ComboMove move : assignedMoves) matchedMoves.add(move);
            return true;
        }
        return false;
    }

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
