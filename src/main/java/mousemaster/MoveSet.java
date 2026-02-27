package mousemaster;

import mousemaster.ComboMove.TapComboMove;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public sealed interface MoveSet permits MoveSet.KeyMoveSet, MoveSet.WaitMoveSet {

    int minMoveCount();

    int maxMoveCount();

    boolean canAbsorbEvents();

    /**
     * @param waitMove If non-null, defines which interleaved key events are
     *                 ignored (absorbed) within this MoveSet — reuses
     *                 {@link ComboMove.WaitComboMove} for its ignoredKeySet,
     *                 ignoredKeysEatEvents, and duration fields.
     */
    record KeyMoveSet(List<ComboMove.KeyComboMove> requiredMoves,
                      List<ComboMove.KeyComboMove> optionalMoves,
                      ComboMove.WaitComboMove waitMove,
                      boolean atLeastOneOptional) implements MoveSet {

        public KeyMoveSet(List<ComboMove.KeyComboMove> requiredMoves,
                          List<ComboMove.KeyComboMove> optionalMoves) {
            this(requiredMoves, optionalMoves, null, false);
        }

        public KeyMoveSet(List<ComboMove.KeyComboMove> requiredMoves,
                          List<ComboMove.KeyComboMove> optionalMoves,
                          ComboMove.WaitComboMove waitMove) {
            this(requiredMoves, optionalMoves, waitMove, false);
        }

        @Override
        public int minMoveCount() {
            int count = 0;
            for (ComboMove.KeyComboMove m : requiredMoves)
                count += (m instanceof TapComboMove) ? 2 : 1;
            if (atLeastOneOptional && !optionalMoves.isEmpty()) {
                // At least one optional move must match.
                // Minimum cost is 1 for press/release, 2 for tap.
                int minOptionalSlots = Integer.MAX_VALUE;
                for (ComboMove.KeyComboMove m : optionalMoves)
                    minOptionalSlots = Math.min(minOptionalSlots,
                            (m instanceof TapComboMove) ? 2 : 1);
                count += minOptionalSlots;
            }
            return count;
        }

        /**
         * Slot count from required moves only (excludes atLeastOne minimum).
         */
        public int requiredMoveSlotCount() {
            int count = 0;
            for (ComboMove.KeyComboMove m : requiredMoves)
                count += (m instanceof TapComboMove) ? 2 : 1;
            return count;
        }

        @Override
        public int maxMoveCount() {
            int count = 0;
            for (ComboMove.KeyComboMove m : requiredMoves)
                count += (m instanceof TapComboMove) ? 2 : 1;
            for (ComboMove.KeyComboMove m : optionalMoves)
                count += (m instanceof TapComboMove) ? 2 : 1;
            return count;
        }

        @Override
        public boolean canAbsorbEvents() {
            return waitMove != null;
        }

        @Override
        public String toString() {
            String optSuffix = atLeastOneOptional ? "+" : "?";
            String ignoredSpec = "";
            if (waitMove != null) {
                ignoredSpec = " " + waitMove;
            }
            if (requiredMoves.size() + optionalMoves.size() == 1 && ignoredSpec.isEmpty()) {
                ComboMove move = requiredMoves.isEmpty() ? optionalMoves.getFirst() :
                        requiredMoves.getFirst();
                return move.toString() + (requiredMoves.isEmpty() ? optSuffix : "");
            }
            return "{"
                   + Stream.concat(requiredMoves.stream().map(Object::toString),
                                   optionalMoves.stream().map(m -> m.toString() + optSuffix))
                           .collect(Collectors.joining(" "))
                   + ignoredSpec
                   + "}";
        }
    }

    record WaitMoveSet(ComboMove.WaitComboMove waitMove) implements MoveSet {

        @Override
        public int minMoveCount() {
            return 0;
        }

        @Override
        public int maxMoveCount() {
            return 0;
        }

        @Override
        public boolean canAbsorbEvents() {
            return !waitMove.ignoredKeySet().equals(KeySet.NONE);
        }

        @Override
        public String toString() {
            return waitMove.toString();
        }
    }

}
