package mousemaster;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ComboSequenceMatch(List<ResolvedKeyComboMove> matchedKeyMoves, boolean complete,
                                 int matchedMoveSetCount, // Includes wait moves.
                                 int lastMatchedKeyMoveEventIndex, // Index in ComboPreparation.events(), or -1.
                                 boolean lastEventAbsorbedByWait,
                                 Set<Key> absorbedPressedKeys,
                                 AliasResolution aliasResolution) {

    private static final ComboSequenceMatch NO_MATCH =
            new ComboSequenceMatch(List.of(), false, 0, -1, false, Set.of(), new AliasResolution(Map.of()));

    public static ComboSequenceMatch noMatch() {
        return NO_MATCH;
    }

    public boolean hasMatch() {
        return !matchedKeyMoves.isEmpty();
    }

    public ResolvedKeyComboMove lastMatchedKeyMove() {
        return matchedKeyMoves.isEmpty() ? null : matchedKeyMoves.getLast();
    }

}
