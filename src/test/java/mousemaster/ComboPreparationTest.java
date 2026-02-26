package mousemaster;

import mousemaster.ComboMove.KeyComboMove;
import mousemaster.ComboMove.PressComboMove;
import mousemaster.ComboMove.ReleaseComboMove;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import mousemaster.MoveSet.KeyMoveSet;

import static org.junit.jupiter.api.Assertions.*;

class ComboPreparationTest {

    static final Key a = Key.ofName("a");
    static final Key b = Key.ofName("b");
    static final Key c = Key.ofName("c");
    static final Key d = Key.ofName("d");
    static final Key x = Key.ofName("x");
    static final Key y = Key.ofName("y");

    static final ComboMoveDuration defaultDuration = new ComboMoveDuration(Duration.ZERO, null);

    static KeyEvent press(Key key, Instant time) {
        return new KeyEvent.PressKeyEvent(time, key);
    }

    static KeyEvent release(Key key, Instant time) {
        return new KeyEvent.ReleaseKeyEvent(time, key);
    }

    static Instant t(long millis) {
        return Instant.EPOCH.plusMillis(millis);
    }

    /** Press move with eventMustBeEaten=true (the + prefix). */
    static KeyComboMove pressMove(Key key) {
        return new PressComboMove(KeyOrAlias.ofKey(key), false, true, defaultDuration);
    }

    /** Press move with eventMustBeEaten=true and custom duration. */
    static KeyComboMove pressMove(Key key, ComboMoveDuration duration) {
        return new PressComboMove(KeyOrAlias.ofKey(key), false, true, duration);
    }

    /** Press move with eventMustBeEaten=false (the # prefix). */
    static KeyComboMove hashPressMove(Key key) {
        return new PressComboMove(KeyOrAlias.ofKey(key), false, false, defaultDuration);
    }

    /** Release move (the - prefix). */
    static KeyComboMove releaseMove(Key key) {
        return new ReleaseComboMove(KeyOrAlias.ofKey(key), false, defaultDuration);
    }

    static KeyComboMove releaseMove(Key key, ComboMoveDuration duration) {
        return new ReleaseComboMove(KeyOrAlias.ofKey(key), false, duration);
    }

    static ComboPreparation prep(KeyEvent... events) {
        return new ComboPreparation(new ArrayList<>(Arrays.asList(events)));
    }

    static ComboSequence seq(MoveSet... moveSets) {
        return new ComboSequence(List.of(moveSets));
    }

    static MoveSet required(KeyComboMove... moves) {
        return new KeyMoveSet(List.of(moves), List.of());
    }

    static MoveSet withOptional(List<KeyComboMove> requiredMoves,
                                List<KeyComboMove> optionalMoves) {
        return new KeyMoveSet(requiredMoves, optionalMoves);
    }

    @Test
    void emptySequence_returnsComplete() {
        ComboSequenceMatch match = prep(press(a, t(0))).match(seq());
        assertTrue(match.complete());
        assertTrue(match.matchedKeyMoves().isEmpty());
    }

    @Test
    void emptyEvents_nonEmptySequence_returnsNoMatch() {
        ComboSequenceMatch match = prep().match(seq(required(pressMove(a))));
        assertFalse(match.hasMatch());
        assertFalse(match.complete());
    }

    @Test
    void bothEmpty_returnsComplete() {
        ComboSequenceMatch match = prep().match(seq());
        assertTrue(match.complete());
        assertTrue(match.matchedKeyMoves().isEmpty());
    }

    // 2. Single required move

    @Test
    void singlePress_matchingKey_complete() {
        ComboSequenceMatch match = prep(press(a, t(0)))
                .match(seq(required(pressMove(a))));
        assertTrue(match.complete());
        assertEquals(1, match.matchedKeyMoves().size());
        assertEquals(a, match.matchedKeyMoves().getFirst().key());
        assertTrue(match.matchedKeyMoves().getFirst().isPress());
    }

    @Test
    void singlePress_wrongKey_noMatch() {
        ComboSequenceMatch match = prep(press(b, t(0)))
                .match(seq(required(pressMove(a))));
        assertFalse(match.hasMatch());
    }

    @Test
    void singlePress_wrongDirection_noMatch() {
        // Event is release, move expects press.
        ComboSequenceMatch match = prep(release(a, t(0)))
                .match(seq(required(pressMove(a))));
        assertFalse(match.hasMatch());
    }

    // 3. Sequential required moves (+a then +b)

