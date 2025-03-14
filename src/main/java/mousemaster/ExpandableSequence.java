package mousemaster;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ExpandableSequence(List<ComboAliasMove> moves) {

    static ExpandableSequence parseSequence(String movesString,
                                            ComboMoveDuration defaultMoveDuration,
                                            Map<String, KeyAlias> aliases) {
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

    public Map<AliasResolution, ComboSequence> expand(Map<String, KeyAlias> aliases) {
        // alias1=key11 key12
        // alias2=key21 key22
        // +alias1 -alias1 +alias2 = +key11 -key11 +key21 | +key11 -key11 +key22 | +key12 ...
        List<String> aliasNames =
                moves.stream().map(ComboAliasMove::aliasOrKeyName)
                     .filter(aliases.keySet()::contains).distinct().toList();
        Map<AliasResolution, ComboSequence> sequenceByAliasResolution = new HashMap<>();
        recursivelyExpand(new HashMap<>(), aliasNames, sequenceByAliasResolution, aliases);
        return sequenceByAliasResolution;
    }

    private void recursivelyExpand(Map<String, Key> fixedKeyByAliasName,
                                   List<String> aliasNames,
                                   Map<AliasResolution, ComboSequence> sequenceByAliasResolution,
                                   Map<String, KeyAlias> aliasByName) {
        if (fixedKeyByAliasName.size() == aliasNames.size()) {
            List<ComboMove> moves = new ArrayList<>();
            for (ComboAliasMove aliasMove : this.moves) {
                Key aliasKey = fixedKeyByAliasName.get(aliasMove.aliasOrKeyName());
                Key key = aliasKey == null ? Key.ofName(aliasMove.aliasOrKeyName()) :
                        aliasKey;
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
            sequenceByAliasResolution.put(
                    new AliasResolution(Map.copyOf(fixedKeyByAliasName)), sequence);
            return;
        }
        String fixedAliasOrKeyName = aliasNames.get(fixedKeyByAliasName.size());
        KeyAlias alias = aliasByName.get(fixedAliasOrKeyName);
        for (Key fixedKey : alias.keys()) {
            fixedKeyByAliasName.put(fixedAliasOrKeyName, fixedKey);
            recursivelyExpand(fixedKeyByAliasName, aliasNames, sequenceByAliasResolution,
                    aliasByName);
            fixedKeyByAliasName.remove(fixedAliasOrKeyName);
        }
    }

}
