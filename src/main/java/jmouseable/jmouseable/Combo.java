package jmouseable.jmouseable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Combo(List<ComboMove> moves) {

    public static Combo of(String string, ComboMoveDuration defaultMoveDuration) {
        String[] moveNames = string.split("\\s+");
        if (moveNames.length == 0)
            throw new IllegalArgumentException("Empty combo: " + string);
        List<ComboMove> moves = new ArrayList<>();
        for (String moveName : moveNames) {
            Matcher matcher =
                    Pattern.compile(";?([_^])([a-z]+)((\\d+)-(\\d+))?").matcher(moveName);
            if (!matcher.matches())
                throw new IllegalArgumentException("Invalid move: " + moveName);
            boolean eventMustBeEaten = !moveName.startsWith(";");
            ComboMoveDuration moveDuration;
            if (matcher.group(3) == null)
                moveDuration = defaultMoveDuration;
            else
                moveDuration = new ComboMoveDuration(
                        Duration.ofMillis(Integer.parseUnsignedInt(matcher.group(4))),
                        Duration.ofMillis(Integer.parseUnsignedInt(matcher.group(5))));
            KeyState state =
                    matcher.group(1).equals("_") ? KeyState.PRESSED : KeyState.RELEASED;
            String keyName = matcher.group(2);
            Key key = Key.keyByName.get(keyName);
            if (key == null)
                throw new IllegalArgumentException("Invalid key: " + keyName);
            moves.add(new ComboMove(new KeyAction(key, state), moveDuration,
                    eventMustBeEaten));
        }
        return new Combo(List.copyOf(moves));
    }

    public static List<Combo> multiCombo(String multiComboString,
                                         ComboMoveDuration defaultMoveDuration) {
        return Arrays.stream(multiComboString.split("\\s*\\|\\s*"))
                     .map(string -> of(string, defaultMoveDuration))
                     .toList();
    }
}
