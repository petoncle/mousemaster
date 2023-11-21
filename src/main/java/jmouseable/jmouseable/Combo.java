package jmouseable.jmouseable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record Combo(Set<Set<Key>> mustNotBePressedKeySets, ComboSequence sequence) {

    public static Combo of(String string, ComboMoveDuration defaultMoveDuration) {
        Matcher mustNotBePressedKeySetsMatcher =
                Pattern.compile("\\^\\{([^{}]+)\\}\\s*").matcher(string);
        Set<Set<Key>> mustNotBePressedKeySets;
        String sequenceString;
        if (mustNotBePressedKeySetsMatcher.find()) {
            String mustNotBePressedKeySetsString = mustNotBePressedKeySetsMatcher.group(1);
            mustNotBePressedKeySets =
                    parseKeySets(mustNotBePressedKeySetsString);
            sequenceString = string.substring(mustNotBePressedKeySetsMatcher.end());
        }
        else {
            mustNotBePressedKeySets = Set.of();
            sequenceString = string;
        }
        ComboSequence sequence = ComboSequence.parseSequence(sequenceString, defaultMoveDuration);
        if (mustNotBePressedKeySets.isEmpty() && sequence.moves().isEmpty())
            throw new IllegalArgumentException("Empty combo: " + string);
        return new Combo(mustNotBePressedKeySets, sequence);
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
                     .map(ComboSequence::parseKey)
                     .collect(Collectors.toSet());
    }

    public static List<Combo> multiCombo(String multiComboString,
                                         ComboMoveDuration defaultMoveDuration) {
        // One combo is: ^{key|...} move ...
        // Two combos: ^{key|...} move ... | ^{key|...} move ...
        Matcher matcher = Pattern.compile("(\\^\\{[^}]+\\})?[^\\^{}|]+").matcher(multiComboString);
        List<Combo> combos = new ArrayList<>();
        while (matcher.find())
            combos.add(of(matcher.group(0), defaultMoveDuration));
        if (combos.isEmpty())
            throw new IllegalArgumentException(
                    "Invalid multi-combo: " + multiComboString);
        return List.copyOf(combos);
    }

    @Override
    public String toString() {
        return (mustNotBePressedKeySets.isEmpty() ? "" : "^{" + mustNotBePressedKeySets.stream()
                                                                                       .map(keySet -> keySet.stream()
                                                                                                .map(Key::keyName)
                                                                                                .collect(
                                                                                                        Collectors.joining(
                                                                                                                " ")))
                                                                                       .collect(
                                                                                   Collectors.joining(
                                                                                           "|")) +
                                                         "} ") + sequence;
    }

}
