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
            int minTotal = 0, maxTotal = 0;
            for (MoveSet ms : subMoveSets) {
                minTotal += ms.minEvents();
                maxTotal += ms.maxEvents();
            }
            if (minTotal > events.size())
                continue;
            int effectiveMax = Math.min(maxTotal, events.size());

            // Try total event counts from max down to min.
            for (int totalEvents = effectiveMax; totalEvents >= minTotal; totalEvents--) {
                int regionStart = events.size() - totalEvents;
                List<ComboMove> matchedMoves = new ArrayList<>();
                if (tryAssignEventsToMoveSets(subMoveSets, 0, regionStart,
                        regionStart + totalEvents, regionStart, matchedMoves)) {
                    boolean complete = (k == moveSets.size());
                    return new MatchResult(totalEvents, complete,
                            List.copyOf(matchedMoves));
                }
            }
        }
        return MatchResult.noMatch();
    }

    private boolean tryAssignEventsToMoveSets(
            List<MoveSet> moveSets, int moveSetIndex, int eventIndex, int eventEnd,
            int regionStart, List<ComboMove> matchedMoves) {
        if (moveSetIndex == moveSets.size())
            return eventIndex == eventEnd;

        MoveSet ms = moveSets.get(moveSetIndex);
        int remaining = eventEnd - eventIndex;
        int minForLater = 0;
        for (int i = moveSetIndex + 1; i < moveSets.size(); i++)
            minForLater += moveSets.get(i).minEvents();
        int maxForThis = Math.min(ms.maxEvents(), remaining - minForLater);

        for (int evCount = maxForThis; evCount >= ms.minEvents(); evCount--) {
            List<ComboMove> moveSetMatched = new ArrayList<>();
            if (tryMatchMoveSetEvents(eventIndex, evCount, ms, regionStart, matchedMoves,
                    moveSetMatched)) {
                int savedSize = matchedMoves.size();
                matchedMoves.addAll(moveSetMatched);
                if (tryAssignEventsToMoveSets(moveSets, moveSetIndex + 1,
                        eventIndex + evCount, eventEnd, regionStart, matchedMoves))
                    return true;
                while (matchedMoves.size() > savedSize) matchedMoves.removeLast();
            }
        }
        return false;
    }

    private boolean tryMatchMoveSetEvents(
            int eventStart, int eventCount, MoveSet moveSet,
            int regionStart, List<ComboMove> previousMatchedMoves,
            List<ComboMove> outMatchedMoves) {
        if (eventCount == 0)
            return true;
        List<ComboMove> required = moveSet.requiredMoves();
        List<ComboMove> optional = moveSet.optionalMoves();
        int optionalToUse = eventCount - required.size();
        if (optionalToUse < 0 || optionalToUse > optional.size())
            return false;

        // Fast path: singleton MoveSet (one required, no optionals).
        if (eventCount == 1 && required.size() == 1 && optional.isEmpty()) {
            ComboMove move = required.getFirst();
            KeyEvent event = events.get(eventStart);
            if (!event.key().equals(move.key()) || event.isPress() != move.isPress())
                return false;
            if (eventStart > regionStart) {
                KeyEvent prevEvent = events.get(eventStart - 1);
                ComboMove prevMove = previousMatchedMoves.getLast();
                if (!prevMove.duration().satisfied(prevEvent.time(), event.time()))
                    return false;
            }
            outMatchedMoves.add(move);
            return true;
        }

        // General path: try subsets of optional moves, then bipartite match.
        return tryOptionalSubsets(eventStart, eventCount, optional,
                optionalToUse, 0, new ArrayList<>(required),
                regionStart, previousMatchedMoves, outMatchedMoves);
    }

    private boolean tryOptionalSubsets(
            int eventStart, int eventCount,
            List<ComboMove> optional,
            int optionalToUse, int optionalIndex,
            List<ComboMove> candidates,
            int regionStart, List<ComboMove> previousMatchedMoves,
            List<ComboMove> outMatchedMoves) {
        if (optionalToUse == 0) {
            return tryBipartiteMatch(eventStart, eventCount, candidates,
                    regionStart, previousMatchedMoves, outMatchedMoves);
        }
        int remaining = optional.size() - optionalIndex;
        if (remaining < optionalToUse)
            return false;

        // Include optional[optionalIndex].
        candidates.add(optional.get(optionalIndex));
        if (tryOptionalSubsets(eventStart, eventCount, optional,
                optionalToUse - 1, optionalIndex + 1, candidates,
                regionStart, previousMatchedMoves, outMatchedMoves))
            return true;
        candidates.removeLast();

        // Skip optional[optionalIndex].
        return tryOptionalSubsets(eventStart, eventCount, optional,
                optionalToUse, optionalIndex + 1, candidates,
                regionStart, previousMatchedMoves, outMatchedMoves);
    }

    private boolean tryBipartiteMatch(
            int eventStart, int eventCount, List<ComboMove> candidates,
            int regionStart, List<ComboMove> previousMatchedMoves,
            List<ComboMove> outMatchedMoves) {
        boolean[] used = new boolean[candidates.size()];
        ComboMove[] assignment = new ComboMove[eventCount];
        if (assignEvents(eventStart, 0, eventCount, candidates, used, assignment,
                regionStart, previousMatchedMoves)) {
            outMatchedMoves.clear();
            for (ComboMove m : assignment) outMatchedMoves.add(m);
            return true;
        }
        return false;
    }

    private boolean assignEvents(
            int eventStart, int eventOffset, int eventCount,
            List<ComboMove> candidates, boolean[] used, ComboMove[] assignment,
            int regionStart, List<ComboMove> previousMatchedMoves) {
        if (eventOffset == eventCount)
            return true;

        int globalEventIndex = eventStart + eventOffset;
        KeyEvent event = events.get(globalEventIndex);

        // Duration check: skip for the first event in the matched region.
        if (globalEventIndex > regionStart) {
            KeyEvent prevEvent = events.get(globalEventIndex - 1);
            ComboMove prevMove;
            if (eventOffset > 0)
                prevMove = assignment[eventOffset - 1];
            else
                prevMove = previousMatchedMoves.getLast();
            if (!prevMove.duration().satisfied(prevEvent.time(), event.time()))
                return false;
        }

        for (int i = 0; i < candidates.size(); i++) {
            if (used[i]) continue;
            ComboMove candidate = candidates.get(i);
            if (event.key().equals(candidate.key()) && event.isPress() == candidate.isPress()) {
                used[i] = true;
                assignment[eventOffset] = candidate;
                if (assignEvents(eventStart, eventOffset + 1, eventCount,
                        candidates, used, assignment, regionStart, previousMatchedMoves))
                    return true;
                used[i] = false;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return events.toString();
    }
}
