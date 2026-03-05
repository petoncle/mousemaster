package mousemaster;

import mousemaster.ComboAliasMove.Optionality;
import mousemaster.ComboMove.KeyComboMove;
import mousemaster.ComboMove.PressComboMove;
import mousemaster.ComboMove.ReleaseComboMove;
import mousemaster.ComboMove.TapComboMove;
import mousemaster.ComboMove.WaitComboMove;
import mousemaster.MoveSet.KeyMoveSet;
import mousemaster.MoveSet.WaitMoveSet;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ExpandableSequence(List<Set<ComboAliasMove>> moveSets) {

    private static final Pattern MOVE_SET_OR_TOKEN_PATTERN =
            Pattern.compile("([#+]!?\\{[^}]+\\}(?:-\\d+(?:-\\d+)?)?)|(\\{(?:[^{}]|\\{[^}]*\\})+\\}(?:-\\d+(?:-\\d+)?)?)|(\\S+)");

    // Tokenizes inside braces: matches either an ignored-key spec or a regular move token.
    // Group 1: ignored-key spec like #{a b}-0-500 or +{*}
    // Group 2: regular move token
    private static final Pattern BRACE_CONTENT_TOKEN_PATTERN =
            Pattern.compile("([#+]!?\\{[^}]+\\}(?:-\\d+(?:-\\d+)?)?)|(\\S+)");

    private static final Pattern MOVE_PATTERN =
            Pattern.compile("([+\\-#])(!?)(\\*?)([^-?+]+?)(-(\\d+)(-(\\d+))?)?([?+])?");

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

    // Bare token duration suffix: keyName[-MIN[-MAX]]
    // Group 1: key name (non-greedy)
    // Group 2: min duration
    // Group 3: max duration
    private static final Pattern BARE_DURATION_SUFFIX_PATTERN =
            Pattern.compile("^(.*?)(?:-(\\d+)(?:-(\\d+))?)?$");

    private static final Pattern DURATION_SUFFIX_PATTERN =
            Pattern.compile("-(\\d+)(?:-(\\d+))?");

    static ExpandableSequence parseSequence(String movesString,
                                            ComboMoveDuration defaultMoveDuration,
                                            Map<String, KeyAlias> aliases) {
        // Single-key shorthand: "leftctrl" = "+leftctrl -leftctrl"
        String trimmed = movesString.strip();
        if (!trimmed.contains("{") && !trimmed.contains(" ") &&
                !trimmed.matches("^[+\\-#*].*") && !trimmed.startsWith("wait")) {
            return new ExpandableSequence(
                    bareTokenMoves(trimmed, defaultMoveDuration, aliases)
                            .stream().map(Set::of).toList());
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
                ComboAliasMove.WaitComboAliasMove waitAliasMove;
                if (content.equals("*")) {
                    // #{*} = ignore all, +{*} = eat all
                    waitAliasMove = new ComboAliasMove.WaitComboAliasMove.KeyWaitComboAliasMove(
                            Set.of(), false, ignoredKeysEatEvents, waitDuration);
                }
                else if (content.equals("-")) {
                    // #{-} = ignore all releases, +{-} = eat all releases
                    waitAliasMove = new ComboAliasMove.WaitComboAliasMove.ReleaseWaitComboAliasMove(
                            ignoredKeysEatEvents, waitDuration);
                }
                else if (content.equals("+")) {
                    // #{+} = ignore all presses, +{+} = eat all presses
                    waitAliasMove = new ComboAliasMove.WaitComboAliasMove.PressWaitComboAliasMove(
                            ignoredKeysEatEvents, waitDuration);
                }
                else if (allExcept) {
                    // #!{keys} = ignore all except, +!{keys} = eat all except
                    String[] keys = content.split("\\s+");
                    waitAliasMove = new ComboAliasMove.WaitComboAliasMove.KeyWaitComboAliasMove(
                            Set.of(keys), false, ignoredKeysEatEvents, waitDuration);
                }
                else {
                    // #{keys} = ignore listed, +{keys} = eat listed
                    String[] keys = content.split("\\s+");
                    waitAliasMove = new ComboAliasMove.WaitComboAliasMove.KeyWaitComboAliasMove(
                            Set.of(keys), true, ignoredKeysEatEvents, waitDuration);
                }
                moveSets.add(Set.of(waitAliasMove));
            }
            else if (matcher.group(2) != null) {
                // {+a -b +c}: set of moves (any-order within the set)
                // Group 2 now includes the outer braces + optional duration suffix.
                String raw = matcher.group(2);
                int closingBrace = raw.lastIndexOf('}');
                String content = raw.substring(1, closingBrace).strip();
                String durationSuffix = raw.substring(closingBrace + 1);
                ComboMoveDuration braceDuration = defaultMoveDuration;
                if (!durationSuffix.isEmpty()) {
                    Matcher dm = DURATION_SUFFIX_PATTERN.matcher(durationSuffix);
                    if (dm.matches())
                        braceDuration = new ComboMoveDuration(
                                Duration.ofMillis(Integer.parseUnsignedInt(dm.group(1))),
                                dm.group(2) == null ? null : Duration.ofMillis(
                                        Integer.parseUnsignedInt(dm.group(2))));
                }
                Set<ComboAliasMove> moveSet = new LinkedHashSet<>();
                Matcher braceTokenMatcher = BRACE_CONTENT_TOKEN_PATTERN.matcher(content);
                while (braceTokenMatcher.find()) {
                    if (braceTokenMatcher.group(1) != null) {
                        // Ignored-key spec inside braces: #{keys}, +{*}, etc.
                        Matcher ignoreMatcher = IGNORE_PATTERN.matcher(braceTokenMatcher.group(1));
                        if (!ignoreMatcher.matches())
                            throw new IllegalArgumentException(
                                    "Invalid ignore token inside braces: " + braceTokenMatcher.group(1));
                        boolean ignoredKeysEatEvents = ignoreMatcher.group(1).equals("+");
                        boolean allExcept = !ignoreMatcher.group(2).isEmpty();
                        String ignoreContent = ignoreMatcher.group(3).strip();
                        ComboMoveDuration waitDuration;
                        if (ignoreMatcher.group(4) != null)
                            waitDuration = new ComboMoveDuration(
                                    Duration.ofMillis(Integer.parseUnsignedInt(ignoreMatcher.group(4))),
                                    ignoreMatcher.group(6) == null ? null : Duration.ofMillis(
                                            Integer.parseUnsignedInt(ignoreMatcher.group(6))));
                        else
                            waitDuration = braceDuration;
                        ComboAliasMove.WaitComboAliasMove waitAliasMove;
                        if (ignoreContent.equals("*")) {
                            waitAliasMove = new ComboAliasMove.WaitComboAliasMove.KeyWaitComboAliasMove(
                                    Set.of(), false, ignoredKeysEatEvents, waitDuration);
                        }
                        else if (ignoreContent.equals("-")) {
                            waitAliasMove = new ComboAliasMove.WaitComboAliasMove.ReleaseWaitComboAliasMove(
                                    ignoredKeysEatEvents, waitDuration);
                        }
                        else if (ignoreContent.equals("+")) {
                            waitAliasMove = new ComboAliasMove.WaitComboAliasMove.PressWaitComboAliasMove(
                                    ignoredKeysEatEvents, waitDuration);
                        }
                        else if (allExcept) {
                            waitAliasMove = new ComboAliasMove.WaitComboAliasMove.KeyWaitComboAliasMove(
                                    Set.of(ignoreContent.split("\\s+")), false,
                                    ignoredKeysEatEvents, waitDuration);
                        }
                        else {
                            waitAliasMove = new ComboAliasMove.WaitComboAliasMove.KeyWaitComboAliasMove(
                                    Set.of(ignoreContent.split("\\s+")), true,
                                    ignoredKeysEatEvents, waitDuration);
                        }
                        moveSet.add(waitAliasMove);
                    }
                    else {
                        // Regular move token
                        String moveToken = braceTokenMatcher.group(2);
                        if (isBareToken(moveToken)) {
                            moveSet.addAll(bareTokenMoves(
                                    moveToken, braceDuration, aliases));
                        }
                        else {
                            ComboAliasMove move = parseMove(moveToken, braceDuration);
                            if (move.expand()) {
                                expandAliasIntoMoves(move, aliases, moveSet);
                            }
                            else {
                                moveSet.add(move);
                            }
                        }
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
                    moveSets.add(Set.of(new ComboAliasMove.WaitComboAliasMove.KeyWaitComboAliasMove(
                            Set.of(), true, ignoredKeysEatEvents, waitDuration)));
                }
                else if (isBareToken(token)) {
                    for (ComboAliasMove move : bareTokenMoves(
                            token, defaultMoveDuration, aliases))
                        moveSets.add(Set.of(move));
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

    private static boolean isBareToken(String token) {
        return !token.startsWith("+") && !token.startsWith("-") && !token.startsWith("#");
    }

    private static List<ComboAliasMove> bareTokenMoves(String token,
                                                        ComboMoveDuration duration,
                                                        Map<String, KeyAlias> aliases) {
        boolean expand = token.startsWith("*");
        String keyName = expand ? token.substring(1) : token;
        Optionality optionality;
        if (keyName.endsWith("+")) {
            optionality = Optionality.AT_LEAST_ONE;
            keyName = keyName.substring(0, keyName.length() - 1);
        }
        else if (keyName.endsWith("?")) {
            optionality = Optionality.OPTIONAL;
            keyName = keyName.substring(0, keyName.length() - 1);
        }
        else {
            optionality = Optionality.REQUIRED;
        }
        Matcher durationMatcher = BARE_DURATION_SUFFIX_PATTERN.matcher(keyName);
        if (durationMatcher.matches() && durationMatcher.group(2) != null) {
            keyName = durationMatcher.group(1);
            duration = new ComboMoveDuration(
                    Duration.ofMillis(Integer.parseUnsignedInt(durationMatcher.group(2))),
                    durationMatcher.group(3) == null ? null : Duration.ofMillis(
                            Integer.parseUnsignedInt(durationMatcher.group(3))));
        }
        List<ComboAliasMove> moves = new ArrayList<>();
        if (expand) {
            KeyAlias alias = aliases.get(keyName);
            if (alias == null)
                throw new IllegalArgumentException(
                        "Cannot expand non-alias: " + keyName);
            for (Key key : alias.keys()) {
                moves.add(new ComboAliasMove.TapComboAliasMove(
                        key.name(), duration, optionality, keyName));
            }
        }
        else {
            moves.add(new ComboAliasMove.TapComboAliasMove(
                    keyName, duration, optionality, null));
        }
        return moves;
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
                            pm.duration(), pm.optionality(), false,
                            move.aliasOrKeyName());
            case ComboAliasMove.ReleaseComboAliasMove rm ->
                    new ComboAliasMove.ReleaseComboAliasMove(keyName,
                            rm.negated(), rm.duration(), rm.optionality(),
                            false, move.aliasOrKeyName());
            case ComboAliasMove.TapComboAliasMove tm ->
                    new ComboAliasMove.TapComboAliasMove(keyName,
                            tm.duration(), tm.optionality(),
                            tm.expandedFromAlias());
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
        String suffix = matcher.group(9);
        Optionality optionality = "+".equals(suffix) ? Optionality.AT_LEAST_ONE
                : "?".equals(suffix) ? Optionality.OPTIONAL
                : Optionality.REQUIRED;
        ComboAliasMove move;
        if (press) {
            boolean eventMustBeEaten = moveString.startsWith("+");
            move = new ComboAliasMove.PressComboAliasMove(aliasName, negated,
                    eventMustBeEaten, moveDuration, optionality, expand, null);
        }
        else
            move = new ComboAliasMove.ReleaseComboAliasMove(aliasName, negated,
                    moveDuration, optionality, expand, null);
        return move;
    }

    public ComboSequence toComboSequence(Map<String, KeyAlias> aliases,
                                         KeyResolver keyResolver) {
        List<MoveSet> resolvedMoveSets = new ArrayList<>();
        for (Set<ComboAliasMove> aliasMoveSet : moveSets) {
            // Check if the MoveSet contains a WaitComboAliasMove (ignored-key spec).
            ComboAliasMove.WaitComboAliasMove waitAliasMove = null;
            boolean hasKeyMoves = false;
            for (ComboAliasMove m : aliasMoveSet) {
                if (m instanceof ComboAliasMove.WaitComboAliasMove wam)
                    waitAliasMove = wam;
                else
                    hasKeyMoves = true;
            }
            // Pure wait MoveSet (standalone #{*}, wait, etc.)
            if (waitAliasMove != null && !hasKeyMoves) {
                WaitComboMove waitMove = resolveWaitComboMove(waitAliasMove, aliases, keyResolver);
                resolvedMoveSets.add(new WaitMoveSet(waitMove));
                continue;
            }
            // Resolve ignored-key spec if present alongside key moves.
            WaitComboMove resolvedWaitMove = null;
            if (waitAliasMove != null) {
                resolvedWaitMove = resolveWaitComboMove(waitAliasMove, aliases, keyResolver);
            }
            List<KeyComboMove> required = new ArrayList<>();
            List<KeyComboMove> optional = new ArrayList<>();
            boolean atLeastOneOptional = false;
            for (ComboAliasMove aliasMove : aliasMoveSet) {
                if (aliasMove instanceof ComboAliasMove.WaitComboAliasMove)
                    continue; // Already handled above.
                KeyOrAlias keyOrAlias;
                KeyAlias alias = aliases.get(aliasMove.aliasOrKeyName());
                if (alias != null)
                    keyOrAlias = KeyOrAlias.ofAlias(alias);
                else if (aliasMove.expandedFromAlias() != null)
                    // Expanded alias key: already resolved to active layout.
                    keyOrAlias = KeyOrAlias.ofKey(
                            Key.ofName(aliasMove.aliasOrKeyName()));
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
                    case ComboAliasMove.TapComboAliasMove tapMove ->
                            new TapComboMove(keyOrAlias,
                                    tapMove.expandedFromAlias(),
                                    aliasMove.duration());
                    case ComboAliasMove.WaitComboAliasMove wm ->
                            throw new IllegalStateException();
                };
                if (aliasMove.optionality().isOptional()) {
                    optional.add(comboMove);
                    if (aliasMove.optionality() == Optionality.AT_LEAST_ONE)
                        atLeastOneOptional = true;
                }
                else
                    required.add(comboMove);
            }
            resolvedMoveSets.add(
                    new KeyMoveSet(List.copyOf(required), List.copyOf(optional),
                            resolvedWaitMove, atLeastOneOptional));
        }
        return new ComboSequence(List.copyOf(resolvedMoveSets));
    }

    private static ComboMove.WaitComboMove resolveWaitComboMove(
            ComboAliasMove.WaitComboAliasMove waitAliasMove,
            Map<String, KeyAlias> aliases, KeyResolver keyResolver) {
        return switch (waitAliasMove) {
            case ComboAliasMove.WaitComboAliasMove.KeyWaitComboAliasMove kwam -> {
                KeySet ignoredKeySet = resolveIgnoredKeySet(kwam, aliases, keyResolver);
                yield new ComboMove.WaitComboMove.KeyWaitComboMove(
                        ignoredKeySet, kwam.ignoredKeysEatEvents(), kwam.duration());
            }
            case ComboAliasMove.WaitComboAliasMove.PressWaitComboAliasMove pwam ->
                new ComboMove.WaitComboMove.PressWaitComboMove(
                        pwam.ignoredKeysEatEvents(), pwam.duration());
            case ComboAliasMove.WaitComboAliasMove.ReleaseWaitComboAliasMove rwam ->
                new ComboMove.WaitComboMove.ReleaseWaitComboMove(
                        rwam.ignoredKeysEatEvents(), rwam.duration());
        };
    }

    private static KeySet resolveIgnoredKeySet(
            ComboAliasMove.WaitComboAliasMove.KeyWaitComboAliasMove waitAliasMove,
            Map<String, KeyAlias> aliases, KeyResolver keyResolver) {
        Set<Key> resolvedKeys = new HashSet<>();
        for (String keyAliasOrKeyName : waitAliasMove.keyAliasOrKeyNames()) {
            KeyAlias waitAlias = aliases.get(keyAliasOrKeyName);
            if (waitAlias != null)
                resolvedKeys.addAll(waitAlias.keys());
            else
                resolvedKeys.add(keyResolver.resolve(keyAliasOrKeyName));
        }
        return waitAliasMove.listedKeysAreIgnored() ?
                new KeySet.Only(Set.copyOf(resolvedKeys)) :
                new KeySet.AllExcept(Set.copyOf(resolvedKeys));
    }

}
