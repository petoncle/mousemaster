package mousemaster;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record Combo(ComboPrecondition precondition, ComboSequence sequence) {

    public static Combo of(String string, ComboMoveDuration defaultMoveDuration) {
        Matcher mustRemainUnpressedKeySetsMatcher =
                Pattern.compile("\\^\\{([^{}]+)\\}\\s*").matcher(string);
        Set<Set<Key>> mustRemainUnpressedKeySets;
        String mustRemainPressedAndSequenceString;
        String mustRemainUnpressedKeySetsString;
        if (mustRemainUnpressedKeySetsMatcher.find()) {
            mustRemainUnpressedKeySetsString = mustRemainUnpressedKeySetsMatcher.group(1);
            mustRemainUnpressedKeySets = parseKeySets(mustRemainUnpressedKeySetsString, false);
            mustRemainPressedAndSequenceString =
                    string.substring(mustRemainUnpressedKeySetsMatcher.end());
        }
        else {
            mustRemainUnpressedKeySetsString = null;
            mustRemainUnpressedKeySets = Set.of();
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
            mustRemainPressedKeySets = parseKeySets(mustRemainPressedKeySetsString, true);
            sequenceString = mustRemainPressedAndSequenceString.substring(
                    mustRemainPressedKeySetsMatcher.end());
        }
        else {
            mustRemainPressedKeySetsString = null;
            mustRemainPressedKeySets = Set.of();
            sequenceString = mustRemainPressedAndSequenceString;
        }
        if (mustRemainUnpressedKeySets.stream()
                                      .flatMap(Collection::stream)
                                      .anyMatch(mustRemainPressedKeySets.stream()
                                                                        .flatMap(
                                                                                Collection::stream)
                                                                        .collect(
                                                                                Collectors.toSet())::contains)) {
            throw new IllegalArgumentException(
                    "There cannot be an overlap between must remain unpressed keys and must remain pressed keys: " +
                    "^{" + mustRemainUnpressedKeySetsString + "} _{" +
                    mustRemainPressedKeySetsString + "}");
        }
        ComboSequence sequence = sequenceString.isEmpty() ? new ComboSequence(List.of()) :
                ComboSequence.parseSequence(sequenceString, defaultMoveDuration);
        Set<Key> sequenceKeys =
                sequence.moves().stream().map(ComboMove::key).collect(Collectors.toSet());
        if (mustRemainUnpressedKeySets.stream()
                                      .flatMap(Collection::stream)
                                      .anyMatch(sequenceKeys::contains))
            throw new IllegalArgumentException(
                    "There cannot be an overlap between must remain unpressed keys and combo sequence keys: " +
                    "^{" + mustRemainUnpressedKeySetsString + "} " + sequenceString);
        if (mustRemainPressedKeySets.stream()
                                    .flatMap(Collection::stream)
                                    .anyMatch(sequenceKeys::contains))
            throw new IllegalArgumentException(
                    "There cannot be an overlap between must remain pressed keys and combo sequence keys: " +
                    "_{" + mustRemainPressedKeySetsString + "} " + sequenceString);
        if (mustRemainPressedKeySets.equals(Set.of(Set.of())))
            mustRemainPressedKeySets = Set.of();
        ComboPrecondition precondition = new ComboPrecondition(mustRemainUnpressedKeySets,
                mustRemainPressedKeySets);
        if (precondition.isEmpty() && sequence.moves().isEmpty())
            throw new IllegalArgumentException("Empty combo: " + string);
        return new Combo(precondition, sequence);
    }

    private static Set<Set<Key>> parseKeySets(String keySetsString, boolean acceptEmptyKeySet) {
        String[] complexKeySetStrings = keySetsString.split("\\s*\\|\\s*");
        return Arrays.stream(complexKeySetStrings)
                     .map(complexKeySetString -> parseComplexKeySet(complexKeySetString,
                             acceptEmptyKeySet))
                     .flatMap(Collection::stream)
                     .collect(Collectors.toSet());
    }

    private static Set<Set<Key>> parseComplexKeySet(String complexKeySetString, boolean acceptEmptyKeySet) {
        // rightctrl up*down -> (rightctrl, up), (rightctrl, down), (rightctrl, up, down)
        boolean containsAnyCombinationOf = false;
        boolean containsEmptyKeySet = false;
        Set<Key> mustBeInSetKeys = new HashSet<>();
        Set<Set<Key>> combinationKeySets = new HashSet<>();
        String[] split = complexKeySetString.split("\\s+");
        for (String complexKeyString : split) {
            if (complexKeyString.equals("none"))
                containsEmptyKeySet = true;
            else if (!complexKeyString.contains("*")) {
                if (containsAnyCombinationOf)
                    throw new IllegalArgumentException(
                            "any-combination-of should be placed at the end: " +
                            complexKeyString);
                mustBeInSetKeys.add(Key.ofName(complexKeyString));
            }
            else {
                if (containsAnyCombinationOf)
                    throw new IllegalArgumentException(
                            "There cannot be more than one any-combination-of: " +
                            complexKeyString);
                containsAnyCombinationOf = true;
                Set<Key> keys = new HashSet<>();
                boolean includeEmptySet = false;
                for (String key : complexKeyString.split("\\*")) {
                    if (key.equals("none"))
                        includeEmptySet = true;
                    else
                        keys.add(Key.ofName(key));
                }
                combinationKeySets = generateCombinations(keys);
                if (includeEmptySet)
                    combinationKeySets.add(new HashSet<>());
            }
        }
        if (containsEmptyKeySet && (!mustBeInSetKeys.isEmpty() || containsAnyCombinationOf))
            // "none" should be alone.
            throw new IllegalArgumentException("Invalid key set: " + complexKeySetString);
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
                                         ComboMoveDuration defaultMoveDuration) {
        // One combo is: ^{key|...} _{key|...} move ...
        // Two combos: ^{key|...} _{key|...} move ... | ^{key|...} _{key|...} move ...
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
                                defaultMoveDuration));
                comboBeginIndex = charIndex + 1;
            }
        }
        combos.add(of(multiComboString.substring(comboBeginIndex).strip(),
                defaultMoveDuration));
        return List.copyOf(combos);
    }

    @Override
    public String toString() {
        return (precondition.isEmpty() ? "" : precondition + " ") + sequence;
    }

}
