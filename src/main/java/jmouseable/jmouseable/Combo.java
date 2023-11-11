package jmouseable.jmouseable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record Combo(List<ComboMove> moves) {

    public static Combo of(String string) {
        String[] moveNames = string.split("\\s+");
        if (moveNames.length == 0)
            throw new IllegalArgumentException("Empty combo: " + string);
        List<ComboMove> moves = new ArrayList<>();
        for (String moveName : moveNames) {
            String actionAndTimeout;
            boolean eventMustBeEaten;
            if (moveName.startsWith("?")) {
                actionAndTimeout = moveName.substring(1);
                eventMustBeEaten = false;
            }
            else {
                actionAndTimeout = moveName;
                eventMustBeEaten = true;
            }
            String action;
            Duration breakingTimeout;
            try {
                breakingTimeout = Duration.ofMillis(
                        Integer.parseUnsignedInt(actionAndTimeout.replaceFirst("\\D+", "")));
                action = actionAndTimeout.replaceFirst("\\d+", "");
            } catch (NumberFormatException e) {
                breakingTimeout = null;
                action = actionAndTimeout;
            }
            if (!action.startsWith("_") && !action.startsWith("^"))
                throw new IllegalArgumentException("Invalid move: " + action);
            KeyState state =
                    action.startsWith("_") ? KeyState.PRESSED : KeyState.RELEASED;
            String keyName = action.substring(1);
            Key key = Key.keyByName.get(keyName);
            if (key == null)
                throw new IllegalArgumentException("Invalid key: " + keyName);
            moves.add(new ComboMove(new KeyAction(key, state), breakingTimeout, eventMustBeEaten));
        }
        return new Combo(List.copyOf(moves));
    }

    public static List<Combo> multiCombo(String multiComboString) {
        return Arrays.stream(multiComboString.split("\\s*\\|\\s*"))
                     .map(Combo::of)
                     .toList();
    }
}
