package mousemaster;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ExpandableSequence(List<ComboAliasMove> moves) {

    static ExpandableSequence parseSequence(String movesString,
                                            ComboMoveDuration defaultMoveDuration,
                                            Map<String, Alias> aliases) {
        String[] moveStrings = movesString.split("\\s+");
        List<ComboAliasMove> moves = new ArrayList<>();
        for (String moveString : moveStrings) {
            // +leftctrl
            // +leftctrl-0-250
            // +leftctrl-1000
            Matcher matcher = Pattern.compile("([+\\-#])([^-]+?)(-(\\d+)(-(\\d+))?)?")
                                     .matcher(moveString);
            if (!matcher.matches())
                throw new IllegalArgumentException("Invalid move: " + moveString);
            boolean press = !moveString.startsWith("-");
            ComboMoveDuration moveDuration;
            if (matcher.group(3) == null)
                moveDuration = defaultMoveDuration;
            else
                moveDuration = new ComboMoveDuration(
                        Duration.ofMillis(Integer.parseUnsignedInt(matcher.group(4))),
                        matcher.group(6) == null ? null : Duration.ofMillis(
                                Integer.parseUnsignedInt(matcher.group(6))));
            String aliasName = matcher.group(2);
            ComboAliasMove move;
            if (press) {
                boolean eventMustBeEaten = moveString.startsWith("+");
                move = new ComboAliasMove.PressComboAliasMove(aliasName, eventMustBeEaten,
                        moveDuration);
            }
            else
                move = new ComboAliasMove.ReleaseComboAliasMove(aliasName, moveDuration);
            moves.add(move);
        }
        return new ExpandableSequence(moves);
    }

    public List<ComboSequence> expand(Map<String, Alias> aliases) {
        // alias1=key11 key12
        // alias2=key21 key22
        // +alias1 -alias1 +alias2 = +key11 -key11 +key21 | +key11 -key11 +key22 | +key12 ...
        List<String> aliasOrKeyNames =
                moves.stream().map(ComboAliasMove::aliasOrKeyName).distinct().toList();
        ArrayList<ComboSequence> sequences = new ArrayList<>();
        recursivelyExpand(new HashMap<>(), aliasOrKeyNames, sequences, aliases);
        return sequences;
    }

    private void recursivelyExpand(Map<String, Key> fixedKeyByAliasName,
                                   List<String> aliasOrKeyNames,
                                   List<ComboSequence> sequences,
                                   Map<String, Alias> aliasByName) {
        if (fixedKeyByAliasName.size() == aliasOrKeyNames.size()) {
            List<ComboMove> moves = new ArrayList<>();
            for (ComboAliasMove aliasMove : this.moves) {
                Key key = fixedKeyByAliasName.get(aliasMove.aliasOrKeyName());
                moves.add(switch (aliasMove) {
                    case ComboAliasMove.PressComboAliasMove pressComboAliasMove ->
                            new ComboMove.PressComboMove(key,
                                    pressComboAliasMove.eventMustBeEaten(),
                                    aliasMove.duration());
                    case ComboAliasMove.ReleaseComboAliasMove releaseComboAliasMove ->
                            new ComboMove.ReleaseComboMove(key, aliasMove.duration());
                });
            }
            ComboSequence sequence = new ComboSequence(moves);
            sequences.add(sequence);
            return;
        }
        String fixedAliasOrKeyName = aliasOrKeyNames.get(fixedKeyByAliasName.size());
        Alias alias = aliasByName.get(fixedAliasOrKeyName);
        Set<Key> aliasKeys =
                alias == null ? Set.of(Key.ofName(fixedAliasOrKeyName)) : alias.keys();
        for (Key fixedKey : aliasKeys) {
            fixedKeyByAliasName.put(fixedAliasOrKeyName, fixedKey);
            recursivelyExpand(fixedKeyByAliasName, aliasOrKeyNames, sequences,
                    aliasByName);
            fixedKeyByAliasName.remove(fixedAliasOrKeyName);
        }
    }

}
