package mousemaster;

import java.util.List;

public record Match(List<ComboMove> matchedMoves, boolean complete) {

    private static final Match NO_MATCH = new Match(List.of(), false);

    public static Match noMatch() {
        return NO_MATCH;
    }

    public boolean hasMatch() {
        return !matchedMoves.isEmpty();
    }

    public ComboMove lastMatchedMove() {
        return matchedMoves.isEmpty() ? null : matchedMoves.getLast();
    }

}
