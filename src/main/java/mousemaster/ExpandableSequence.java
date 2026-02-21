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
            Pattern.compile("\\{([^}]+)\\}|(\\+?wait-ignore(?:-all-except)?-\\{[^}]+\\}(?:-\\S+)?)|(\\S+)");

    private static final Pattern MOVE_PATTERN =
            Pattern.compile("([+\\-#])(!?)([^-]+?)(-(\\d+)(-(\\d+))?)?(\\?)?");

    // [+]wait[-MIN[-MAX]],
    // [+]wait-ignore-{keys}[-MIN[-MAX]], [+]wait-ignore-all[-MIN[-MAX]],
    // [+]wait-ignore-all-except-{keys}[-MIN[-MAX]]
    // When MIN is omitted, it defaults to 0.
    // Group 1: optional "+" prefix (eat absorbed events)
    // Group 2: full ignore block (e.g. "-ignore-{b c}" or "-ignore-all" or "-ignore-all-except-{b c}")
    // Group 3: "-all..." or "-{keys}" part after -ignore
    // Group 4: "-except-{keys}" part (null if just -all)
    // Group 5: key names in except block
    // Group 6: key names in ignore block (direct, without -all)
    // Group 8: min duration (null if omitted, defaults to 0)
    // Group 10: max duration
    private static final Pattern WAIT_PATTERN =
            Pattern.compile("(\\+)?wait(-ignore(-all(-except-\\{([^}]+)\\})?|-\\{([^}]+)\\}))?(-(\\d+)(-(\\d+))?)?");

    static ExpandableSequence parseSequence(String movesString,
                                            ComboMoveDuration defaultMoveDuration,
                                            Map<String, KeyAlias> aliases) {
        // Single-key shorthand: "leftctrl" = "+leftctrl"
        String trimmed = movesString.strip();
        if (!trimmed.contains("{") && !trimmed.contains(" ") &&
                !trimmed.matches("^[+\\-#].*") && !trimmed.startsWith("wait")) {
            ComboAliasMove.PressComboAliasMove move =
                    new ComboAliasMove.PressComboAliasMove(trimmed, false, true,
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
                    boolean ignoredKeysEatEvents = waitMatcher.group(1) != null;
                    ComboMoveDuration waitDuration = new ComboMoveDuration(
                            Duration.ofMillis(waitMatcher.group(8) == null ? 0 :
                                    Integer.parseUnsignedInt(waitMatcher.group(8))),
                            waitMatcher.group(10) == null ? null : Duration.ofMillis(
                                    Integer.parseUnsignedInt(waitMatcher.group(10))));
                    Set<String> keyNames;
                    boolean listedKeysAreIgnored;
                    if (waitMatcher.group(2) == null) {
                        // Plain wait: no key is ignored.
                        keyNames = Set.of();
                        listedKeysAreIgnored = true;
                    }
                    else if (waitMatcher.group(6) != null) {
                        // wait-ignore-{keys}: listed keys are ignored.
                        String[] keys = waitMatcher.group(6).strip().split("\\s+");
                        keyNames = Set.of(keys);
                        listedKeysAreIgnored = true;
                    }
                    else if (waitMatcher.group(4) == null) {
                        // wait-ignore-all: all keys are ignored.
                        keyNames = Set.of();
                        listedKeysAreIgnored = false;
                    }
                    else {
                        // wait-ignore-all-except-{keys}: all keys except listed are ignored.
                        String[] keys = waitMatcher.group(5).strip().split("\\s+");
                        keyNames = Set.of(keys);
                        listedKeysAreIgnored = false;
                    }
                    moveSets.add(Set.of(new ComboAliasMove.WaitComboAliasMove(
                            keyNames, listedKeysAreIgnored, ignoredKeysEatEvents, waitDuration)));
                }
                else {
                    moveSets.add(Set.of(parseMove(token, defaultMoveDuration)));
                }
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
        boolean negated = !matcher.group(2).isEmpty();
        ComboMoveDuration moveDuration;
        if (matcher.group(4) == null)
            moveDuration = defaultMoveDuration;
        else
            moveDuration = new ComboMoveDuration(
                    Duration.ofMillis(Integer.parseUnsignedInt(matcher.group(5))),
                    matcher.group(7) == null ? null : Duration.ofMillis(
                            Integer.parseUnsignedInt(matcher.group(7))));
        String aliasName = matcher.group(3);
        boolean optional = matcher.group(8) != null;
        ComboAliasMove move;
        if (press) {
            boolean eventMustBeEaten = moveString.startsWith("+");
            move = new ComboAliasMove.PressComboAliasMove(aliasName, negated,
                    eventMustBeEaten, moveDuration, optional);
        }
        else
            move = new ComboAliasMove.ReleaseComboAliasMove(aliasName, negated,
                    moveDuration, optional);
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
                IgnoredKeySet ignoredKeySet = waitAliasMove.listedKeysAreIgnored() ?
                        new IgnoredKeySet.Only(Set.copyOf(resolvedKeys)) :
                        new IgnoredKeySet.AllExcept(Set.copyOf(resolvedKeys));
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
