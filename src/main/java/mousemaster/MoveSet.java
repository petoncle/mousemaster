package mousemaster;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record MoveSet(List<ComboMove> requiredMoves, List<ComboMove> optionalMoves) {

    boolean isWaitMoveSet() {
        return requiredMoves.size() == 1 && optionalMoves.isEmpty() &&
               requiredMoves.getFirst() instanceof ComboMove.WaitComboMove;
    }

    /**
     * Returns true if this wait MoveSet can absorb (skip over) events during matching.
     * A wait can absorb events unless no key is ignored (plain wait).
     */
    boolean canAbsorbEvents() {
        if (!isWaitMoveSet())
            return false;
        ComboMove.WaitComboMove wait = (ComboMove.WaitComboMove) requiredMoves.getFirst();
        return !wait.ignoredKeySet().equals(IgnoredKeySet.NONE);
    }

    int minMoveCount() {
        return isWaitMoveSet() ? 0 : requiredMoves.size();
    }

    int maxMoveCount() {
        return isWaitMoveSet() ? 0 : requiredMoves.size() + optionalMoves.size();
    }

    @Override
    public String toString() {
        if (requiredMoves.size() + optionalMoves.size() == 1) {
            ComboMove move = requiredMoves.isEmpty() ? optionalMoves.getFirst() : requiredMoves.getFirst();
            return move.toString() + (requiredMoves.isEmpty() ? "?" : "");
        }
        return "{"
               + Stream.concat(requiredMoves.stream().map(Object::toString),
                               optionalMoves.stream().map(m -> m.toString() + "?"))
                       .collect(Collectors.joining(" "))
               + "}";
    }

}
