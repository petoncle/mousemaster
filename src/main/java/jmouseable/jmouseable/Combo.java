package jmouseable.jmouseable;

import java.util.ArrayList;
import java.util.List;

public record Combo(List<ComboMove> moves) {

    public static Combo of(String string) {
        String[] moveNames = string.split("\\s+");
        if (moveNames.length == 0)
            throw new IllegalArgumentException("Empty combo: " + string);
        List<ComboMove> moves = new ArrayList<>();
        for (String moveName : moveNames) {
            String actionName;
            boolean eventMustBeEaten;
            if (moveName.startsWith(";")) {
                actionName = moveName.substring(1);
                eventMustBeEaten = false;
            }
            else {
                actionName = moveName;
                eventMustBeEaten = true;
            }
            if (!actionName.startsWith("_") && !actionName.startsWith("^"))
                throw new IllegalArgumentException("Invalid move: " + actionName);
            KeyState state =
                    actionName.startsWith("_") ? KeyState.PRESSED : KeyState.RELEASED;
            String keyName = actionName.substring(1);
            Key key = Key.keyByName.get(keyName);
            if (key == null)
                throw new IllegalArgumentException("Invalid key: " + keyName);
            moves.add(new ComboMove(new KeyAction(key, state), eventMustBeEaten));
        }
        return new Combo(List.copyOf(moves));
    }

}
