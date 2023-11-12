package jmouseable.jmouseable;

import java.util.ArrayList;
import java.util.List;

public record ComboPreparation(List<KeyEvent> events) {

    public static ComboPreparation empty() {
        return new ComboPreparation(new ArrayList<>());
    }

    public int matchingMoveCount(Combo combo) {
        List<KeyEvent> preparationEvents = events;
        List<ComboMove> comboMoves = combo.moves();
        sub_combos_loop:
        for (int subComboMoveCount = comboMoves.size(); subComboMoveCount >= 1;
             subComboMoveCount--) {
            if (subComboMoveCount > preparationEvents.size())
                continue;
            List<ComboMove> subComboMoves = comboMoves.subList(0, subComboMoveCount);
            for (int subComboMoveIndex = 0; subComboMoveIndex < subComboMoves.size();
                 subComboMoveIndex++) {
                int preparationEventIndex =
                        preparationEvents.size() - subComboMoveCount + subComboMoveIndex;
                KeyEvent preparationEvent = preparationEvents.get(preparationEventIndex);
                ComboMove comboMove = subComboMoves.get(subComboMoveIndex);
                if (!preparationEvent.action().equals(comboMove.action()))
                    continue sub_combos_loop;
                if (subComboMoveIndex == 0)
                    continue;
                KeyEvent previousPreparationEvent =
                        preparationEvents.get(preparationEventIndex - 1);
                ComboMove previousComboMove = subComboMoves.get(subComboMoveIndex - 1);
                if (!previousComboMove.duration()
                                      .isRespected(previousPreparationEvent.time(),
                                              preparationEvent.time()))
                    continue sub_combos_loop;
            }
            return subComboMoveCount;
        }
        return 0;
    }
}
