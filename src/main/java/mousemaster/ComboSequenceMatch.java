package mousemaster;

import java.util.List;

public record ComboSequenceMatch(List<ComboMove> matchedMoves, boolean complete) {

    private static final ComboSequenceMatch NO_MATCH = new ComboSequenceMatch(List.of(), false);

    public static ComboSequenceMatch noMatch() {
        return NO_MATCH;
    }

    public boolean hasMatch() {
        return !matchedMoves.isEmpty();
    }

    public ComboMove lastMatchedMove() {
        return matchedMoves.isEmpty() ? null : matchedMoves.getLast();
    }

}
