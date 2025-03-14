package mousemaster;

import mousemaster.ComboPrecondition.ComboAppPrecondition;
import mousemaster.ComboPrecondition.ComboKeyPrecondition;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record Combo(ComboPrecondition precondition, ComboSequence sequence) {

    public static List<AliasResolvedCombo> of(String string,
                                              ComboMoveDuration defaultMoveDuration,
                                              Map<String, KeyAlias> keyAliases,
                                              Map<String, AppAlias> appAliases) {
        Matcher mustNotMatcher = Pattern.compile("\\^\\{([^{}]+)\\}\\s*").matcher(string);
        String mustNotBeActiveAppsString = null;
        Set<App> mustNotBeActiveApps = Set.of();
        String unpressedKeySetString = null;
        Set<Key> unpressedKeySet = Set.of();
        String mustString = string;
        while (mustNotMatcher.find()) {
            String mustNotSetString = mustNotMatcher.group(1);
            if (isAppSetString(mustNotSetString, appAliases)) {
                // ^{firefox.exe chrome.exe}
                mustNotBeActiveAppsString = mustNotSetString;
                mustNotBeActiveApps =
                        parseMustNotBeActiveApps(mustNotBeActiveAppsString, appAliases);
            }
            else {
                unpressedKeySetString = mustNotSetString;
                unpressedKeySet =
                        parseUnpressedKeySet(unpressedKeySetString,
                                keyAliases);
            }
            mustString = string.substring(mustNotMatcher.end());
        }
        Matcher mustMatcher = Pattern.compile("_\\{([^{}]+)\\}\\s*").matcher(mustString);
        String mustBeActiveAppsString = null;
        Set<App> mustBeActiveApps = Set.of();
        String pressedKeySetsString = null;
        Set<Set<Key>> pressedKeySets = Set.of();
        String sequenceString = mustString;
        while (mustMatcher.find()) {
            String mustSetsString = mustMatcher.group(1);
            if (isAppSetString(mustSetsString, appAliases)) {
                // _{firefox.exe | chrome.exe}
                mustBeActiveAppsString = mustSetsString;
                mustBeActiveApps = parseMustBeActiveApps(mustBeActiveAppsString, appAliases);
            }
            else {
                pressedKeySetsString = mustSetsString;
                pressedKeySets =
                        parsePressedKeySets(pressedKeySetsString,
                                keyAliases);
            }
            sequenceString = mustString.substring(mustMatcher.end());
        }
        if (mustNotBeActiveApps.stream().anyMatch(mustBeActiveApps::contains)) {
            throw new IllegalArgumentException(
                    "There cannot be an overlap between must not be active apps and must be active apps: " +
                    "^{" + mustNotBeActiveAppsString + "} _{" +
                    mustBeActiveAppsString + "}");
        }
        if (unpressedKeySet.stream()
                           .anyMatch(pressedKeySets.stream()
                                                                       .flatMap(
                                                                               Collection::stream)
                                                                       .collect(
                                                                               Collectors.toSet())::contains)) {
            throw new IllegalArgumentException(
                    "There cannot be an overlap between unpressed and pressed precondition keys: " +
                    "^{" + unpressedKeySetString + "} _{" +
                    pressedKeySetsString + "}");
        }
        if (sequenceString.isEmpty())
            return List.of(
                    new AliasResolvedCombo(new AliasResolution(Map.of()),
                            of(string, new ComboSequence(List.of()), unpressedKeySet,
                                    unpressedKeySetString, sequenceString,
                                    pressedKeySets, pressedKeySetsString,
                                    mustNotBeActiveApps, mustBeActiveApps)));
        else {
            ExpandableSequence expandableSequence =
                    ExpandableSequence.parseSequence(sequenceString, defaultMoveDuration,
                            keyAliases);
            Map<AliasResolution, ComboSequence> expandedSequences =
                    expandableSequence.expand(keyAliases);
            return ofExpandedSequences(string, expandedSequences,
                    unpressedKeySet,
                    unpressedKeySetString, sequenceString,
                    pressedKeySets, pressedKeySetsString,
                    mustNotBeActiveApps, mustBeActiveApps);
        }
    }

    private static List<AliasResolvedCombo> ofExpandedSequences(String string,
                                                   Map<AliasResolution, ComboSequence> expandedSequences,
                                                   Set<Key> unpressedKeySet,
                                                   String unpressedKeySetString,
                                                   String sequenceString,
                                                   Set<Set<Key>> pressedKeySets,
                                                   String pressedKeySetsString,
                                                   Set<App> mustNotBeActiveApps,
                                                   Set<App> mustBeActiveApps) {
        List<AliasResolvedCombo> aliasResolvedCombos = new ArrayList<>();
        for (Map.Entry<AliasResolution, ComboSequence> entry : expandedSequences.entrySet()) {
            AliasResolution aliasResolution = entry.getKey();
            ComboSequence sequence = entry.getValue();
            Combo combo = of(string, sequence,
                    unpressedKeySet,
                    unpressedKeySetString,
                    sequenceString,
                    pressedKeySets,
                    pressedKeySetsString,
                    mustNotBeActiveApps, mustBeActiveApps);
            aliasResolvedCombos.add(new AliasResolvedCombo(aliasResolution, combo));
        }
        return aliasResolvedCombos;
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

    private static Combo of(String string, ComboSequence sequence,
                            Set<Key> unpressedKeySet,
                            String unpressedKeySetString,
                            String sequenceString,
                            Set<Set<Key>> pressedKeySets,
                            String pressedKeySetsString,
                            Set<App> mustNotBeActiveApps,
                            Set<App> mustBeActiveApps) {
        Set<Key> sequenceKeys =
                sequence.moves().stream().map(ComboMove::key).collect(Collectors.toSet());
        ComboPrecondition precondition = new ComboPrecondition(
                new ComboKeyPrecondition(unpressedKeySet,
                        pressedKeySets),
                new ComboAppPrecondition(mustNotBeActiveApps, mustBeActiveApps));
        if (precondition.isEmpty() && sequence.moves().isEmpty())
            throw new IllegalArgumentException("Empty combo: " + string);
        return new Combo(precondition, sequence);
    }

    private static Set<Key> parseUnpressedKeySet(String keySetString,
                                                 Map<String, KeyAlias> aliases) {
        String[] keyStrings = keySetString.split("\\s+");
        return Arrays.stream(keyStrings)
                     .map(keyString -> expandKeyAlias(keyString, aliases))
                     .flatMap(Collection::stream)
                     .collect(Collectors.toSet());
    }

    private static Set<Key> expandKeyAlias(String keyString, Map<String, KeyAlias> aliases) {
        KeyAlias alias = aliases.get(keyString);
        if (alias == null)
            return Set.of(Key.ofName(keyString));
        return Set.copyOf(alias.keys());
    }

    private static Set<App> expandAppAlias(String appString, Map<String, AppAlias> aliases) {
        AppAlias alias = aliases.get(appString);
        if (alias == null)
            return Set.of(new App(appString));
        return alias.apps();
    }

    private static boolean isAppSetString(String string, Map<String, AppAlias> appAliases) {
        if (string.contains(".exe"))
            // firefox.exe chrome.exe
            return true;
        List<String> appStrings = List.of(string.split("\\s+|(\\s*\\|\\s*)"));
        return appStrings.stream().allMatch(appAliases::containsKey);
    }

    private static Set<Set<Key>> parsePressedKeySets(String keySetsString,
                                                     Map<String, KeyAlias> aliases) {
        String[] keySetStrings = keySetsString.split("\\s*\\|\\s*");
        Set<Set<Key>> pressedKeySets = Arrays.stream(keySetStrings)
                                      .map(complexKeySetString -> parsePressedKeySet(
                                              complexKeySetString, aliases))
                                      .flatMap(Collection::stream)
                                      .collect(Collectors.toSet());
        if (pressedKeySets.equals(Set.of(Set.of())))
            pressedKeySets = Set.of();
        return pressedKeySets;
    }

    private static Set<Set<Key>> parsePressedKeySet(String keySetString,
                                                    Map<String, KeyAlias> aliases) {
        // rightctrl up*down -> (rightctrl, up), (rightctrl, down), (rightctrl, up, down)
        boolean containsEmptyKeySet = false;
        List<Set<Set<Key>>> combinations = new ArrayList<>();
        String[] split = keySetString.split("\\s+");
        for (String complexKeyString : split) {
            if (complexKeyString.equals("none"))
                containsEmptyKeySet = true;
            else if (!complexKeyString.contains("*")) {
                Set<Key> expandedKeys = expandKeyAlias(complexKeyString, aliases);
                combinations.add(generateCombinations(expandedKeys));
            }
            else {
                Set<Key> keys = new HashSet<>();
                for (String keyName : complexKeyString.split("\\*")) {
                    Set<Key> expandedKeys = expandKeyAlias(keyName, aliases);
                    keys.addAll(expandedKeys);
                }
                combinations.add(generateCombinations(keys));
            }
        }
        if (containsEmptyKeySet && !combinations.isEmpty())
            // "none rightctrl" is invalid
            // "none up*down" is invalid
            throw new IllegalArgumentException("Invalid key set: " + keySetString);
        // leftctrl*leftshift up*down
        // _{alias1 alias2}: one or more keys of alias1 must be pressed, and same for alias2
        Set<Set<Key>> pressedKeySet = new HashSet<>();
        recursivelyExpandCombinations(new HashMap<>(), combinations,
                pressedKeySet);
        return pressedKeySet;
    }

    private static void recursivelyExpandCombinations(Map<Integer, Set<Key>> fixedCombinationBySetIndex,
                                           List<Set<Set<Key>>> combinations,
                                           Set<Set<Key>> result) {
        if (fixedCombinationBySetIndex.size() == combinations.size()) {
            Set<Key> mergedCombination = new HashSet<>();
            for (Set<Key> fixedCombination : fixedCombinationBySetIndex.values())
                mergedCombination.addAll(fixedCombination);
            result.add(mergedCombination);
            return;
        }
        int fixedCombinationSetIndex = fixedCombinationBySetIndex.size();
        Set<Set<Key>> fixedCombinationSet = combinations.get(fixedCombinationSetIndex);
        for (Set<Key> fixedCombination : fixedCombinationSet) {
            fixedCombinationBySetIndex.put(fixedCombinationSetIndex, fixedCombination);
            recursivelyExpandCombinations(fixedCombinationBySetIndex, combinations, result);
            fixedCombinationBySetIndex.remove(fixedCombinationSetIndex);
        }
    }

    /**
     * Input: Set.of(1, 2, 3)
     * Output: Set.of(Set.of(1), Set.of(2), Set.of(3), Set.of(1, 2), Set.of(1, 3), Set.of(2, 3), Set.of(1, 2, 3))
     */
    public static <T> Set<Set<T>> generateCombinations(Set<T> originalSet) {
        Set<Set<T>> combinations = new HashSet<>();
        if (originalSet.isEmpty()) {
            combinations.add(new HashSet<>());
            return combinations;
        }
        List<T> list = List.copyOf(originalSet);
        // Start from an empty set and build up combinations.
        recursivelyGenerateCombinations(combinations, list, new HashSet<>(), 0);
        // Remove the empty set from the combinations.
        combinations.remove(Set.of());
        return combinations;
    }

    private static <T> void recursivelyGenerateCombinations(Set<Set<T>> combinations,
                                                            List<T> originalList,
                                                            Set<T> current, int index) {
        if (index == originalList.size()) {
            combinations.add(new HashSet<>(current));
            return;
        }
        recursivelyGenerateCombinations(combinations, originalList, current, index + 1);
        current.add(originalList.get(index));
        recursivelyGenerateCombinations(combinations, originalList, current, index + 1);
        current.remove(originalList.get(index));
    }

    public static List<AliasResolvedCombo> multiCombo(String multiComboString,
                                                      ComboMoveDuration defaultMoveDuration,
                                                      Map<String, KeyAlias> keyAliases,
                                                      Map<String, AppAlias> appAliases) {
        // One combo is: ^{key|...} _{key|...} move1 move2 ...
        // Two combos: ^{key|...} _{key|...} move1 move2 ... | ^{key|...} _{key|...} move ...
        // Combo with mustBeActiveApps: _{firefox.exe|chrome.exe} ^{key|...} _{key|...} move1 move2 ...
        // Combo with mustNotBeActiveApps: ^{firefox.exe chrome.exe} ^{key|...} _{key|...} move1 move2 ...
        int comboBeginIndex = 0;
        boolean rightBraceExpected = false;
        List<AliasResolvedCombo> aliasResolvedCombos = new ArrayList<>();
        for (int charIndex = 0; charIndex < multiComboString.length(); charIndex++) {
            char character = multiComboString.charAt(charIndex);
            if (character == '{') {
                if (rightBraceExpected)
                    throw new IllegalArgumentException(
                            "Invalid multi-combo: " + multiComboString);
                rightBraceExpected = true;
            }
            else if (character == '}') {
                if (!rightBraceExpected)
                    throw new IllegalArgumentException(
                            "Invalid multi-combo: " + multiComboString);
                rightBraceExpected = false;
            }
            else if (character == '|' && !rightBraceExpected) {
                aliasResolvedCombos.addAll(
                        of(multiComboString.substring(comboBeginIndex, charIndex).strip(),
                                defaultMoveDuration, keyAliases, appAliases));
                comboBeginIndex = charIndex + 1;
            }
        }
        aliasResolvedCombos.addAll(of(multiComboString.substring(comboBeginIndex).strip(),
                defaultMoveDuration, keyAliases, appAliases));
        return List.copyOf(aliasResolvedCombos);
    }

    @Override
    public String toString() {
        return (precondition.isEmpty() ? "" : precondition + " ") + sequence;
    }

    public Set<Key> keysPressedInComboPriorToMoveOfIndex(
            Set<Key> preconditionPressedKeySet, int maxMoveIndex) {
        Set<Key> pressedKeys = new HashSet<>(preconditionPressedKeySet);
        for (int moveIndex = 0;
             moveIndex <= maxMoveIndex; moveIndex++) {
            ComboMove move = sequence().moves().get(moveIndex);
            if (move.isPress())
                pressedKeys.add(move.key());
            else
                pressedKeys.remove(move.key());
        }
        return pressedKeys;
    }
}
