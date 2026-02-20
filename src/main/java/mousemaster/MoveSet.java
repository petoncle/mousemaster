package mousemaster;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public sealed interface MoveSet permits MoveSet.KeyMoveSet, MoveSet.WaitMoveSet {

    int minMoveCount();

    int maxMoveCount();

    boolean canAbsorbEvents();

    record KeyMoveSet(List<ComboMove.KeyComboMove> requiredMoves,
                      List<ComboMove.KeyComboMove> optionalMoves) implements MoveSet {

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
            return false;
        }

        @Override
        public String toString() {
            if (requiredMoves.size() + optionalMoves.size() == 1) {
                ComboMove move = requiredMoves.isEmpty() ? optionalMoves.getFirst() :
                        requiredMoves.getFirst();
                return move.toString() + (requiredMoves.isEmpty() ? "?" : "");
            }
            return "{"
                   + Stream.concat(requiredMoves.stream().map(Object::toString),
                                   optionalMoves.stream().map(m -> m.toString() + "?"))
                           .collect(Collectors.joining(" "))
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
            return !waitMove.ignoredKeySet().equals(IgnoredKeySet.NONE);
        }

        @Override
        public String toString() {
            return waitMove.toString();
        }
    }

}
