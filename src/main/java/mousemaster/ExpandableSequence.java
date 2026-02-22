package mousemaster;

import mousemaster.ComboMove.KeyComboMove;
import mousemaster.ComboMove.PressComboMove;
import mousemaster.ComboMove.ReleaseComboMove;
import mousemaster.ComboMove.WaitComboMove;
import mousemaster.MoveSet.KeyMoveSet;
import mousemaster.MoveSet.WaitMoveSet;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ExpandableSequence(List<Set<ComboAliasMove>> moveSets) {

    private static final Pattern MOVE_SET_OR_TOKEN_PATTERN =
            Pattern.compile("([#+]!?\\{[^}]+\\}(?:-\\d+(?:-\\d+)?)?)|\\{([^}]+)\\}|(\\S+)");

    private static final Pattern MOVE_PATTERN =
            Pattern.compile("([+\\-#])(!?)(\\*?)([^-]+?)(-(\\d+)(-(\\d+))?)?(\\?)?");

    // [#+]!?{keys|*}[-MIN[-MAX]]
    // Group 1: "#" or "+" prefix
    // Group 2: "!" (all-except) or empty
    // Group 3: key names or "*" (all)
    // Group 4: min duration (null if omitted, defaults to 0)
    // Group 6: max duration
    private static final Pattern IGNORE_PATTERN =
            Pattern.compile("([#+])(!?)\\{([^}]+)\\}(?:-(\\d+)(-(\\d+))?)?");

    // [+]wait[-MIN[-MAX]]
    // Group 1: optional "+" prefix
    // Group 3: min duration (null if omitted, defaults to 0)
    // Group 5: max duration
    private static final Pattern WAIT_PATTERN =
            Pattern.compile("(\\+)?wait(-(\\d+)(-(\\d+))?)?");

    static ExpandableSequence parseSequence(String movesString,
                                            ComboMoveDuration defaultMoveDuration,
                                            Map<String, KeyAlias> aliases) {
        // Single-key shorthand: "leftctrl" = "+leftctrl"
        String trimmed = movesString.strip();
        if (!trimmed.contains("{") && !trimmed.contains(" ") &&
                !trimmed.matches("^[+\\-#].*") && !trimmed.startsWith("wait")) {
            ComboAliasMove.PressComboAliasMove move =
                    new ComboAliasMove.PressComboAliasMove(trimmed, false, true,
                            defaultMoveDuration, false, false);
            return new ExpandableSequence(List.of(Set.of(move)));
        }
        List<Set<ComboAliasMove>> moveSets = new ArrayList<>();
        Matcher matcher = MOVE_SET_OR_TOKEN_PATTERN.matcher(trimmed);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                // Ignore/eat token: #{keys}, +{keys}, #!{keys}, +!{keys}, #{*}, +{*}
                Matcher ignoreMatcher = IGNORE_PATTERN.matcher(matcher.group(1));
                if (!ignoreMatcher.matches())
                    throw new IllegalArgumentException(
                            "Invalid ignore token: " + matcher.group(1));
                boolean ignoredKeysEatEvents = ignoreMatcher.group(1).equals("+");
                boolean allExcept = !ignoreMatcher.group(2).isEmpty();
                String content = ignoreMatcher.group(3).strip();
                ComboMoveDuration waitDuration = new ComboMoveDuration(
                        Duration.ofMillis(ignoreMatcher.group(4) == null ? 0 :
                                Integer.parseUnsignedInt(ignoreMatcher.group(4))),
                        ignoreMatcher.group(6) == null ? null : Duration.ofMillis(
                                Integer.parseUnsignedInt(ignoreMatcher.group(6))));
                Set<String> keyNames;
                boolean listedKeysAreIgnored;
                if (content.equals("*")) {
                    // #{*} = ignore all, +{*} = eat all
                    keyNames = Set.of();
                    listedKeysAreIgnored = false;
                }
                else if (allExcept) {
                    // #!{keys} = ignore all except, +!{keys} = eat all except
                    String[] keys = content.split("\\s+");
                    keyNames = Set.of(keys);
                    listedKeysAreIgnored = false;
                }
                else {
                    // #{keys} = ignore listed, +{keys} = eat listed
                    String[] keys = content.split("\\s+");
                    keyNames = Set.of(keys);
                    listedKeysAreIgnored = true;
                }
                moveSets.add(Set.of(new ComboAliasMove.WaitComboAliasMove(
                        keyNames, listedKeysAreIgnored, ignoredKeysEatEvents, waitDuration)));
            }
            else if (matcher.group(2) != null) {
                // {+a -b +c}: set of moves (any-order within the set)
                String content = matcher.group(2).strip();
                String[] moveTokens = content.split("\\s+");
                Set<ComboAliasMove> moveSet = new LinkedHashSet<>();
                for (String moveToken : moveTokens) {
                    ComboAliasMove move = parseMove(moveToken, defaultMoveDuration);
                    if (move.expand()) {
                        // Inside braces: expand alias into individual key
                        // moves in the same MoveSet (any-order).
                        expandAliasIntoMoves(move, aliases, moveSet);
                    }
                    else {
                        moveSet.add(move);
                    }
                }
                moveSets.add(moveSet);
            }
            else {
                // Single token (group 3)
                String token = matcher.group(3);
                Matcher waitMatcher = WAIT_PATTERN.matcher(token);
                if (waitMatcher.matches()) {
                    // Plain wait: no key is ignored.
                    boolean ignoredKeysEatEvents = waitMatcher.group(1) != null;
                    ComboMoveDuration waitDuration = new ComboMoveDuration(
                            Duration.ofMillis(waitMatcher.group(3) == null ? 0 :
                                    Integer.parseUnsignedInt(waitMatcher.group(3))),
                            waitMatcher.group(5) == null ? null : Duration.ofMillis(
                                    Integer.parseUnsignedInt(waitMatcher.group(5))));
                    moveSets.add(Set.of(new ComboAliasMove.WaitComboAliasMove(
                            Set.of(), true, ignoredKeysEatEvents, waitDuration)));
                }
                else {
                    ComboAliasMove move = parseMove(token, defaultMoveDuration);
                    if (move.expand()) {
                        // Outside braces: expand alias into separate
                        // singleton MoveSets (sequential order).
                        KeyAlias alias = aliases.get(move.aliasOrKeyName());
                        if (alias == null)
                            throw new IllegalArgumentException(
                                    "Cannot expand non-alias: " +
                                    move.aliasOrKeyName());
                        for (Key key : alias.keys()) {
                            ComboAliasMove expanded = expandedMove(
                                    move, key.name());
                            moveSets.add(Set.of(expanded));
                        }
                    }
                    else {
                        moveSets.add(Set.of(move));
                    }
                }
            }
        }
        return new ExpandableSequence(moveSets);
    }

    private static void expandAliasIntoMoves(ComboAliasMove move,
                                               Map<String, KeyAlias> aliases,
                                               Set<ComboAliasMove> moveSet) {
        KeyAlias alias = aliases.get(move.aliasOrKeyName());
        if (alias == null)
            throw new IllegalArgumentException(
                    "Cannot expand non-alias: " + move.aliasOrKeyName());
        for (Key key : alias.keys())
            moveSet.add(expandedMove(move, key.name()));
    }

    private static ComboAliasMove expandedMove(ComboAliasMove move,
                                                String keyName) {
        return switch (move) {
            case ComboAliasMove.PressComboAliasMove pm ->
                    new ComboAliasMove.PressComboAliasMove(keyName,
                            pm.negated(), pm.eventMustBeEaten(),
                            pm.duration(), pm.optional(), false);
            case ComboAliasMove.ReleaseComboAliasMove rm ->
                    new ComboAliasMove.ReleaseComboAliasMove(keyName,
                            rm.negated(), rm.duration(), rm.optional(),
                            false);
            case ComboAliasMove.WaitComboAliasMove wm ->
                    throw new IllegalStateException(
                            "Cannot expand wait move");
        };
    }

    private static ComboAliasMove parseMove(String moveString,
                                            ComboMoveDuration defaultMoveDuration) {
        Matcher matcher = MOVE_PATTERN.matcher(moveString);
        if (!matcher.matches())
            throw new IllegalArgumentException("Invalid move: " + moveString);
        boolean press = !moveString.startsWith("-");
        boolean negated = !matcher.group(2).isEmpty();
        boolean expand = !matcher.group(3).isEmpty();
        ComboMoveDuration moveDuration;
        if (matcher.group(5) == null)
            moveDuration = defaultMoveDuration;
        else
            moveDuration = new ComboMoveDuration(
                    Duration.ofMillis(Integer.parseUnsignedInt(matcher.group(6))),
                    matcher.group(8) == null ? null : Duration.ofMillis(
                            Integer.parseUnsignedInt(matcher.group(8))));
        String aliasName = matcher.group(4);
        boolean optional = matcher.group(9) != null;
        ComboAliasMove move;
        if (press) {
            boolean eventMustBeEaten = moveString.startsWith("+");
            move = new ComboAliasMove.PressComboAliasMove(aliasName, negated,
                    eventMustBeEaten, moveDuration, optional, expand);
        }
        else
            move = new ComboAliasMove.ReleaseComboAliasMove(aliasName, negated,
                    moveDuration, optional, expand);
        return move;
    }

    public ComboSequence toComboSequence(Map<String, KeyAlias> aliases,
                                         KeyResolver keyResolver) {
        List<MoveSet> resolvedMoveSets = new ArrayList<>();
        for (Set<ComboAliasMove> aliasMoveSet : moveSets) {
            // A MoveSet is either a single wait move or a set of key moves.
            ComboAliasMove first = aliasMoveSet.iterator().next();
            if (first instanceof ComboAliasMove.WaitComboAliasMove waitAliasMove) {
                // Resolve key names to Key objects.
                Set<Key> resolvedKeys = new HashSet<>();
                for (String keyAliasOrKeyName : waitAliasMove.keyAliasOrKeyNames()) {
                    KeyAlias waitAlias = aliases.get(keyAliasOrKeyName);
                    if (waitAlias != null)
                        resolvedKeys.addAll(waitAlias.keys());
                    else
                        resolvedKeys.add(keyResolver.resolve(keyAliasOrKeyName));
                }
                KeySet ignoredKeySet = waitAliasMove.listedKeysAreIgnored() ?
                        new KeySet.Only(Set.copyOf(resolvedKeys)) :
                        new KeySet.AllExcept(Set.copyOf(resolvedKeys));
                WaitComboMove waitMove = new WaitComboMove(
                        ignoredKeySet, waitAliasMove.ignoredKeysEatEvents(),
                        waitAliasMove.duration());
                resolvedMoveSets.add(new WaitMoveSet(waitMove));
                continue;
            }
            List<KeyComboMove> required = new ArrayList<>();
            List<KeyComboMove> optional = new ArrayList<>();
            for (ComboAliasMove aliasMove : aliasMoveSet) {
                KeyOrAlias keyOrAlias;
                KeyAlias alias = aliases.get(aliasMove.aliasOrKeyName());
                if (alias != null)
                    keyOrAlias = KeyOrAlias.ofAlias(alias);
                else
                    keyOrAlias = KeyOrAlias.ofKey(
                            keyResolver.resolve(aliasMove.aliasOrKeyName()));
                KeyComboMove comboMove = switch (aliasMove) {
                    case ComboAliasMove.PressComboAliasMove pressMove ->
                            new PressComboMove(keyOrAlias,
                                    pressMove.negated(),
                                    pressMove.eventMustBeEaten(),
                                    aliasMove.duration());
                    case ComboAliasMove.ReleaseComboAliasMove releaseMove ->
                            new ReleaseComboMove(keyOrAlias,
                                    releaseMove.negated(),
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
                    new KeyMoveSet(List.copyOf(required), List.copyOf(optional)));
        }
        return new ComboSequence(List.copyOf(resolvedMoveSets));
    }

}
