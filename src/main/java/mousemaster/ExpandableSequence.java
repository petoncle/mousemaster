package mousemaster;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ExpandableSequence(List<Set<ComboAliasMove>> moveSets) {

    private static final Pattern MOVE_SET_OR_TOKEN_PATTERN =
            Pattern.compile("\\{([^}]+)\\}|(wait\\^?\\{[^}]+\\}-\\S+)|(\\S+)");

    private static final Pattern MOVE_PATTERN =
            Pattern.compile("([+\\-#])([^-]+?)(-(\\d+)(-(\\d+))?)?(\\?)?");

    // wait-MIN, wait-MIN-MAX, wait^{keys}-MIN, wait{keys}-MIN, etc.
    // Group 1: key block with optional ^ (e.g. "^{b c}" or "{b c}")
    // Group 2: key names inside braces (e.g. "b c")
    // Group 3: min duration
    // Group 5: max duration
    private static final Pattern WAIT_PATTERN =
            Pattern.compile("wait(\\^?\\{([^}]+)\\})?-(\\d+)(-(\\d+))?");

    static ExpandableSequence parseSequence(String movesString,
                                            ComboMoveDuration defaultMoveDuration,
                                            Map<String, KeyAlias> aliases) {
        // Single-key shorthand: "leftctrl" = "+leftctrl"
        String trimmed = movesString.strip();
        if (!trimmed.contains("{") && !trimmed.contains(" ") &&
                !trimmed.matches("^[+\\-#].*") && !trimmed.startsWith("wait")) {
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
                // Single token: singleton set (group 2 = wait token, group 3 = regular token)
                String token = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
                Matcher waitMatcher = WAIT_PATTERN.matcher(token);
                if (waitMatcher.matches()) {
                    ComboMoveDuration waitDuration = new ComboMoveDuration(
                            Duration.ofMillis(Integer.parseUnsignedInt(waitMatcher.group(3))),
                            waitMatcher.group(5) == null ? null : Duration.ofMillis(
                                    Integer.parseUnsignedInt(waitMatcher.group(5))));
                    if (waitMatcher.group(1) == null && !moveSets.isEmpty()) {
                        // No key block and previous moveSet exists: syntactic sugar.
                        // Apply duration to the last move of the previous moveSet.
                        Set<ComboAliasMove> lastMoveSet = moveSets.getLast();
                        Set<ComboAliasMove> updatedMoveSet = new LinkedHashSet<>();
                        for (ComboAliasMove move : lastMoveSet) {
                            if (!move.duration().min().equals(Duration.ZERO) ||
                                move.duration().max() != null)
                                throw new IllegalArgumentException(
                                        "Duration on move before wait is invalid: " + move);
                            updatedMoveSet.add(withDuration(move, waitDuration));
                        }
                        moveSets.set(moveSets.size() - 1, updatedMoveSet);
                    }
                    else {
                        // New WaitComboAliasMove.
                        Set<String> keyNames;
                        boolean keysAreExempt;
                        if (waitMatcher.group(1) != null) {
                            String[] keys = waitMatcher.group(2).strip().split("\\s+");
                            keyNames = Set.of(keys);
                            keysAreExempt = waitMatcher.group(1).startsWith("^");
                        }
                        else {
                            keyNames = Set.of();
                            keysAreExempt = true; // plain wait: all keys break
                        }
                        moveSets.add(Set.of(new ComboAliasMove.WaitComboAliasMove(
                                keyNames, keysAreExempt, waitDuration)));
                    }
                }
                else {
                    moveSets.add(Set.of(parseMove(token, defaultMoveDuration)));
                }
            }
        }
        return new ExpandableSequence(moveSets);
    }

    private static ComboAliasMove withDuration(ComboAliasMove move, ComboMoveDuration duration) {
        return switch (move) {
            case ComboAliasMove.PressComboAliasMove p ->
                    new ComboAliasMove.PressComboAliasMove(p.aliasOrKeyName(),
                            p.eventMustBeEaten(), duration, p.optional());
            case ComboAliasMove.ReleaseComboAliasMove r ->
                    new ComboAliasMove.ReleaseComboAliasMove(r.aliasOrKeyName(),
                            duration, r.optional());
            case ComboAliasMove.WaitComboAliasMove w ->
                    throw new IllegalArgumentException("Cannot set duration on a wait move");
        };
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

    public ComboSequence toComboSequence(Map<String, KeyAlias> aliases,
                                         KeyResolver keyResolver) {
        List<MoveSet> resolvedMoveSets = new ArrayList<>();
        for (Set<ComboAliasMove> aliasMoveSet : moveSets) {
            List<ComboMove> required = new ArrayList<>();
            List<ComboMove> optional = new ArrayList<>();
            for (ComboAliasMove aliasMove : aliasMoveSet) {
                if (aliasMove instanceof ComboAliasMove.WaitComboAliasMove waitAliasMove) {
                    // Resolve key names to Key objects.
                    Set<Key> resolvedKeys = new HashSet<>();
                    for (String keyAliasOrKeyName : waitAliasMove.keyAliasOrKeyNames()) {
                        KeyAlias waitAlias = aliases.get(keyAliasOrKeyName);
                        if (waitAlias != null)
                            resolvedKeys.addAll(waitAlias.keys());
                        else
                            resolvedKeys.add(keyResolver.resolve(keyAliasOrKeyName));
                    }
                    ComboMove waitMove = new ComboMove.WaitComboMove(
                            Set.copyOf(resolvedKeys), waitAliasMove.keysAreExempt(),
                            waitAliasMove.duration());
                    required.add(waitMove);
                    continue;
                }
                KeyOrAlias keyOrAlias;
                KeyAlias alias = aliases.get(aliasMove.aliasOrKeyName());
                if (alias != null)
                    keyOrAlias = KeyOrAlias.ofAlias(alias);
                else
                    keyOrAlias = KeyOrAlias.ofKey(
                            keyResolver.resolve(aliasMove.aliasOrKeyName()));
                ComboMove comboMove = switch (aliasMove) {
                    case ComboAliasMove.PressComboAliasMove pressMove ->
                            new ComboMove.PressComboMove(keyOrAlias,
                                    pressMove.eventMustBeEaten(),
                                    aliasMove.duration());
                    case ComboAliasMove.ReleaseComboAliasMove releaseMove ->
                            new ComboMove.ReleaseComboMove(keyOrAlias,
                                    aliasMove.duration());
                    case ComboAliasMove.WaitComboAliasMove waitMove ->
                            throw new IllegalStateException();
                };
                if (aliasMove.optional())
                    optional.add(comboMove);
                else
                    required.add(comboMove);
            }
            resolvedMoveSets.add(
                    new MoveSet(List.copyOf(required), List.copyOf(optional)));
        }
        return new ComboSequence(List.copyOf(resolvedMoveSets));
    }

}
