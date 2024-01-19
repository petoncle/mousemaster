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
        String[] keySetStrings = keySetsString.split("\\s*\\|\\s*");
        return Arrays.stream(keySetStrings)
                     .map(keySetString ->
                             acceptEmptyKeySet && keySetString.equals("none") ?
                                     Set.<Key>of() : parseKeySet(keySetString))
                     .collect(Collectors.toSet());
    }

    private static Set<Key> parseKeySet(String keySetString) {
        String[] keyStrings = keySetString.split("\\s+");
        return Arrays.stream(keyStrings).map(Key::ofName).collect(Collectors.toSet());
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
