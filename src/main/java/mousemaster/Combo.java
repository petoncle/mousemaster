package mousemaster;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record Combo(ComboPrecondition precondition, ComboSequence sequence) {

    public static Combo of(String string, ComboMoveDuration defaultMoveDuration,
                           Map<String, Alias> aliases) {
        Matcher mustRemainUnpressedKeySetMatcher =
                Pattern.compile("\\^\\{([^{}]+)\\}\\s*").matcher(string);
        Set<Key> mustRemainUnpressedKeySet;
        String mustRemainPressedAndSequenceString;
        String mustRemainUnpressedKeySetString;
        if (mustRemainUnpressedKeySetMatcher.find()) {
            mustRemainUnpressedKeySetString = mustRemainUnpressedKeySetMatcher.group(1);
            mustRemainUnpressedKeySet = parseMustRemainUnpressedKeySet(mustRemainUnpressedKeySetString, aliases);
            mustRemainPressedAndSequenceString =
                    string.substring(mustRemainUnpressedKeySetMatcher.end());
        }
        else {
            mustRemainUnpressedKeySetString = null;
            mustRemainUnpressedKeySet = Set.of();
            mustRemainPressedAndSequenceString = string;
        }
        Matcher mustRemainPressedKeySetsMatcher = Pattern.compile("_\\{([^{}]+)\\}\\s*")
                                                         .matcher(
                                                                 mustRemainPressedAndSequenceString);
        Set<Set<Key>> mustRemainPressedKeySets;
        String sequenceString;
        String mustRemainPressedKeySetsString;
        if (mustRemainPressedKeySetsMatcher.find()) {
            mustRemainPressedKeySetsString = mustRemainPressedKeySetsMatcher.group(1);
            mustRemainPressedKeySets =
                    parseMustRemainPressedKeySets(mustRemainPressedKeySetsString,
                            aliases);
            sequenceString = mustRemainPressedAndSequenceString.substring(
                    mustRemainPressedKeySetsMatcher.end());
        }
        else {
            mustRemainPressedKeySetsString = null;
            mustRemainPressedKeySets = Set.of();
            sequenceString = mustRemainPressedAndSequenceString;
        }
        if (mustRemainUnpressedKeySet.stream()
                                     .anyMatch(mustRemainPressedKeySets.stream()
                                                                       .flatMap(
                                                                               Collection::stream)
                                                                       .collect(
                                                                               Collectors.toSet())::contains)) {
            throw new IllegalArgumentException(
                    "There cannot be an overlap between must remain unpressed keys and must remain pressed keys: " +
                    "^{" + mustRemainUnpressedKeySetString + "} _{" +
                    mustRemainPressedKeySetsString + "}");
        }
        ComboSequence sequence = sequenceString.isEmpty() ? new ComboSequence(List.of()) :
                ComboSequence.parseSequence(sequenceString, defaultMoveDuration);
        Set<Key> sequenceKeys =
                sequence.moves().stream().map(ComboMove::key).collect(Collectors.toSet());
        if (mustRemainUnpressedKeySet.stream().anyMatch(sequenceKeys::contains))
            throw new IllegalArgumentException(
                    "There cannot be an overlap between must remain unpressed keys and combo sequence keys: " +
                    "^{" + mustRemainUnpressedKeySetString + "} " + sequenceString);
        if (mustRemainPressedKeySets.stream()
                                    .flatMap(Collection::stream)
                                    .anyMatch(sequenceKeys::contains))
            throw new IllegalArgumentException(
                    "There cannot be an overlap between must remain pressed keys and combo sequence keys: " +
                    "_{" + mustRemainPressedKeySetsString + "} " + sequenceString);
        if (mustRemainPressedKeySets.equals(Set.of(Set.of())))
            mustRemainPressedKeySets = Set.of();
        ComboPrecondition precondition = new ComboPrecondition(mustRemainUnpressedKeySet,
                mustRemainPressedKeySets);
        if (precondition.isEmpty() && sequence.moves().isEmpty())
            throw new IllegalArgumentException("Empty combo: " + string);
        return new Combo(precondition, sequence);
    }

    private static Set<Key> parseMustRemainUnpressedKeySet(String keySetString,
                                                           Map<String, Alias> aliases) {
        String[] keyStrings = keySetString.split("\\s+");
        return Arrays.stream(keyStrings)
                     .map(keyString -> expandAlias(keyString, aliases))
                     .flatMap(Collection::stream)
                     .collect(Collectors.toSet());
    }

    private static Set<Key> expandAlias(String keyString, Map<String, Alias> aliases) {
        Alias alias = aliases.get(keyString);
        if (alias == null)
            return Set.of(Key.ofName(keyString));
        return alias.keys();
    }

    private static Set<Set<Key>> parseMustRemainPressedKeySets(String keySetsString,
                                                               Map<String, Alias> aliases) {
        String[] keySetStrings = keySetsString.split("\\s*\\|\\s*");
        return Arrays.stream(keySetStrings)
                     .map(complexKeySetString -> parseMustRemainPressedKeySet(
                             complexKeySetString, aliases))
                     .flatMap(Collection::stream)
                     .collect(Collectors.toSet());
    }

    private static Set<Set<Key>> parseMustRemainPressedKeySet(String keySetString,
                                                              Map<String, Alias> aliases) {
        // rightctrl up*down -> (rightctrl, up), (rightctrl, down), (rightctrl, up, down)
        boolean containsAnyCombinationOf = false;
        boolean containsEmptyKeySet = false;
        boolean containsMultiKeyAlias = false;
        Set<Key> mustBeInSetKeys = new HashSet<>();
        Set<Set<Key>> combinationKeySets = new HashSet<>();
        String[] split = keySetString.split("\\s+");
        for (String complexKeyString : split) {
            if (complexKeyString.equals("none"))
                containsEmptyKeySet = true;
            else if (!complexKeyString.contains("*")) {
                Set<Key> expandedKeys = expandAlias(complexKeyString, aliases);
                if (expandedKeys.size() > 1) {
                    if (containsMultiKeyAlias)
                        throw new IllegalArgumentException(
                                "There cannot be more than one multi-key alias: " +
                                complexKeyString);
                    containsMultiKeyAlias = true;
                    containsAnyCombinationOf = true;
                    combinationKeySets = generateCombinations(expandedKeys);
                }
                else
                    mustBeInSetKeys.addAll(expandedKeys); // Only one key.
            }
            else {
                if (containsAnyCombinationOf)
                    throw new IllegalArgumentException(
                            "There cannot be more than one any-combination-of: " +
                            complexKeyString);
                containsAnyCombinationOf = true;
                Set<Key> keys = new HashSet<>();
                for (String keyName : complexKeyString.split("\\*")) {
                    Set<Key> expandedKeys = expandAlias(keyName, aliases);
                    if (expandedKeys.size() > 1)
                        throw new IllegalArgumentException(
                                "Multi-key aliases cannot be used in a any-combination-of: " +
                                complexKeyString);
                    keys.addAll(expandedKeys); // Only one key.
                }
                combinationKeySets = generateCombinations(keys);
            }
        }
        if (containsEmptyKeySet && (!mustBeInSetKeys.isEmpty() || containsAnyCombinationOf))
            // "none rightctrl" is invalid
            // "none up*down" is invalid
            throw new IllegalArgumentException("Invalid key set: " + keySetString);
        if (!containsAnyCombinationOf) {
            return Set.of(mustBeInSetKeys);
        }
        else {
            for (Set<Key> combinationKeySet : combinationKeySets)
                combinationKeySet.addAll(mustBeInSetKeys);
        }
        return combinationKeySets;
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

    public static List<Combo> multiCombo(String multiComboString,
                                         ComboMoveDuration defaultMoveDuration,
                                         Map<String, Alias> aliases) {
        // One combo is: ^{key|...} _{key|...} move1 move2 ...
        // Two combos: ^{key|...} _{key|...} move1 move2 ... | ^{key|...} _{key|...} move ...
        int comboBeginIndex = 0;
        boolean rightBraceExpected = false;
        List<Combo> combos = new ArrayList<>();
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
                combos.add(
                        of(multiComboString.substring(comboBeginIndex, charIndex).strip(),
                                defaultMoveDuration, aliases));
                comboBeginIndex = charIndex + 1;
            }
        }
        combos.add(of(multiComboString.substring(comboBeginIndex).strip(),
                defaultMoveDuration, aliases));
        return List.copyOf(combos);
    }

    @Override
    public String toString() {
        return (precondition.isEmpty() ? "" : precondition + " ") + sequence;
    }

}
