package mousemaster;

import mousemaster.ComboPrecondition.ComboAppPrecondition;
import mousemaster.ComboPrecondition.ComboKeyPrecondition;
import mousemaster.ComboPrecondition.PressedKeyGroup;
import mousemaster.ComboPrecondition.PressedKeyPrecondition;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record Combo(String label, ComboPrecondition precondition, ComboSequence sequence) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Combo combo)) return false;
        return precondition.equals(combo.precondition) && sequence.equals(combo.sequence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(precondition, sequence);
    }

    public static List<Combo> of(String label, String string,
                                 ComboMoveDuration defaultMoveDuration,
                                 Map<String, KeyAlias> keyAliases,
                                 Map<String, AppAlias> appAliases,
                                 KeyResolver keyResolver) {
        // Match ^{...} and _{...} preconditions in any order.
        Matcher preconditionMatcher = Pattern.compile("\\G([\\^_])\\{([^{}]+)\\}\\s*").matcher(string);
        String mustNotBeActiveAppsString = null;
        Set<App> mustNotBeActiveApps = Set.of();
        String unpressedKeySetString = null;
        Set<Key> unpressedKeySet = Set.of();
        String mustBeActiveAppsString = null;
        Set<App> mustBeActiveApps = Set.of();
        String pressedKeyPreconditionString = null;
        PressedKeyPrecondition pressedKeyPrecondition = new PressedKeyPrecondition(List.of());
        String sequenceString = string;
        while (preconditionMatcher.find()) {
            String prefix = preconditionMatcher.group(1);
            String content = preconditionMatcher.group(2);
            if (prefix.equals("^")) {
                if (isAppSetString(content, appAliases)) {
                    // ^{firefox.exe chrome.exe}
                    mustNotBeActiveAppsString = content;
                    mustNotBeActiveApps =
                            parseMustNotBeActiveApps(mustNotBeActiveAppsString, appAliases);
                }
                else {
                    unpressedKeySetString = content;
                    unpressedKeySet =
                            parseUnpressedKeySet(unpressedKeySetString,
                                    keyAliases, keyResolver);
                }
            }
            else {
                if (isAppSetString(content, appAliases)) {
                    // _{firefox.exe | chrome.exe}
                    mustBeActiveAppsString = content;
                    mustBeActiveApps = parseMustBeActiveApps(mustBeActiveAppsString, appAliases);
                }
                else {
                    pressedKeyPreconditionString = content;
                    pressedKeyPrecondition =
                            parsePressedKeyPrecondition(pressedKeyPreconditionString,
                                    keyAliases, keyResolver);
                }
            }
            sequenceString = string.substring(preconditionMatcher.end());
        }
        if (mustNotBeActiveApps.stream().anyMatch(mustBeActiveApps::contains)) {
            throw new IllegalArgumentException(
                    "There cannot be an overlap between must not be active apps and must be active apps: " +
                    "^{" + mustNotBeActiveAppsString + "} _{" +
                    mustBeActiveAppsString + "}");
        }
        if (unpressedKeySet.stream()
                           .anyMatch(pressedKeyPrecondition.allKeys()::contains)) {
            throw new IllegalArgumentException(
                    "There cannot be an overlap between unpressed and pressed precondition keys: " +
                    "^{" + unpressedKeySetString + "} _{" +
                    pressedKeyPreconditionString + "}");
        }
        ComboSequence sequence;
        if (sequenceString.isEmpty()) {
            sequence = new ComboSequence(List.of());
        }
        else {
            ExpandableSequence expandableSequence =
                    ExpandableSequence.parseSequence(sequenceString, defaultMoveDuration,
                            keyAliases);
            sequence = expandableSequence.toComboSequence(keyAliases, keyResolver);
        }
        return List.of(buildCombo(label, string, sequence,
                unpressedKeySet, unpressedKeySetString, sequenceString,
                pressedKeyPrecondition, pressedKeyPreconditionString,
                mustNotBeActiveApps, mustBeActiveApps));
    }

    private static Set<App> parseMustNotBeActiveApps(String appSetString,
                                                     Map<String, AppAlias> appAliases) {
        String[] appStrings = appSetString.split("\\s+");
        return Arrays.stream(appStrings)
                     .map(keyString -> expandAppAlias(keyString, appAliases))
                     .flatMap(Collection::stream)
                     .collect(Collectors.toSet());
    }

    private static Set<App> parseMustBeActiveApps(String appSetString,
                                                  Map<String, AppAlias> appAliases) {
        String[] appStrings = appSetString.split("\\s*\\|\\s*");
        return Arrays.stream(appStrings)
                     .map(keyString -> expandAppAlias(keyString, appAliases))
                     .flatMap(Collection::stream)
                     .collect(Collectors.toSet());
    }

    private static Combo buildCombo(String label, String string,
                                    ComboSequence sequence,
                                    Set<Key> unpressedKeySet,
                                    String unpressedKeySetString,
                                    String sequenceString,
                                    PressedKeyPrecondition pressedKeyPrecondition,
                                    String pressedKeyPreconditionString,
                                    Set<App> mustNotBeActiveApps,
                                    Set<App> mustBeActiveApps) {
        ComboPrecondition precondition = new ComboPrecondition(
                new ComboKeyPrecondition(unpressedKeySet,
                        pressedKeyPrecondition),
                new ComboAppPrecondition(mustNotBeActiveApps, mustBeActiveApps));
        if (precondition.isEmpty() && sequence.isEmpty())
            throw new IllegalArgumentException("Empty combo: " + string);
        return new Combo(label, precondition, sequence);
    }

    private static Set<Key> parseUnpressedKeySet(String keySetString,
                                                 Map<String, KeyAlias> aliases,
                                                 KeyResolver keyResolver) {
        String[] keyStrings = keySetString.split("\\s+");
        return Arrays.stream(keyStrings)
                     .map(keyString -> expandKeyAlias(keyString, aliases, keyResolver))
                     .flatMap(Collection::stream)
                     .collect(Collectors.toSet());
    }

    private static Set<Key> expandKeyAlias(String keyString, Map<String, KeyAlias> aliases,
                                           KeyResolver keyResolver) {
        KeyAlias alias = aliases.get(keyString);
        if (alias == null)
            return Set.of(keyResolver.resolve(keyString));
        return Set.copyOf(alias.keys());
    }

    private static Set<App> expandAppAlias(String appString, Map<String, AppAlias> aliases) {
        AppAlias alias = aliases.get(appString);
        if (alias == null)
            return Set.of(new App(appString));
        return alias.apps();
    }

    private static boolean isAppSetString(String string, Map<String, AppAlias> appAliases) {
        if (string.toLowerCase(Locale.ENGLISH).contains(".exe"))
            // firefox.exe chrome.exe
            return true;
        List<String> appStrings = List.of(string.split("\\s+|(\\s*\\|\\s*)"));
        return appStrings.stream().allMatch(appAliases::containsKey);
    }

    private static PressedKeyPrecondition parsePressedKeyPrecondition(
            String keySetsString,
            Map<String, KeyAlias> aliases,
            KeyResolver keyResolver) {
        String[] groupStrings = keySetsString.split("\\s*\\|\\s*");
        List<PressedKeyGroup> groups = Arrays.stream(groupStrings)
                                      .map(groupString -> parsePressedKeyGroup(
                                              groupString, aliases, keyResolver))
                                      .collect(Collectors.toList());
        return new PressedKeyPrecondition(groups);
    }

    private static PressedKeyGroup parsePressedKeyGroup(String keySetString,
                                                        Map<String, KeyAlias> aliases,
                                                        KeyResolver keyResolver) {
        boolean containsNone = false;
        List<Set<Key>> keySets = new ArrayList<>();
        String[] split = keySetString.split("\\s+");
        for (String complexKeyString : split) {
            if (complexKeyString.equals("none"))
                containsNone = true;
            else if (!complexKeyString.contains("*")) {
                Set<Key> expandedKeys = expandKeyAlias(complexKeyString, aliases,
                        keyResolver);
                keySets.add(expandedKeys);
            }
            else {
                Set<Key> keys = new HashSet<>();
                for (String keyName : complexKeyString.split("\\*")) {
                    Set<Key> expandedKeys = expandKeyAlias(keyName, aliases, keyResolver);
                    keys.addAll(expandedKeys);
                }
                keySets.add(keys);
            }
        }
        if (containsNone && !keySets.isEmpty())
            // "none rightctrl" is invalid
            // "none up*down" is invalid
            throw new IllegalArgumentException("Invalid key set: " + keySetString);
        return new PressedKeyGroup(keySets);
    }

    public static List<Combo> multiCombo(String label, String multiComboString,
                                         ComboMoveDuration defaultMoveDuration,
                                         Map<String, KeyAlias> keyAliases,
                                         Map<String, AppAlias> appAliases,
                                         KeyResolver keyResolver) {
        // One combo is: ^{key|...} _{key|...} move1 move2 ...
        // Two combos: ^{key|...} _{key|...} move1 move2 ... | ^{key|...} _{key|...} move ...
        // Combo with mustBeActiveApps: _{firefox.exe|chrome.exe} ^{key|...} _{key|...} move1 move2 ...
        // Combo with mustNotBeActiveApps: ^{firefox.exe chrome.exe} ^{key|...} _{key|...} move1 move2 ...
        int comboBeginIndex = 0;
        int braceDepth = 0;
        List<Combo> combos = new ArrayList<>();
        for (int charIndex = 0; charIndex < multiComboString.length(); charIndex++) {
            char character = multiComboString.charAt(charIndex);
            if (character == '{') {
                braceDepth++;
            }
            else if (character == '}') {
                if (braceDepth == 0)
                    throw new IllegalArgumentException(
                            "Invalid multi-combo: " + multiComboString);
                braceDepth--;
            }
            else if (character == '|' && braceDepth == 0) {
                combos.addAll(
                        of(label, multiComboString.substring(comboBeginIndex, charIndex).strip(),
                                defaultMoveDuration, keyAliases, appAliases, keyResolver));
                comboBeginIndex = charIndex + 1;
            }
        }
        combos.addAll(of(label, multiComboString.substring(comboBeginIndex).strip(),
                defaultMoveDuration, keyAliases, appAliases, keyResolver));
        return List.copyOf(combos);
    }

    @Override
    public String toString() {
        String preconditionString = precondition.isEmpty() ? "" : precondition.toString();
        String sequenceString = sequence.toString();
        if (!preconditionString.isEmpty() && !sequenceString.isEmpty())
            return label + ": " + preconditionString + " " + sequenceString;
        return label + ": " + preconditionString + sequenceString;
    }

    public Set<Key> keysPressedAfterMoves(Set<Key> preconditionPressedKeySet,
                                           List<ResolvedKeyComboMove> matchedKeyMoves) {
        Set<Key> pressedKeys = new HashSet<>(preconditionPressedKeySet);
        for (ResolvedKeyComboMove move : matchedKeyMoves) {
            if (move.isPress())
                pressedKeys.add(move.key());
            else
                pressedKeys.remove(move.key());
        }
        return pressedKeys;
    }
}
