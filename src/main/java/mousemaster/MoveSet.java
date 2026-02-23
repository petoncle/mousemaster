package mousemaster;

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
                      ComboMove.WaitComboMove waitMove) implements MoveSet {

        public KeyMoveSet(List<ComboMove.KeyComboMove> requiredMoves,
                          List<ComboMove.KeyComboMove> optionalMoves) {
            this(requiredMoves, optionalMoves, null);
        }

        @Override
        public int minMoveCount() {
            return requiredMoves.size();
        }

        @Override
        public int maxMoveCount() {
            return requiredMoves.size() + optionalMoves.size();
        }

        @Override
        public boolean canAbsorbEvents() {
            return waitMove != null;
        }

        @Override
        public String toString() {
            String ignoredSpec = "";
            if (waitMove != null) {
                ignoredSpec = " " + waitMove;
            }
            if (requiredMoves.size() + optionalMoves.size() == 1 && ignoredSpec.isEmpty()) {
                ComboMove move = requiredMoves.isEmpty() ? optionalMoves.getFirst() :
                        requiredMoves.getFirst();
                return move.toString() + (requiredMoves.isEmpty() ? "?" : "");
            }
            return "{"
                   + Stream.concat(requiredMoves.stream().map(Object::toString),
                                   optionalMoves.stream().map(m -> m.toString() + "?"))
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
