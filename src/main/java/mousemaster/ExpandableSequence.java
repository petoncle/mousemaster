package mousemaster;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ExpandableSequence(List<Set<ComboAliasMove>> moveSets) {

    private static final Pattern MOVE_SET_OR_TOKEN_PATTERN =
            Pattern.compile("\\{([^}]+)\\}|(\\S+)");

    private static final Pattern MOVE_PATTERN =
            Pattern.compile("([+\\-#])([^-]+?)(-(\\d+)(-(\\d+))?)?(\\?)?");

    static ExpandableSequence parseSequence(String movesString,
                                            ComboMoveDuration defaultMoveDuration,
                                            Map<String, KeyAlias> aliases) {
        // Single-key shorthand: "leftctrl" = "+leftctrl"
        String trimmed = movesString.strip();
        if (!trimmed.contains("{") && !trimmed.contains(" ") &&
                !trimmed.matches("^[+\\-#].*")) {
            ComboAliasMove.PressComboAliasMove move =
                    new ComboAliasMove.PressComboAliasMove(trimmed, true,
                            defaultMoveDuration, false);
            return new ExpandableSequence(List.of(Set.of(move)));
        }
        List<Set<ComboAliasMove>> moveSets = new ArrayList<>();
        Matcher matcher = MOVE_SET_OR_TOKEN_PATTERN.matcher(trimmed);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                // {+a -b +c}: set of moves
                String content = matcher.group(1).strip();
                String[] moveTokens = content.split("\\s+");
                Set<ComboAliasMove> moveSet = new LinkedHashSet<>();
                for (String moveToken : moveTokens) {
                    moveSet.add(parseMove(moveToken, defaultMoveDuration));
                }
                moveSets.add(moveSet);
            }
            else {
                // Single token: singleton set
                String token = matcher.group(2);
                moveSets.add(Set.of(parseMove(token, defaultMoveDuration)));
            }
        }
        return new ExpandableSequence(moveSets);
    }

    private static ComboAliasMove parseMove(String moveString,
                                            ComboMoveDuration defaultMoveDuration) {
        Matcher matcher = MOVE_PATTERN.matcher(moveString);
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
        boolean optional = matcher.group(7) != null;
        ComboAliasMove move;
        if (press) {
            boolean eventMustBeEaten = moveString.startsWith("+");
            move = new ComboAliasMove.PressComboAliasMove(aliasName, eventMustBeEaten,
                    moveDuration, optional);
        }
        else
            move = new ComboAliasMove.ReleaseComboAliasMove(aliasName, moveDuration,
                    optional);
        return move;
    }

    public Map<AliasResolution, List<ComboSequence>> expand(
            Map<String, KeyAlias> aliases, KeyResolver keyResolver) {
        // Collect all alias names used across all move sets.
        Set<String> aliasNameSet = new LinkedHashSet<>();
        for (Set<ComboAliasMove> moveSet : moveSets) {
            for (ComboAliasMove move : moveSet) {
                if (aliases.containsKey(move.aliasOrKeyName()))
                    aliasNameSet.add(move.aliasOrKeyName());
            }
        }
        List<String> aliasNames = new ArrayList<>(aliasNameSet);
        Map<AliasResolution, List<ComboSequence>> sequencesByAliasResolution = new HashMap<>();
        expandAliases(moveSets, new HashMap<>(), aliasNames,
                sequencesByAliasResolution, aliases, keyResolver);
        return sequencesByAliasResolution;
    }

    private static void expandAliases(
            List<Set<ComboAliasMove>> aliasMoveSetList,
            Map<String, Key> fixedKeyByAliasName,
            List<String> aliasNames,
            Map<AliasResolution, List<ComboSequence>> sequencesByAliasResolution,
            Map<String, KeyAlias> aliasByName,
            KeyResolver keyResolver) {
        if (fixedKeyByAliasName.size() == aliasNames.size()) {
            // All aliases resolved — convert each Set<ComboAliasMove> to a MoveSet.
            List<MoveSet> resolvedMoveSets = new ArrayList<>();
            for (Set<ComboAliasMove> aliasMoveSet : aliasMoveSetList) {
                List<ComboMove> required = new ArrayList<>();
                List<ComboMove> optional = new ArrayList<>();
                for (ComboAliasMove aliasMove : aliasMoveSet) {
                    Key aliasKey = fixedKeyByAliasName.get(aliasMove.aliasOrKeyName());
                    Key key = aliasKey == null ?
                            keyResolver.resolve(aliasMove.aliasOrKeyName()) : aliasKey;
                    ComboMove comboMove = switch (aliasMove) {
                        case ComboAliasMove.PressComboAliasMove pressMove ->
                                new ComboMove.PressComboMove(key,
                                        pressMove.eventMustBeEaten(),
                                        aliasMove.duration());
                        case ComboAliasMove.ReleaseComboAliasMove releaseMove ->
                                new ComboMove.ReleaseComboMove(key, aliasMove.duration());
                    };
                    if (aliasMove.optional())
                        optional.add(comboMove);
                    else
                        required.add(comboMove);
                }
                resolvedMoveSets.add(new MoveSet(List.copyOf(required), List.copyOf(optional)));
            }
            ComboSequence sequence = new ComboSequence(List.copyOf(resolvedMoveSets));
            sequencesByAliasResolution.computeIfAbsent(
                    new AliasResolution(Map.copyOf(fixedKeyByAliasName)),
                    k -> new ArrayList<>()).add(sequence);
            return;
        }
        String fixedAliasOrKeyName = aliasNames.get(fixedKeyByAliasName.size());
        KeyAlias alias = aliasByName.get(fixedAliasOrKeyName);
        for (Key fixedKey : alias.keys()) {
            fixedKeyByAliasName.put(fixedAliasOrKeyName, fixedKey);
            expandAliases(aliasMoveSetList, fixedKeyByAliasName, aliasNames,
                    sequencesByAliasResolution, aliasByName, keyResolver);
            fixedKeyByAliasName.remove(fixedAliasOrKeyName);
        }
    }

}
