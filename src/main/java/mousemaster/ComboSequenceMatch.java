package mousemaster;

import java.util.List;
import java.util.Map;

public record ComboSequenceMatch(List<ResolvedComboMove> matchedMoves, boolean complete,
                                 int matchedMoveSetCount,
                                 boolean lastEventAbsorbedByWait,
                                 AliasResolution aliasResolution) {

    private static final ComboSequenceMatch NO_MATCH =
            new ComboSequenceMatch(List.of(), false, 0, false, new AliasResolution(Map.of()));

    public static ComboSequenceMatch noMatch() {
        return NO_MATCH;
    }

    public boolean hasMatch() {
        return !matchedMoves.isEmpty();
    }

    public ResolvedComboMove lastMatchedMove() {
        return matchedMoves.isEmpty() ? null : matchedMoves.getLast();
    }

}
