package mousemaster;

import java.time.Duration;
import java.util.*;
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
     * - 'text' = send string to OS
     */
    public static Macro of(String name, String string,
                           Map<String, KeyAlias> keyAliases, KeyResolver keyResolver) {
        List<MacroParallel> parallels = new ArrayList<>();
        Duration parallelDuration;
        List<MacroMove> moves = new ArrayList<>();
        for (String token : tokenize(string)) {
            if (token.startsWith("'") && token.endsWith("'")) {
                String content = token.substring(1, token.length() - 1);
                moves.add(new StringMacroMove(parseUnicodeEscapes(content)));
                continue;
            }
            Matcher waitMatcher = Pattern.compile("wait-(\\d+)").matcher(token);
            if (waitMatcher.matches()) {
                parallelDuration =
                        Duration.ofMillis(Integer.parseUnsignedInt(waitMatcher.group(1)));
                parallels.add(new MacroParallel(moves, parallelDuration));
                moves = new ArrayList<>();
                continue;
            }
            if (!token.matches("[+\\-#~].*")) {
                moves.add(parseKeyMacroMove(keyAliases, token, keyResolver, true, MacroMoveDestination.COMBO_WATCHER));
                moves.add(parseKeyMacroMove(keyAliases, token, keyResolver, false, MacroMoveDestination.COMBO_WATCHER));
            }
            else {
                moves.add(parseKeyMacroMove(name, keyAliases, keyResolver, token));
            }
        }
        if (!moves.isEmpty())
            parallels.add(new MacroParallel(moves, Duration.ZERO));
        return new Macro(name, new MacroSequence(parallels));
    }

    private static List<String> tokenize(String string) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < string.length()) {
            char c = string.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '\'') {
                int end = string.indexOf('\'', i + 1);
                if (end == -1)
                    throw new IllegalArgumentException(
                            "Unclosed single quote in macro: " + string);
                tokens.add(string.substring(i, end + 1));
                i = end + 1;
            }
            else {
                int start = i;
                while (i < string.length() && !Character.isWhitespace(string.charAt(i)))
                    i++;
                tokens.add(string.substring(start, i));
            }
        }
        return tokens;
    }

    private static String parseUnicodeEscapes(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == '\\' && i + 5 < s.length() && s.charAt(i + 1) == 'u') {
                String hex = s.substring(i + 2, i + 6);
                sb.append((char) Integer.parseInt(hex, 16));
                i += 6;
            } else {
                sb.append(s.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    private static KeyMacroMove parseKeyMacroMove(String name, Map<String, KeyAlias> keyAliases,
                                            KeyResolver keyResolver, String word) {
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
        return parseKeyMacroMove(keyAliases, keyOrAliasName, keyResolver, press,
                destination);
    }

    private static KeyMacroMove parseKeyMacroMove(
            Map<String, KeyAlias> keyAliases,
            String keyOrAliasName, KeyResolver keyResolver, boolean press,
            MacroMoveDestination destination) {
        KeyAlias alias = keyAliases.get(keyOrAliasName);
        KeyOrAlias keyOrAlias;
        if (alias != null)
            keyOrAlias = KeyOrAlias.ofAlias(alias);
        else
            keyOrAlias = KeyOrAlias.ofKey(keyResolver.resolve(keyOrAliasName));
        return new KeyMacroMove(keyOrAlias, press, destination);
    }

    public Set<String> outputAliasNames() {
        Set<String> names = new HashSet<>();
        for (MacroParallel parallel : output.parallels()) {
            for (MacroMove move : parallel.moves()) {
                if (move instanceof KeyMacroMove(KeyOrAlias keyOrAlias, var __, var ___)) {
                    if (keyOrAlias.isAlias())
                        names.add(keyOrAlias.aliasName());
                }
            }
        }
        return names;
    }

    public ResolvedMacro resolve(AliasResolution aliasResolution) {
        List<ResolvedMacroParallel> resolvedParallels = new ArrayList<>();
        for (MacroParallel parallel : output.parallels()) {
            List<ResolvedMacroMove> resolvedMoves = new ArrayList<>();
            for (MacroMove move : parallel.moves()) {
                switch (move) {
                    case KeyMacroMove(KeyOrAlias keyOrAlias, boolean press,
                                      MacroMoveDestination destination) -> {
                        Key key = keyOrAlias.isAlias()
                                ? aliasResolution.keyByAliasName()
                                                 .get(keyOrAlias.aliasName())
                                : keyOrAlias.key();
                        resolvedMoves.add(
                                new ResolvedKeyMacroMove(key, press, destination));
                    }
                    case StringMacroMove stringMove -> resolvedMoves.add(stringMove);
                }
            }
            resolvedParallels.add(
                    new ResolvedMacroParallel(resolvedMoves, parallel.duration()));
        }
        return new ResolvedMacro(name, new ResolvedMacroSequence(resolvedParallels));
    }

    public static Set<String> aliasNamesUsedInOutput(String string,
                                                     Set<String> aliasNames) {
        Set<String> aliasNamesUsedInOutput = new HashSet<>();
        for (String token : tokenize(string)) {
            if (token.startsWith("'") && token.endsWith("'"))
                continue;
            Matcher moveMatcher = Pattern.compile("([+\\-#~])(.+)").matcher(token);
            if (moveMatcher.matches()) {
                String keyOrAliasName = moveMatcher.group(2);
                if (aliasNames.contains(keyOrAliasName))
                    aliasNamesUsedInOutput.add(keyOrAliasName);
            }
            else {
                // No prefix: token itself is the key or alias name.
                if (aliasNames.contains(token))
                    aliasNamesUsedInOutput.add(token);
            }
        }
        return aliasNamesUsedInOutput;
    }

}
