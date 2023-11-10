package jmouseable.jmouseable;

import java.util.ArrayList;
import java.util.List;

public record ComboPreparation(List<KeyEvent> events) {

    public static ComboPreparation empty() {
        return new ComboPreparation(new ArrayList<>());
    }

    public int matchingMoveCount(Combo combo) {
        List<KeyAction> preparationActions =
                events.stream().map(KeyEvent::action).toList();
        List<KeyAction> comboActions =
                combo.moves().stream().map(ComboMove::action).toList();
        for (int subComboActionCount = comboActions.size(); subComboActionCount >= 1;
             subComboActionCount--) {
            if (subComboActionCount > preparationActions.size())
                continue;
            List<KeyAction> subComboActions =
                    comboActions.subList(0, subComboActionCount);
            if (preparationActions.subList(
                    preparationActions.size() - subComboActionCount,
                    preparationActions.size()).equals(subComboActions))
                return subComboActionCount;
        }
        return 0;
    }
}
