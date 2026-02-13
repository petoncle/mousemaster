package mousemaster;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record MoveSet(List<ComboMove> requiredMoves, List<ComboMove> optionalMoves) {

    int minMoveCount() {
        return requiredMoves.size();
    }

    int maxMoveCount() {
        return requiredMoves.size() + optionalMoves.size();
    }

    List<ComboMove> allMoves() {
        return Stream.concat(requiredMoves.stream(), optionalMoves.stream()).toList();
    }

    @Override
    public String toString() {
        if (requiredMoves.size() + optionalMoves.size() == 1) {
            ComboMove move = requiredMoves.isEmpty() ? optionalMoves.getFirst() : requiredMoves.getFirst();
            return move.toString() + (requiredMoves.isEmpty() ? "?" : "");
        }
        String inner = Stream.concat(
                requiredMoves.stream().map(Object::toString),
                optionalMoves.stream().map(m -> m.toString() + "?")
        ).collect(Collectors.joining(" "));
        return "{" + inner + "}";
    }

}
