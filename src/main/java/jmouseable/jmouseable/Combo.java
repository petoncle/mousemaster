package jmouseable.jmouseable;

import jmouseable.jmouseable.ComboMove.PressComboMove;
import jmouseable.jmouseable.ComboMove.ReleaseComboMove;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record Combo(List<ComboMove> moves) {

    public static Combo of(String string, ComboMoveDuration defaultMoveDuration) {
        String[] moveNames = string.split("\\s+");
        if (moveNames.length == 0)
            throw new IllegalArgumentException("Empty combo: " + string);
        List<ComboMove> moves = new ArrayList<>();
        for (String moveName : moveNames) {
            Matcher matcher = Pattern.compile("([_^\\-])([a-z]+)((\\d+)-(\\d+))?")
                                     .matcher(moveName);
            if (!matcher.matches())
                throw new IllegalArgumentException("Invalid move: " + moveName);
            boolean press = !moveName.startsWith("^");
            ComboMoveDuration moveDuration;
            if (matcher.group(3) == null)
                moveDuration = defaultMoveDuration;
            else
                moveDuration = new ComboMoveDuration(
                        Duration.ofMillis(Integer.parseUnsignedInt(matcher.group(4))),
                        Duration.ofMillis(Integer.parseUnsignedInt(matcher.group(5))));
            String keyName = matcher.group(2);
            Key key = Key.keyByName.get(keyName);
            if (key == null)
                throw new IllegalArgumentException("Invalid key: " + keyName);
            ComboMove move;
            if (press) {
                boolean eventMustBeEaten = moveName.startsWith("_");
                move = new PressComboMove(key, eventMustBeEaten, moveDuration);
            }
            else
                move = new ReleaseComboMove(key, moveDuration);
            moves.add(move);
        }
        return new Combo(List.copyOf(moves));
    }

    public static List<Combo> multiCombo(String multiComboString,
                                         ComboMoveDuration defaultMoveDuration) {
        return Arrays.stream(multiComboString.split("\\s*\\|\\s*"))
                     .map(string -> of(string, defaultMoveDuration))
                     .toList();
    }

    @Override
    public String toString() {
        return moves.stream().map(Object::toString).collect(Collectors.joining(" "));
    }
}
