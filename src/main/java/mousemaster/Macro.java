package mousemaster;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Macro(String name, MacroSequence output) {

    public static Macro of(String name, String string,
                           AliasResolution aliasResolution) {
        List<MacroParallel> parallels = new ArrayList<>();
        Duration parallelDuration;
        List<MacroMove> moves = new ArrayList<>();
        for (String word : string.split("\\s+")) {
            Matcher waitMatcher = Pattern.compile("wait-(\\d+)").matcher(word);
            if (waitMatcher.matches()) {
                parallelDuration =
                        Duration.ofMillis(Integer.parseUnsignedInt(waitMatcher.group(1)));
                parallels.add(new MacroParallel(moves, parallelDuration));
                moves = new ArrayList<>();
                continue;
            }
            Matcher moveMatcher = Pattern.compile("([+\\-])([^-]+?)").matcher(word);
            if (!moveMatcher.matches())
                throw new IllegalArgumentException(
                        "Invalid macro move for " + name + ": " + word);
            String keyOrAliasName = moveMatcher.group(2);
            Key aliasKey = aliasResolution.keyByAliasName().get(keyOrAliasName);
            Key key = aliasKey == null ? Key.ofName(keyOrAliasName) : aliasKey;
            boolean press = moveMatcher.group(1).equals("+");
            moves.add(new MacroMove(key, press));
        }
        if (!moves.isEmpty())
            parallels.add(new MacroParallel(moves, Duration.ZERO));
        return new Macro(name, new MacroSequence(parallels));
    }

    public static Set<String> aliasNamesUsedInOutput(String string,
                                                     Set<String> aliasNames) {
        Set<String> aliasNamesUsedInOutput = new HashSet<>();
        for (String word : string.split("\\s+")) {
            Matcher moveMatcher = Pattern.compile("([+\\-])([^-]+?)").matcher(word);
            if (!moveMatcher.matches())
                continue;
            String keyOrAliasName = moveMatcher.group(2);
            if (aliasNames.contains(keyOrAliasName))
                aliasNamesUsedInOutput.add(keyOrAliasName);
        }
        return aliasNamesUsedInOutput;
    }

}