    @Test
    void twoSequentialMoves_correctOrder_complete() {
        ComboSequenceMatch match = prep(press(a, t(0)), press(b, t(10)))
                .match(seq(required(pressMove(a)), required(pressMove(b))));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void twoSequentialMoves_wrongOrder_partialSuffixMatch() {
        // Events [+b, +a] with sequence [+a, +b]: can't complete-match because
        // order between MoveSets matters. But the suffix [+a] matches the first
        // MoveSet, so it returns a partial match.
        ComboSequenceMatch match = prep(press(b, t(0)), press(a, t(10)))
                .match(seq(required(pressMove(a)), required(pressMove(b))));
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
        assertEquals(1, match.matchedKeyMoves().size());
        assertEquals(a, match.matchedKeyMoves().getFirst().key());
    }

    @Test
    void twoSequentialMoves_onlyFirst_partial() {
        ComboSequenceMatch match = prep(press(a, t(0)))
                .match(seq(required(pressMove(a)), required(pressMove(b))));
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
        assertEquals(1, match.matchedKeyMoves().size());
        assertEquals(a, match.matchedKeyMoves().getFirst().key());
    }

    // 4. Suffix matching

    @Test
    void suffixMatch_extraPrefixEvents_complete() {
        // Events [+x, +a, +b], sequence [+a, +b] — should match the last 2 events.
        ComboSequenceMatch match = prep(
                press(x, t(0)), press(a, t(10)), press(b, t(20)))
                .match(seq(required(pressMove(a)), required(pressMove(b))));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void suffixMatch_multipleExtraPrefixEvents_complete() {
        // Events [+x, +y, +a], sequence [+a].
        ComboSequenceMatch match = prep(
                press(x, t(0)), press(y, t(10)), press(a, t(20)))
                .match(seq(required(pressMove(a))));
        assertTrue(match.complete());
        assertEquals(1, match.matchedKeyMoves().size());
    }

    // 5. Any-order within MoveSet ({+a +b})

    @Test
    void anyOrderInMoveSet_normalOrder_complete() {
        // MoveSet {+a +b}: events [+a, +b].
        ComboSequenceMatch match = prep(press(a, t(0)), press(b, t(10)))
                .match(seq(required(pressMove(a), pressMove(b))));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void anyOrderInMoveSet_reversedOrder_complete() {
        // MoveSet {+a +b}: events [+b, +a].
        ComboSequenceMatch match = prep(press(b, t(0)), press(a, t(10)))
                .match(seq(required(pressMove(a), pressMove(b))));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    // 6. Optional moves ({+a #b?})

    @Test
    void optionalSkipped_requiredOnly_complete() {
        // Sequence [{+a #b?}]: events [+a] — optional #b skipped.
        MoveSet moveSet = withOptional(
                List.of(pressMove(a)), List.of(hashPressMove(b)));
        ComboSequenceMatch match = prep(press(a, t(0))).match(seq(moveSet));
        assertTrue(match.complete());
        assertEquals(1, match.matchedKeyMoves().size());
    }

    @Test
    void optionalIncluded_complete() {
        // Sequence [{+a #b?}]: events [+a, +b] — optional included.
        MoveSet moveSet = withOptional(
                List.of(pressMove(a)), List.of(hashPressMove(b)));
        ComboSequenceMatch match = prep(press(a, t(0)), press(b, t(10)))
                .match(seq(moveSet));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void optionalIncluded_reversedOrder_complete() {
        // Sequence [{+a #b?}]: events [+b, +a] — reversed order, both match.
        MoveSet moveSet = withOptional(
                List.of(pressMove(a)), List.of(hashPressMove(b)));
        ComboSequenceMatch match = prep(press(b, t(0)), press(a, t(10)))
                .match(seq(moveSet));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void optionalOnly_requiredMissing_noMatch() {
        // Sequence [{+a #b?}]: events [+b] — required +a missing.
        MoveSet moveSet = withOptional(
                List.of(pressMove(a)), List.of(hashPressMove(b)));
        ComboSequenceMatch match = prep(press(b, t(0))).match(seq(moveSet));
        assertFalse(match.hasMatch());
    }

    // 7. All-optional MoveSet (#a? as standalone)

    @Test
    void allOptionalMoveSet_matchingEvent_complete() {
        // Sequence [{#a?}]: events [+a] → complete.
        MoveSet moveSet = withOptional(List.of(), List.of(hashPressMove(a)));
        ComboSequenceMatch match = prep(press(a, t(0))).match(seq(moveSet));
        assertTrue(match.complete());
        assertEquals(1, match.matchedKeyMoves().size());
    }

    @Test
    void allOptionalMoveSet_noMatchingEvent_complete() {
        // Sequence [{#a?}]: events [+x] → still complete because minMoveCount=0.
        MoveSet moveSet = withOptional(List.of(), List.of(hashPressMove(a)));
        ComboSequenceMatch match = prep(press(x, t(0))).match(seq(moveSet));
        assertTrue(match.complete());
        assertEquals(0, match.matchedKeyMoves().size());
    }

    // 8. Greedy matching

    @Test
    void greedy_optionalSkippedWhenNeeded() {
        // Sequence [{+a #b?} +c]: events [+a, +c].
        // First MoveSet takes +a (skips optional #b), second takes +c.
        MoveSet first = withOptional(
                List.of(pressMove(a)), List.of(hashPressMove(b)));
        MoveSet second = required(pressMove(c));
        ComboSequenceMatch match = prep(press(a, t(0)), press(c, t(10)))
                .match(seq(first, second));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void greedy_optionalIncludedWhenPossible() {
        // Sequence [{+a #b?} +c]: events [+b, +a, +c].
        // First MoveSet greedily takes +b and +a, second takes +c.
        MoveSet first = withOptional(
                List.of(pressMove(a)), List.of(hashPressMove(b)));
        MoveSet second = required(pressMove(c));
        ComboSequenceMatch match = prep(
                press(b, t(0)), press(a, t(10)), press(c, t(20)))
                .match(seq(first, second));
        assertTrue(match.complete());
        assertEquals(3, match.matchedKeyMoves().size());
    }

    // 9. Duration constraints

    @Test
    void duration_withinMax_matches() {
        // Max 100ms between moves, events 50ms apart.
        ComboMoveDuration dur = new ComboMoveDuration(Duration.ZERO, Duration.ofMillis(100));
        ComboSequenceMatch match = prep(press(a, t(0)), press(b, t(50)))
                .match(seq(required(pressMove(a, dur)), required(pressMove(b))));
        assertTrue(match.complete());
    }

    @Test
    void duration_exceedsMax_noMatch() {
        // Max 100ms, events 200ms apart → no match.
        ComboMoveDuration dur = new ComboMoveDuration(Duration.ZERO, Duration.ofMillis(100));
        ComboSequenceMatch match = prep(press(a, t(0)), press(b, t(200)))
                .match(seq(required(pressMove(a, dur)), required(pressMove(b))));
        assertFalse(match.hasMatch());
    }

    @Test
    void duration_firstEventInRegion_noDurationConstraint() {
        // Even if the event before the region is far away, the first event in
        // the region has no duration constraint.
        // Events: [+x at t=0, +a at t=5000], sequence [+a].
        // +a is the first event in the matched region; no prior move's duration
        // applies to it.
        ComboSequenceMatch match = prep(press(x, t(0)), press(a, t(5000)))
                .match(seq(required(pressMove(a))));
        assertTrue(match.complete());
    }

    @Test
    void duration_betweenMoveSets() {
        // Duration on last move of first MoveSet constrains gap to first event
        // of second MoveSet.
        ComboMoveDuration dur = new ComboMoveDuration(Duration.ZERO, Duration.ofMillis(50));
        ComboSequenceMatch match = prep(press(a, t(0)), press(b, t(100)))
                .match(seq(required(pressMove(a, dur)), required(pressMove(b))));
        // 100ms > 50ms max → should not match.
        assertFalse(match.hasMatch());
    }

    @Test
    void duration_minNotSatisfied_noMatch() {
        // Min 100ms between moves, events only 10ms apart → too fast.
        ComboMoveDuration dur = new ComboMoveDuration(Duration.ofMillis(100), null);
        ComboSequenceMatch match = prep(press(a, t(0)), press(b, t(10)))
                .match(seq(required(pressMove(a, dur)), required(pressMove(b))));
        assertFalse(match.hasMatch());
    }

    @Test
    void duration_minSatisfied_matches() {
        // Min 100ms, events 150ms apart → satisfies.
        ComboMoveDuration dur = new ComboMoveDuration(Duration.ofMillis(100), null);
        ComboSequenceMatch match = prep(press(a, t(0)), press(b, t(150)))
                .match(seq(required(pressMove(a, dur)), required(pressMove(b))));
        assertTrue(match.complete());
    }

    // 10. Multiple MoveSets with optionals

    @Test
    void multiMoveSets_bothOptionalsSkipped_complete() {
        // Sequence [{+a #b?} {+c #d?}]: events [+a, +c].
        MoveSet first = withOptional(
                List.of(pressMove(a)), List.of(hashPressMove(b)));
        MoveSet second = withOptional(
                List.of(pressMove(c)), List.of(hashPressMove(d)));
        ComboSequenceMatch match = prep(press(a, t(0)), press(c, t(10)))
                .match(seq(first, second));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void multiMoveSets_firstOptionalIncluded_complete() {
        // Sequence [{+a #b?} {+c #d?}]: events [+a, +b, +c].
        MoveSet first = withOptional(
                List.of(pressMove(a)), List.of(hashPressMove(b)));
        MoveSet second = withOptional(
                List.of(pressMove(c)), List.of(hashPressMove(d)));
        ComboSequenceMatch match = prep(
                press(a, t(0)), press(b, t(10)), press(c, t(20)))
                .match(seq(first, second));
        assertTrue(match.complete());
        assertEquals(3, match.matchedKeyMoves().size());
    }

    // 11. Release moves

    @Test
    void singleRelease_complete() {
        ComboSequenceMatch match = prep(release(a, t(0)))
                .match(seq(required(releaseMove(a))));
        assertTrue(match.complete());
        assertEquals(1, match.matchedKeyMoves().size());
        assertTrue(match.matchedKeyMoves().getFirst().isRelease());
    }

    @Test
    void pressAndRelease_complete() {
        ComboSequenceMatch match = prep(press(a, t(0)), release(a, t(50)))
                .match(seq(required(pressMove(a)), required(releaseMove(a))));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    // 12. Partial match preference

    @Test
    void partialMatch_longestPrefix() {
        // Sequence [+a, +b, +c]: events [+a, +b] → partial with 2 matched moves.
        ComboSequenceMatch match = prep(press(a, t(0)), press(b, t(10)))
                .match(seq(
                        required(pressMove(a)),
                        required(pressMove(b)),
                        required(pressMove(c))));
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void partialMatch_singleMoveOfThree() {
        // Sequence [+a, +b, +c]: events [+a] → partial with 1 matched move.
        ComboSequenceMatch match = prep(press(a, t(0)))
                .match(seq(
                        required(pressMove(a)),
                        required(pressMove(b)),
                        required(pressMove(c))));
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
        assertEquals(1, match.matchedKeyMoves().size());
    }

    @Test
    void partialMatch_preferLongerOverShorter() {
        // With events [+a, +b, +x], sequence [+a, +b, +c]:
        // Suffix matching means only the last N events are considered.
        // The suffix [+x] doesn't match +a, and suffix [+b, +x] doesn't
        // match [+a, +b]. So no match is possible.
        ComboSequenceMatch match = prep(
                press(a, t(0)), press(b, t(10)), press(x, t(20)))
                .match(seq(
                        required(pressMove(a)),
                        required(pressMove(b)),
                        required(pressMove(c))));
        assertFalse(match.hasMatch());
    }

    @Test
    void partialMatch_suffixAligns() {
        // Events [+x, +a, +b], sequence [+a, +b, +c]:
        // Suffix [+a, +b] matches the first 2 MoveSets → partial with 2 moves.
        ComboSequenceMatch match = prep(
                press(x, t(0)), press(a, t(10)), press(b, t(20)))
                .match(seq(
                        required(pressMove(a)),
                        required(pressMove(b)),
                        required(pressMove(c))));
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    // --- Alias expansion tests ---

    static final KeyResolver identityKeyResolver = new KeyResolver(
            new KeyboardLayout("test", "test", "test", "test", List.of()),
            new KeyboardLayout("test", "test", "test", "test", List.of()));

    static ComboSequence parseCombo(String comboString,
                                    Map<String, KeyAlias> aliases) {
        ExpandableSequence expandable = ExpandableSequence.parseSequence(
                comboString, defaultDuration, aliases);
        return expandable.toComboSequence(aliases, identityKeyResolver);
    }

    static final Map<String, KeyAlias> testAliases = Map.of(
            "testkeys", new KeyAlias("testkeys", List.of(a, b, c)));

    @Test
    void expandedAlias_inBraces_anyOrder_complete() {
        // {+*testkeys} with testkeys=a b c → {+a +b +c} any-order
        ComboSequence combo = parseCombo("{+*testkeys}", testAliases);
        ComboSequenceMatch match = prep(
                press(a, t(0)), press(b, t(10)), press(c, t(20)))
                .match(combo);
        assertTrue(match.complete());
        assertEquals(3, match.matchedKeyMoves().size());
    }

    @Test
    void expandedAlias_inBraces_anyOrder_reversed_complete() {
        ComboSequence combo = parseCombo("{+*testkeys}", testAliases);
        ComboSequenceMatch match = prep(
                press(c, t(0)), press(b, t(10)), press(a, t(20)))
                .match(combo);
        assertTrue(match.complete());
        assertEquals(3, match.matchedKeyMoves().size());
    }

    @Test
    void expandedAlias_inBraces_missingKey_noComplete() {
        ComboSequence combo = parseCombo("{+*testkeys}", testAliases);
        ComboSequenceMatch match = prep(
                press(a, t(0)), press(b, t(10)))
                .match(combo);
        assertFalse(match.complete());
    }

    @Test
    void expandedAlias_sequential_strictOrder_complete() {
        // +*testkeys (no braces) → +a +b +c sequential
        ComboSequence combo = parseCombo("+*testkeys", testAliases);
        ComboSequenceMatch match = prep(
                press(a, t(0)), press(b, t(10)), press(c, t(20)))
                .match(combo);
        assertTrue(match.complete());
        assertEquals(3, match.matchedKeyMoves().size());
    }

    @Test
    void expandedAlias_sequential_wrongOrder_noComplete() {
        ComboSequence combo = parseCombo("+*testkeys", testAliases);
        ComboSequenceMatch match = prep(
                press(c, t(0)), press(b, t(10)), press(a, t(20)))
                .match(combo);
        assertFalse(match.complete());
    }

    @Test
    void expandedAlias_optional_allSkipped_complete() {
        // {+*testkeys?} → all optional, no events needed
        ComboSequence combo = parseCombo("{+*testkeys?}", testAliases);
        ComboSequenceMatch match = prep(press(x, t(0))).match(combo);
        assertTrue(match.complete());
        assertEquals(0, match.matchedKeyMoves().size());
    }

    @Test
    void expandedAlias_optional_somePresent_complete() {
        ComboSequence combo = parseCombo("{+*testkeys?}", testAliases);
        ComboSequenceMatch match = prep(press(a, t(0))).match(combo);
        assertTrue(match.complete());
        assertEquals(1, match.matchedKeyMoves().size());
    }

    @Test
    void nonExpandedAlias_matchesOneKey() {
        // +testkeys (no *) with event [+a] → complete (alias matches any key)
        ComboSequence combo = parseCombo("+testkeys", testAliases);
        ComboSequenceMatch match = prep(press(a, t(0))).match(combo);
        assertTrue(match.complete());
        assertEquals(1, match.matchedKeyMoves().size());
    }

    // --- KeyMoveSet with ignored keys ---

    @Test
    void ignoredKeys_interleavedIgnoredEvent_complete() {
        // {-a -b #{*}}: events [-a, +x, -b] → +x is ignored, -a and -b match.
        ComboSequenceMatch match = prep(
                release(a, t(0)), press(x, t(10)), release(b, t(20)))
                .match(parseCombo("{-a -b #{*}}", Map.of()));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void ignoredKeys_noInterleavedEvents_complete() {
        // {-a -b #{*}}: events [-a, -b] → no ignored events needed, still matches.
        ComboSequenceMatch match = prep(
                release(a, t(0)), release(b, t(10)))
                .match(parseCombo("{-a -b #{*}}", Map.of()));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void ignoredKeys_multipleInterleavedEvents_complete() {
        // {-a -b #{*}}: events [-a, +x, +y, -b] → +x and +y are ignored.
        ComboSequenceMatch match = prep(
                release(a, t(0)), press(x, t(10)), press(y, t(20)),
                release(b, t(30)))
                .match(parseCombo("{-a -b #{*}}", Map.of()));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void ignoredKeys_specificKeysOnly_nonIgnoredFails() {
        // {-a -b #{x}}: events [-a, +y, -b] → +y is NOT in ignored set, no match.
        ComboSequenceMatch match = prep(
                release(a, t(0)), press(y, t(10)), release(b, t(20)))
                .match(parseCombo("{-a -b #{x}}", Map.of()));
        assertFalse(match.complete());
    }

    @Test
    void ignoredKeys_specificKeysOnly_ignoredSucceeds() {
        // {-a -b #{x}}: events [-a, +x, -b] → +x is in ignored set, match.
        ComboSequenceMatch match = prep(
                release(a, t(0)), press(x, t(10)), release(b, t(20)))
                .match(parseCombo("{-a -b #{x}}", Map.of()));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void ignoredKeys_withFollowingMoveSet_complete() {
        // {-a -b #{*}} +c: events [-a, +x, -b, +c].
        ComboSequenceMatch match = prep(
                release(a, t(0)), press(x, t(10)),
                release(b, t(20)), press(c, t(30)))
                .match(parseCombo("{-a -b #{*}} +c", Map.of()));
        assertTrue(match.complete());
        assertEquals(3, match.matchedKeyMoves().size());
    }

    @Test
    void ignoredKeys_trailingAbsorbedEvent_noMatch() {
        // {-a #{*}}: events [-a, +x] → -a matches, but +x is trailing absorbed
        // (not interleaved). Absorbed events must be strictly between key moves.
        // Suffix matching can only try [-a, +x] (rejected) or [+x] (doesn't
        // match -a), so no match.
        ComboSequenceMatch match = prep(
                release(a, t(0)), press(x, t(10)))
                .match(parseCombo("{-a #{*}}", Map.of()));
        assertFalse(match.hasMatch());
    }

    @Test
    void ignoredKeys_aliasExpandedIntoMoves_complete() {
        // {-*ab #{*}} with ab=a,b → {-a -b #{*}}: events [-a, +x, -b].
        Map<String, KeyAlias> aliases =
                Map.of("ab", new KeyAlias("ab", List.of(a, b)));
        ComboSequenceMatch match = prep(
                release(a, t(0)), press(x, t(10)), release(b, t(20)))
                .match(parseCombo("{-*ab #{*}}", aliases));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void ignoredKeys_aliasInIgnoredKeySet_trailingAbsorbed_noMatch() {
        // {-a #{ab}} with ab=a,b: events [-a, +b] → +b is in ignored set but
        // trailing (not interleaved). No match.
        Map<String, KeyAlias> aliases =
                Map.of("ab", new KeyAlias("ab", List.of(a, b)));
        ComboSequenceMatch match = prep(
                release(a, t(0)), press(b, t(10)))
                .match(parseCombo("{-a #{ab}}", aliases));
        assertFalse(match.hasMatch());
    }

    @Test
    void ignoredKeys_aliasInIgnoredKeySet_nonAliasKeyFails() {
        // {-a #{ab}} with ab=a,b: events [-a, +x] → +x is NOT in ignored set.
        Map<String, KeyAlias> aliases =
                Map.of("ab", new KeyAlias("ab", List.of(a, b)));
        ComboSequenceMatch match = prep(
                release(a, t(0)), press(x, t(10)))
                .match(parseCombo("{-a #{ab}}", aliases));
        assertFalse(match.complete());
    }

    // --- Partial matching within a MoveSet ---

    @Test
    void partialMoveSet_basicPartial_hasMatch() {
        // {+a +b} with events [+a] → partial match (hasMatch=true, complete=false).
        ComboSequenceMatch match = prep(press(a, t(0)))
                .match(parseCombo("{+a +b}", Map.of()));
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
        assertEquals(1, match.matchedKeyMoves().size());
        assertEquals(a, match.matchedKeyMoves().getFirst().key());
        assertEquals(0, match.matchedMoveSetCount());
    }

    @Test
    void partialMoveSet_fullMatchPriority() {
        // {+a +b} with events [+a, +b] → full match (complete=true).
        ComboSequenceMatch match = prep(press(a, t(0)), press(b, t(10)))
                .match(parseCombo("{+a +b}", Map.of()));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
        assertEquals(1, match.matchedMoveSetCount());
    }

    @Test
    void partialMoveSet_partialSecondMoveSet() {
        // +a {+b +c} with events [+a, +b] → first MoveSet fully matched,
        // second partially matched (hasMatch=true, complete=false, matchedMoveSetCount=1).
        ComboSequenceMatch match = prep(press(a, t(0)), press(b, t(10)))
                .match(parseCombo("+a {+b +c}", Map.of()));
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
        assertEquals(1, match.matchedMoveSetCount());
    }

    @Test
    void partialMoveSet_noFalseMatch() {
        // {+a +b} with events [+c] → no match (neither a nor b).
        ComboSequenceMatch match = prep(press(c, t(0)))
                .match(parseCombo("{+a +b}", Map.of()));
        assertFalse(match.hasMatch());
    }

    @Test
    void partialMoveSet_withAbsorbing() {
        // {+a +b #{*}} with events [+a] → partial match.
        ComboSequenceMatch match = prep(press(a, t(0)))
                .match(parseCombo("{+a +b #{*}}", Map.of()));
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
    }

    @Test
    void partialMoveSet_withOptionalMatched() {
        // {+a +b #c?} with events [+c] → partial match (optional matched).
        ComboSequenceMatch match = prep(press(c, t(0)))
                .match(parseCombo("{+a +b #c?}", Map.of()));
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
        assertEquals(1, match.matchedKeyMoves().size());
        assertEquals(c, match.matchedKeyMoves().getFirst().key());
    }

    @Test
    void partialMoveSet_withAbsorbedEvent() {
        // {+a -a +b -b +{d}} with events [+a, -a, +d]:
        // +a and -a match key moves, +d is absorbed by +{d}.
        // Partial match (hasMatch=true, complete=false).
        ComboSequenceMatch match = prep(
                press(a, t(0)), release(a, t(10)), press(d, t(20)))
                .match(parseCombo("{+a -a +b -b +{d}}", Map.of()));
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
        assertEquals(a, match.matchedKeyMoves().get(0).key());
        assertEquals(a, match.matchedKeyMoves().get(1).key());
    }

    // --- Leading/trailing ignored keys ---

    @Test
    void ignoredKeys_leadingAbsorbed_complete() {
        // {+a #{x}}: events [+x, +a] → +x is leading absorbed, +a matches.
        // Leading ignored is always allowed.
        ComboSequenceMatch match = prep(
                press(x, t(0)), press(a, t(10)))
                .match(parseCombo("{+a #{x}}", Map.of()));
        assertTrue(match.complete());
        assertEquals(1, match.matchedKeyMoves().size());
        assertEquals(a, match.matchedKeyMoves().getFirst().key());
    }

    @Test
    void ignoredKeys_trailingAbsorbedWithFollowingMoveSet_complete() {
        // {+a #{x}} +b: events [+a, +x, +b] → +x is trailing absorbed in
        // first MoveSet (allowed because there's a following MoveSet).
        ComboSequenceMatch match = prep(
                press(a, t(0)), press(x, t(10)), press(b, t(20)))
                .match(parseCombo("{+a #{x}} +b", Map.of()));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void ignoredKeys_trailingAbsorbedOnLastMoveSet_noMatch() {
        // {+a #{x}} (last MoveSet): events [+a, +x] → +x is trailing absorbed
        // but not allowed on last MoveSet. No match.
        ComboSequenceMatch match = prep(
                press(a, t(0)), press(x, t(10)))
                .match(parseCombo("{+a #{x}}", Map.of()));
        assertFalse(match.hasMatch());
    }

    @Test
    void ignoredKeys_adjacentMoveSetsUnion_complete() {
        // {+a #{x}} {+b #{y}}: events [+a, +y, +b].
        // +y between +a and +b is absorbed by second MoveSet's leading.
        ComboSequenceMatch match = prep(
                press(a, t(0)), press(y, t(10)), press(b, t(20)))
                .match(parseCombo("{+a #{x}} {+b #{y}}", Map.of()));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void ignoredKeys_trailingAbsorbedDuringPartialMatch_hasMatch() {
        // {+a #{x}} +b: events [+a, +x] → partial match of just the first
        // MoveSet. +x is trailing absorbed, allowed because the full sequence
        // has a following MoveSet (+b).
        ComboSequenceMatch match = prep(
                press(a, t(0)), press(x, t(10)))
                .match(parseCombo("{+a #{x}} +b", Map.of()));
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
        assertEquals(1, match.matchedKeyMoves().size());
        assertEquals(a, match.matchedKeyMoves().getFirst().key());
    }

    // --- Tap move tests ---

    static ComboMove.TapComboMove tapMove(Key key) {
        return new ComboMove.TapComboMove(KeyOrAlias.ofKey(key), null, defaultDuration);
    }

    @Test
    void tap_singleKey_complete() {
        // {a} (tap) with events [+a, -a] → complete.
        ComboSequenceMatch match = prep(press(a, t(0)), release(a, t(50)))
                .match(seq(required(tapMove(a))));
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
        assertTrue(match.matchedKeyMoves().get(0).isPress());
        assertTrue(match.matchedKeyMoves().get(1).isRelease());
        assertEquals(a, match.matchedKeyMoves().get(0).key());
        assertEquals(a, match.matchedKeyMoves().get(1).key());
    }

    @Test
    void tap_releaseBeforePress_noMatch() {
        // {a} (tap) with events [-a, +a] → no match (release before press).
        ComboSequenceMatch match = prep(release(a, t(0)), press(a, t(50)))
                .match(seq(required(tapMove(a))));
        assertFalse(match.complete());
    }

    @Test
    void tap_danglingPress_noMatch() {
        // {a} (tap) with events [+a] → no match (tap incomplete).
        ComboSequenceMatch match = prep(press(a, t(0)))
                .match(seq(required(tapMove(a))));
        assertFalse(match.complete());
    }

    @Test
    void tap_multiTapInterleaved_complete() {
        // {a b} (two taps, any-order) with events [+a, +b, -b, -a] → complete.
        ComboSequenceMatch match = prep(
                press(a, t(0)), press(b, t(10)),
                release(b, t(20)), release(a, t(30)))
                .match(seq(required(tapMove(a), tapMove(b))));
        assertTrue(match.complete());
        assertEquals(4, match.matchedKeyMoves().size());
    }

    @Test
    void tap_plusPress_mixed_complete() {
        // {a +c} (tap(a) + press(c)) with events [+a, +c, -a] → complete.
        ComboSequenceMatch match = prep(
                press(a, t(0)), press(c, t(10)), release(a, t(20)))
                .match(seq(required(tapMove(a), pressMove(c))));
        assertTrue(match.complete());
        assertEquals(3, match.matchedKeyMoves().size());
    }

    @Test
    void tap_optional_skipped_complete() {
        // {a? +c} with events [+c] → tap(a) skipped, press(c) matches.
        MoveSet moveSet = withOptional(
                List.of(pressMove(c)), List.of(tapMove(a)));
        ComboSequenceMatch match = prep(press(c, t(0))).match(seq(moveSet));
        assertTrue(match.complete());
        assertEquals(1, match.matchedKeyMoves().size());
    }

    @Test
    void tap_optional_included_complete() {
        // {a? +c} with events [+a, +c, -a] → tap(a) included, press(c) matches.
        MoveSet moveSet = withOptional(
                List.of(pressMove(c)), List.of(tapMove(a)));
        ComboSequenceMatch match = prep(
                press(a, t(0)), press(c, t(10)), release(a, t(20)))
                .match(seq(moveSet));
        assertTrue(match.complete());
        assertEquals(3, match.matchedKeyMoves().size());
    }

    @Test
    void tap_parsedFromBareKey_complete() {
        // "a" parses as tap(a). Events [+a, -a] → complete.
        ComboSequence combo = parseCombo("a", Map.of());
        ComboSequenceMatch match = prep(press(a, t(0)), release(a, t(50)))
                .match(combo);
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void tap_parsedFromBareKey_pressOnly_noComplete() {
        // "a" parses as tap(a). Events [+a] → not complete (dangling press).
        ComboSequence combo = parseCombo("a", Map.of());
        ComboSequenceMatch match = prep(press(a, t(0))).match(combo);
        // Partial match (press matched) but not complete.
        assertFalse(match.complete());
    }

    @Test
    void tap_sequential_twoTaps_complete() {
        // "a b" → tap(a) tap(b) sequential. Events [+a, -a, +b, -b] → complete.
        ComboSequence combo = parseCombo("a b", Map.of());
        ComboSequenceMatch match = prep(
                press(a, t(0)), release(a, t(10)),
                press(b, t(20)), release(b, t(30)))
                .match(combo);
        assertTrue(match.complete());
        assertEquals(4, match.matchedKeyMoves().size());
    }

    @Test
    void tap_expandedAlias_optional_complete() {
        // {*alias1? +c} with alias1={a,b}: events [+a, +b, +c, -b, -a] → complete.
        Map<String, KeyAlias> aliases = Map.of("alias1",
                new KeyAlias("alias1", List.of(a, b)));
        ComboSequence combo = parseCombo("{*alias1? +c}", aliases);
        ComboSequenceMatch match = prep(
                press(a, t(0)), press(b, t(10)), press(c, t(20)),
                release(b, t(30)), release(a, t(40)))
                .match(combo);
        assertTrue(match.complete());
        assertEquals(5, match.matchedKeyMoves().size());
    }

    @Test
    void tap_expandedAlias_optional_noneMatched_complete() {
        // {*alias1? +c} with alias1={a,b}: events [+c] → all taps skipped, +c matches.
        Map<String, KeyAlias> aliases = Map.of("alias1",
                new KeyAlias("alias1", List.of(a, b)));
        ComboSequence combo = parseCombo("{*alias1? +c}", aliases);
        ComboSequenceMatch match = prep(press(c, t(0))).match(combo);
        assertTrue(match.complete());
        assertEquals(1, match.matchedKeyMoves().size());
    }
}
