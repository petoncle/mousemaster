package mousemaster;

import java.util.List;

public record MatchResult(int matchedEventCount, boolean complete,
                           List<ComboMove> matchedMoves) {

    private static final MatchResult NO_MATCH =
            new MatchResult(0, false, List.of());

    public static MatchResult noMatch() {
        return NO_MATCH;
    }

    public boolean hasMatch() {
        return matchedEventCount > 0;
    }

    public ComboMove lastMatchedMove() {
        return matchedMoves.isEmpty() ? null : matchedMoves.getLast();
    }

}
