package jmouseable.jmouseable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record Combo(ComboPrecondition precondition, ComboSequence sequence) {

    public static Combo of(String string, ComboMoveDuration defaultMoveDuration) {
        Matcher mustNotBePressedKeySetsMatcher =
                Pattern.compile("\\^\\{([^{}]+)\\}\\s*").matcher(string);
        Set<Set<Key>> mustNotBePressedKeySets;
        String mustBePressedAndSequenceString;
        if (mustNotBePressedKeySetsMatcher.find()) {
            String mustNotBePressedKeySetsString = mustNotBePressedKeySetsMatcher.group(1);
            mustNotBePressedKeySets =
                    parseKeySets(mustNotBePressedKeySetsString);
            mustBePressedAndSequenceString = string.substring(mustNotBePressedKeySetsMatcher.end());
        }
        else {
            mustNotBePressedKeySets = Set.of();
            mustBePressedAndSequenceString = string;
        }
        Matcher mustBePressedKeySetsMatcher =
                Pattern.compile("_\\{([^{}]+)\\}\\s*").matcher(mustBePressedAndSequenceString);
        Set<Set<Key>> mustBePressedKeySets;
        String sequenceString;
        if (mustBePressedKeySetsMatcher.find()) {
            String mustBePressedKeySetsString = mustBePressedKeySetsMatcher.group(1);
            mustBePressedKeySets =
                    parseKeySets(mustBePressedKeySetsString);
            sequenceString = mustBePressedAndSequenceString.substring(mustBePressedKeySetsMatcher.end());
        }
        else {
            mustBePressedKeySets = Set.of();
            sequenceString = mustBePressedAndSequenceString;
        }
        ComboSequence sequence = sequenceString.isEmpty() ? new ComboSequence(List.of()) :
                ComboSequence.parseSequence(sequenceString, defaultMoveDuration);
        ComboPrecondition precondition =
                new ComboPrecondition(mustNotBePressedKeySets, mustBePressedKeySets);
        if (precondition.isEmpty() && sequence.moves().isEmpty())
            throw new IllegalArgumentException("Empty combo: " + string);
        return new Combo(precondition, sequence);
    }

    private static Set<Set<Key>> parseKeySets(String keySetsString) {
        String[] keySetStrings = keySetsString.split("\\s*\\|\\s*");
        return Arrays.stream(keySetStrings)
                     .map(Combo::parseKeySet)
                     .collect(Collectors.toSet());
    }

    private static Set<Key> parseKeySet(String keySetString) {
        String[] keyStrings = keySetString.split("\\s+");
        return Arrays.stream(keyStrings)
                     .map(Key::ofName)
                     .collect(Collectors.toSet());
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
