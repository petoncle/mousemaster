package mousemaster;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Remapping(String name, RemappingSequence output) {

    public static Remapping of(String name, String string) {
        List<RemappingParallel> parallels = new ArrayList<>();
        Duration parallelDuration;
        List<RemappingMove> moves = new ArrayList<>();
        for (String word : string.split("\\s+")) {
            Matcher waitMatcher = Pattern.compile("wait-(\\d+)").matcher(word);
            if (waitMatcher.matches()) {
                parallelDuration =
                        Duration.ofMillis(Integer.parseUnsignedInt(waitMatcher.group(1)));
                parallels.add(new RemappingParallel(moves, parallelDuration));
                moves = new ArrayList<>();
                continue;
            }
            Matcher moveMatcher = Pattern.compile("([+\\-])([^-]+?)").matcher(word);
            if (!moveMatcher.matches())
                throw new IllegalArgumentException(
                        "Invalid remapping move for " + name + ": " + word);
            Key key = Key.ofName(moveMatcher.group(2));
            boolean press = moveMatcher.group(1).equals("+");
            moves.add(new RemappingMove(key, press));
        }
        if (!moves.isEmpty())
            parallels.add(new RemappingParallel(moves, Duration.ZERO));
        return new Remapping(name, new RemappingSequence(parallels));
    }

}
