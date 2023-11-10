package jmouseable.jmouseable;

import java.util.ArrayList;
import java.util.List;

public record Combo(List<KeyAction> actions) {

    public static Combo of(String string) {
        String[] actionNames = string.split("\\s+");
        if (actionNames.length == 0)
            throw new IllegalArgumentException("Empty combo: " + string);
        List<KeyAction> actions = new ArrayList<>();
        for (String actionName : actionNames) {
            if (!actionName.startsWith("_") && !actionName.startsWith("^"))
                throw new IllegalArgumentException("Invalid action: " + actionName);
            KeyState state =
                    actionName.startsWith("_") ? KeyState.PRESSED : KeyState.RELEASED;
            String keyName = actionName.substring(1);
            Key key = Key.keyByName.get(keyName);
            if (key == null)
                throw new IllegalArgumentException("Invalid key: " + keyName);
            actions.add(new KeyAction(key, state));
        }
        return new Combo(List.copyOf(actions));
    }

}
