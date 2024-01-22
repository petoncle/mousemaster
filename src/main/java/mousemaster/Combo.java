package mousemaster;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record Combo(ComboPrecondition precondition, ComboSequence sequence) {

    public static List<Combo> of(String string, ComboMoveDuration defaultMoveDuration,
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
        if (sequenceString.isEmpty())
            return List.of(
                    of(string, new ComboSequence(List.of()), mustRemainUnpressedKeySet,
                            mustRemainUnpressedKeySetString, sequenceString,
                            mustRemainPressedKeySets, mustRemainPressedKeySetsString));
        else {
            ExpandableSequence expandableSequence =
                    ExpandableSequence.parseSequence(sequenceString, defaultMoveDuration,
                            aliases);
            List<ComboSequence> expandedSequences = expandableSequence.expand(aliases);
            return expandedSequences.stream()
                                    .map(sequence -> of(string, sequence,
                                            mustRemainUnpressedKeySet,
                                            mustRemainUnpressedKeySetString,
                                            sequenceString, mustRemainPressedKeySets,
                                            mustRemainPressedKeySetsString))
                                    .toList();
        }
    }

    private static Combo of(String string, ComboSequence sequence,
                                      Set<Key> mustRemainUnpressedKeySet,
                                      String mustRemainUnpressedKeySetString,
                                      String sequenceString,
                                      Set<Set<Key>> mustRemainPressedKeySets,
                                      String mustRemainPressedKeySetsString) {
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
        Set<Set<Key>> mustRemainPressedKeySets = Arrays.stream(keySetStrings)
                                      .map(complexKeySetString -> parseMustRemainPressedKeySet(
                                              complexKeySetString, aliases))
                                      .flatMap(Collection::stream)
                                      .collect(Collectors.toSet());
        if (mustRemainPressedKeySets.equals(Set.of(Set.of())))
            mustRemainPressedKeySets = Set.of();
        return mustRemainPressedKeySets;
    }

    private static Set<Set<Key>> parseMustRemainPressedKeySet(String keySetString,
                                                              Map<String, Alias> aliases) {
        // rightctrl up*down -> (rightctrl, up), (rightctrl, down), (rightctrl, up, down)
        boolean containsEmptyKeySet = false;
        List<Set<Set<Key>>> combinations = new ArrayList<>();
        String[] split = keySetString.split("\\s+");
        for (String complexKeyString : split) {
            if (complexKeyString.equals("none"))
                containsEmptyKeySet = true;
            else if (!complexKeyString.contains("*")) {
                Set<Key> expandedKeys = expandAlias(complexKeyString, aliases);
                combinations.add(generateCombinations(expandedKeys));
            }
            else {
                Set<Key> keys = new HashSet<>();
                for (String keyName : complexKeyString.split("\\*")) {
                    Set<Key> expandedKeys = expandAlias(keyName, aliases);
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
        Set<Set<Key>> mustRemainPressedKeySet = new HashSet<>();
        recursivelyExpandCombinations(new HashMap<>(), combinations,
                mustRemainPressedKeySet);
        return mustRemainPressedKeySet;
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
                combos.addAll(
                        of(multiComboString.substring(comboBeginIndex, charIndex).strip(),
                                defaultMoveDuration, aliases));
                comboBeginIndex = charIndex + 1;
            }
        }
        combos.addAll(of(multiComboString.substring(comboBeginIndex).strip(),
                defaultMoveDuration, aliases));
        return List.copyOf(combos);
    }

    @Override
    public String toString() {
        return (precondition.isEmpty() ? "" : precondition + " ") + sequence;
    }

}
