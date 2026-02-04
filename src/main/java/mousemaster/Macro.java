package mousemaster;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Macro(String name, MacroSequence output) {

    /**
     * - +key = press key, send to OS
     * - -key = release key, send to OS
     * - #key = press key, send to ComboWatcher
     * - ~key = release key, send to ComboWatcher
     * - key (no prefix) = shorthand for #key ~key (press+release to ComboWatcher)
     * - wait-N = wait N milliseconds
     */
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
            if (!word.matches("[+\\-#~].*")) {
                moves.add(parseMacroMove(aliasResolution, word, true, MacroMoveDestination.COMBO_WATCHER));
                moves.add(parseMacroMove(aliasResolution, word, false, MacroMoveDestination.COMBO_WATCHER));
            }
            else {
                moves.add(parseMacroMove(name, aliasResolution, word));
            }
        }
        if (!moves.isEmpty())
            parallels.add(new MacroParallel(moves, Duration.ZERO));
        return new Macro(name, new MacroSequence(parallels));
    }

    private static MacroMove parseMacroMove(String name, AliasResolution aliasResolution,
                                            String word) {
        Matcher moveMatcher = Pattern.compile("([+\\-#~])(.+)").matcher(word);
        if (!moveMatcher.matches())
            throw new IllegalArgumentException(
                    "Invalid macro move for " + name + ": " + word);
        String keyOrAliasName = moveMatcher.group(2);
        String prefix = moveMatcher.group(1);
        boolean press = prefix.equals("+") || prefix.equals("#");
        MacroMoveDestination destination = (prefix.equals("+") || prefix.equals("-"))
                ? MacroMoveDestination.OS
                : MacroMoveDestination.COMBO_WATCHER;
        return parseMacroMove(aliasResolution, keyOrAliasName, press, destination);
    }

    private static MacroMove parseMacroMove(
            AliasResolution aliasResolution,
            String keyOrAliasName, boolean press, MacroMoveDestination destination) {
        Key aliasKey = aliasResolution.keyByAliasName().get(keyOrAliasName);
        Key key = aliasKey == null ? Key.ofName(keyOrAliasName) : aliasKey;
        return new MacroMove(key, press, destination);
    }

    public static Set<String> aliasNamesUsedInOutput(String string,
                                                     Set<String> aliasNames) {
        Set<String> aliasNamesUsedInOutput = new HashSet<>();
        for (String word : string.split("\\s+")) {
            Matcher moveMatcher = Pattern.compile("([+\\-#~])(.+)").matcher(word);
            if (moveMatcher.matches()) {
                String keyOrAliasName = moveMatcher.group(2);
                if (aliasNames.contains(keyOrAliasName))
                    aliasNamesUsedInOutput.add(keyOrAliasName);
            }
            else {
                // No prefix: word itself is the key or alias name.
                if (aliasNames.contains(word))
                    aliasNamesUsedInOutput.add(word);
            }
        }
        return aliasNamesUsedInOutput;
    }

}
