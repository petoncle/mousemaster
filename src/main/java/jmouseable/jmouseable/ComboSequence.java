package jmouseable.jmouseable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record ComboSequence(List<ComboMove> moves) {
    static ComboSequence parseSequence(String movesString,
                                       ComboMoveDuration defaultMoveDuration) {
        String[] moveStrings = movesString.split("\\s+");
        List<ComboMove> moves = new ArrayList<>();
        for (String moveString : moveStrings) {
            // +leftctrl
            // +leftctrl-0-250
            // +leftctrl-1000
            Matcher matcher = Pattern.compile("([+\\-#])([^-]+?)(-(\\d+)(-(\\d+))?)?")
                                     .matcher(moveString);
            if (!matcher.matches())
                throw new IllegalArgumentException("Invalid move: " + moveString);
            boolean press = !moveString.startsWith("-");
            ComboMoveDuration moveDuration;
            if (matcher.group(3) == null)
                moveDuration = defaultMoveDuration;
            else
                moveDuration = new ComboMoveDuration(
                        Duration.ofMillis(Integer.parseUnsignedInt(matcher.group(4))),
                        matcher.group(6) == null ? null : Duration.ofMillis(
                                Integer.parseUnsignedInt(matcher.group(6))));
            String keyName = matcher.group(2);
            Key key = Key.ofName(keyName);
            ComboMove move;
            if (press) {
                boolean eventMustBeEaten = moveString.startsWith("+");
                move = new ComboMove.PressComboMove(key, eventMustBeEaten, moveDuration);
            }
            else
                move = new ComboMove.ReleaseComboMove(key, moveDuration);
            moves.add(move);
        }
        return new ComboSequence(List.copyOf(moves));
    }

    @Override
    public String toString() {
        return moves.stream().map(Object::toString).collect(Collectors.joining(" "));
    }

}
