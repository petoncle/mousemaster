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
                    ComboAliasMove move = parseMove(moveToken, defaultMoveDuration);
                    KeyAlias alias = aliases.get(move.aliasOrKeyName());
                    if (alias != null) {
                        // Expand alias into one move per key in this move set.
                        for (Key key : alias.keys()) {
                            moveSet.add(switch (move) {
                                case ComboAliasMove.PressComboAliasMove p ->
                                        new ComboAliasMove.PressComboAliasMove(
                                                key.name(), p.eventMustBeEaten(),
                                                p.duration(), p.optional());
                                case ComboAliasMove.ReleaseComboAliasMove r ->
                                        new ComboAliasMove.ReleaseComboAliasMove(
                                                key.name(), r.duration(),
                                                r.optional());
                            });
                        }
                    }
                    else {
                        moveSet.add(move);
                    }
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
        // Step 1: Expand move sets into all permutations
        List<List<ComboAliasMove>> expandedMoveLists = new ArrayList<>();
        expandMoveSets(moveSets, 0, new ArrayList<>(), expandedMoveLists);

        // Step 2: For each permutation, expand aliases
        Map<AliasResolution, List<ComboSequence>> sequencesByAliasResolution = new HashMap<>();
        for (List<ComboAliasMove> moves : expandedMoveLists) {
            expandAliases(moves, new HashMap<>(),
                    moves.stream().map(ComboAliasMove::aliasOrKeyName)
                         .filter(aliases.keySet()::contains).distinct().toList(),
                    sequencesByAliasResolution, aliases, keyResolver);
        }
        return sequencesByAliasResolution;
    }

    private static void expandMoveSets(List<Set<ComboAliasMove>> moveSets, int index,
                                       List<ComboAliasMove> current,
                                       List<List<ComboAliasMove>> result) {
        if (index == moveSets.size()) {
            result.add(new ArrayList<>(current));
            return;
        }
        Set<ComboAliasMove> moveSet = moveSets.get(index);
        if (moveSet.size() == 1) {
            ComboAliasMove move = moveSet.iterator().next();
            if (move.optional()) {
                // Optional singleton: branch: skip or include.
                // Skip branch.
                expandMoveSets(moveSets, index + 1, current, result);
                // Include branch.
                current.add(move);
                expandMoveSets(moveSets, index + 1, current, result);
                current.removeLast();
            }
            else {
                // Required singleton: just append
                current.add(move);
                expandMoveSets(moveSets, index + 1, current, result);
                current.removeLast();
            }
        }
        else {
            // Multi-element set: separate required and optional moves
            List<ComboAliasMove> required = new ArrayList<>();
            List<ComboAliasMove> optional = new ArrayList<>();
            for (ComboAliasMove move : moveSet) {
                if (move.optional())
                    optional.add(move);
                else
                    required.add(move);
            }
            if (optional.isEmpty()) {
                // No optional moves: generate all permutations (existing behavior)
                List<ComboAliasMove> elements = new ArrayList<>(moveSet);
                List<List<ComboAliasMove>> permutations = new ArrayList<>();
                generatePermutations(elements, 0, permutations);
                for (List<ComboAliasMove> perm : permutations) {
                    current.addAll(perm);
                    expandMoveSets(moveSets, index + 1, current, result);
                    for (int i = 0; i < perm.size(); i++)
                        current.removeLast();
                }
            }
            else {
                // Generate all subsets of optional moves.
                int n = optional.size();
                for (int mask = 0; mask < (1 << n); mask++) {
                    List<ComboAliasMove> subset = new ArrayList<>(required);
                    for (int bit = 0; bit < n; bit++) {
                        if ((mask & (1 << bit)) != 0)
                            subset.add(optional.get(bit));
                    }
                    if (subset.isEmpty())
                        continue; // All optional, none selected: skip empty set.
                    // Generate all permutations of this subset.
                    List<List<ComboAliasMove>> permutations = new ArrayList<>();
                    generatePermutations(subset, 0, permutations);
                    for (List<ComboAliasMove> perm : permutations) {
                        current.addAll(perm);
                        expandMoveSets(moveSets, index + 1, current, result);
                        for (int i = 0; i < perm.size(); i++)
                            current.removeLast();
                    }
                }
            }
        }
    }

    private static void generatePermutations(List<ComboAliasMove> elements, int start,
                                             List<List<ComboAliasMove>> result) {
        if (start == elements.size()) {
            result.add(new ArrayList<>(elements));
            return;
        }
        for (int i = start; i < elements.size(); i++) {
            Collections.swap(elements, start, i);
            generatePermutations(elements, start + 1, result);
            Collections.swap(elements, start, i);
        }
    }

    private static void expandAliases(List<ComboAliasMove> moves,
                                      Map<String, Key> fixedKeyByAliasName,
                                      List<String> aliasNames,
                                      Map<AliasResolution, List<ComboSequence>> sequencesByAliasResolution,
                                      Map<String, KeyAlias> aliasByName,
                                      KeyResolver keyResolver) {
        if (fixedKeyByAliasName.size() == aliasNames.size()) {
            List<ComboMove> comboMoves = new ArrayList<>();
            for (ComboAliasMove aliasMove : moves) {
                Key aliasKey = fixedKeyByAliasName.get(aliasMove.aliasOrKeyName());
                Key key = aliasKey == null ? keyResolver.resolve(aliasMove.aliasOrKeyName()) :
                        aliasKey;
                comboMoves.add(switch (aliasMove) {
                    case ComboAliasMove.PressComboAliasMove pressComboAliasMove ->
                            new ComboMove.PressComboMove(key,
                                    pressComboAliasMove.eventMustBeEaten(),
                                    aliasMove.duration());
                    case ComboAliasMove.ReleaseComboAliasMove releaseComboAliasMove ->
                            new ComboMove.ReleaseComboMove(key, aliasMove.duration());
                });
            }
            ComboSequence sequence = new ComboSequence(comboMoves);
            sequencesByAliasResolution.computeIfAbsent(
                    new AliasResolution(Map.copyOf(fixedKeyByAliasName)),
                    k -> new ArrayList<>()).add(sequence);
            return;
        }
        String fixedAliasOrKeyName = aliasNames.get(fixedKeyByAliasName.size());
        KeyAlias alias = aliasByName.get(fixedAliasOrKeyName);
        for (Key fixedKey : alias.keys()) {
            fixedKeyByAliasName.put(fixedAliasOrKeyName, fixedKey);
            expandAliases(moves, fixedKeyByAliasName, aliasNames,
                    sequencesByAliasResolution, aliasByName, keyResolver);
            fixedKeyByAliasName.remove(fixedAliasOrKeyName);
        }
    }

}
