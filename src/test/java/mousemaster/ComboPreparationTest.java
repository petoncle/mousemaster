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
    void tap_releaseBeforePress_partialMatch() {
        // {a} (tap) with events [-a, +a] → partial match (suffix [+a] is dangling press).
        ComboSequenceMatch match = prep(release(a, t(0)), press(a, t(50)))
                .match(seq(required(tapMove(a))));
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
    }

    @Test
    void tap_danglingPress_hasPartialMatch() {
        // {a} (tap) with events [+a] → partial match (press matched, tap incomplete).
        ComboSequenceMatch match = prep(press(a, t(0)))
                .match(seq(required(tapMove(a))));
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
        assertEquals(1, match.matchedKeyMoves().size());
        assertTrue(match.matchedKeyMoves().getFirst().isPress());
        assertEquals(a, match.matchedKeyMoves().getFirst().key());
    }

    @Test
    void tap_multiTap_danglingPress_hasPartialMatch() {
        // {a b} (two required taps) with events [+a] → partial match.
        ComboSequenceMatch match = prep(press(a, t(0)))
                .match(seq(required(tapMove(a), tapMove(b))));
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
    }

    @Test
    void tap_multiTap_completePlusDangling_hasPartialMatch() {
        // {a b} (two required taps) with events [+a, -a, +b] → partial match
        // (one complete tap + one dangling press).
        ComboSequenceMatch match = prep(
                press(a, t(0)), release(a, t(10)), press(b, t(20)))
                .match(seq(required(tapMove(a), tapMove(b))));
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
    }

    @Test
    void tap_danglingPress_wrongKey_noMatch() {
        // {a} (tap) with events [+b] → no match (wrong key).
        ComboSequenceMatch match = prep(press(b, t(0)))
                .match(seq(required(tapMove(a))));
        assertFalse(match.hasMatch());
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
    void tap_optional_danglingPress_hasPartialMatch() {
        // {a?} (all-optional tap) with events [+a] → partial match (dangling press).
        MoveSet moveSet = withOptional(List.of(), List.of(tapMove(a)));
        ComboSequenceMatch match = prep(press(a, t(0))).match(seq(moveSet));
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
    }

    @Test
    void tap_optional_danglingPress_thenUnrelatedEvent_absorbedByWait_noMatch() {
        // {a?} #{*} +c with events [+a, +b]: +a starts optional tap(a), but +b
        // arrives before -a. The wait #{*} should NOT absorb +b because the
        // tap in the previous move set is incomplete (dangling press).
        ComboSequence combo = parseCombo("{a?} #{*} +c", Map.of());
        ComboSequenceMatch match = prep(press(a, t(0)), press(b, t(50)))
                .match(combo);
        assertFalse(match.hasMatch());
    }

    @Test
    void tap_optional_danglingPress_unmatchedEventsInRegion_shouldNotConsume() {
        // {a? b?} with events [-a, +b, +c]: the tap-press fallback matches +b
        // as dangling tap(b), but -a and +c don't match any move. These unmatched
        // events should not be consumed by the dangling tap press.
        // All-optional move set: should complete with 0 moves (skip all optionals).
        MoveSet moveSet = withOptional(List.of(), List.of(tapMove(a), tapMove(b)));
        ComboSequenceMatch match = prep(
                release(a, t(0)), press(b, t(50)), press(c, t(100)))
                .match(seq(moveSet));
        assertTrue(match.complete());
    }

    @Test
    void tap_optional_danglingPress_withNegatedWait_unmatchedEventNotConsumed() {
        // Simplified oneshot: #!{keys}-0 {*keys?} +c
        // keys=a,b, events [-a, +b, +d].
        // Wait #!{keys} ignores all EXCEPT keys. -a (key in set) can't be
        // absorbed, so all 3 events go to {*keys?}. The fallback must not
        // consume +d as part of a dangling tap press for +b.
        // If a match exists (wait absorbs +d at a smaller suffix), +d must
        // not appear in matchedKeyMoves (i.e. mustBeEaten would be false).
        Map<String, KeyAlias> aliases = Map.of(
                "keys", new KeyAlias("keys", List.of(a, b)));
        ComboSequence combo = parseCombo("#!{keys}-0 {*keys?} +c", aliases);
        ComboSequenceMatch match = prep(
                release(a, t(0)), press(b, t(50)), press(d, t(100)))
                .match(combo);
        for (var m : match.matchedKeyMoves()) {
            assertNotEquals(d, m.key());
        }
    }

    @Test
    void tap_optional_multipleDanglingPresses_partialMatch() {
        // +1 -1 {*keys?} +4 with keys=a,b,c, events [+1, -1, +a, +b].
        // {*keys?} gets [+a, +b]: both are dangling tap presses.
        // Should be a partial match (waiting for releases then +4).
        Map<String, KeyAlias> aliases = Map.of(
                "keys", new KeyAlias("keys", List.of(a, b, c)));
        ComboSequence combo = parseCombo("+1 -1 {*keys?} +4", aliases);
        Key one = Key.ofName("1");
        ComboSequenceMatch match = prep(
                press(one, t(0)), release(one, t(50)),
                press(a, t(200)), press(b, t(300)))
                .match(combo);
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
    }

    @Test
    void tap_optional_completeTapPlusDanglingPress_partialMatch() {
        // +1 -1 {*keys?} +4 with keys=a,b,c, events [+1, -1, +a, -a, +b].
        // {*keys?} gets [+a, -a, +b]: tap(a) complete + tap(b) dangling.
        // Should be a partial match (waiting for -b then +4).
        Map<String, KeyAlias> aliases = Map.of(
                "keys", new KeyAlias("keys", List.of(a, b, c)));
        ComboSequence combo = parseCombo("+1 -1 {*keys?} +4", aliases);
        Key one = Key.ofName("1");
        ComboSequenceMatch match = prep(
                press(one, t(0)), release(one, t(50)),
                press(a, t(200)), release(a, t(250)), press(b, t(300)))
                .match(combo);
        assertTrue(match.hasMatch());
        assertFalse(match.complete());
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

    @Test
    void wait_peek_skipsAllOptionalMoveSet() {
        // #!{keys}-0 {*keys?} +c with keys=a,b: event [+c].
        // The wait peek should not reject +c just because it doesn't match {*keys?}.
        // {*keys?} is all-optional and can match 0 events.
        Map<String, KeyAlias> aliases = Map.of(
                "keys", new KeyAlias("keys", List.of(a, b)));
        ComboSequence combo = parseCombo("#!{keys}-0 {*keys?} +c", aliases);
        ComboSequenceMatch match = prep(press(c, t(0))).match(combo);
        assertTrue(match.complete());
    }

    @Test
    void wait_peek_skipsAllOptionalMoveSet_withPrecedingEvents() {
        // #!{keys}-0 {*keys?} +c with keys=a,b: events [+a, -a, +c].
        // Wait absorbs +a -a, {*keys?} matches 0, +c matches.
        Map<String, KeyAlias> aliases = Map.of(
                "keys", new KeyAlias("keys", List.of(a, b)));
        ComboSequence combo = parseCombo("#!{keys}-0 {*keys?} +c", aliases);
        ComboSequenceMatch match = prep(
                press(a, t(0)), release(a, t(50)), press(c, t(100)))
                .match(combo);
        assertTrue(match.complete());
    }

    @Test
    void wait_peek_stillRejectsNonMatchingRequiredMoveSet() {
        // #!{keys}-0 +c with keys=a,b: event [+d].
        // The wait peek should still reject +d since +c is a required move set.
        Map<String, KeyAlias> aliases = Map.of(
                "keys", new KeyAlias("keys", List.of(a, b)));
        ComboSequence combo = parseCombo("#!{keys}-0 +c", aliases);
        ComboSequenceMatch match = prep(press(d, t(0))).match(combo);
        assertFalse(match.hasMatch());
    }

    // --- Leading/mid-sequence WaitMoveSet + partial tap matching ---

    @Test
    void leadingWait_tapMoveSet_singlePress_partialMatch() {
        // #{*}-0 a: wait absorbing all keys, then tap a.
        // Events [+a]: wait absorbs 0, +a should partially match as dangling tap press.
        // Bug: WaitMoveSet computed minForRemaining using full minMoveCount()
        // of subsequent moveSets (2 for tap), not accounting for
        // allowPartialLastMoveSet, making maxAbsorb negative.
        ComboSequence combo = parseCombo("#{*}-0 a", Map.of());
        ComboSequenceMatch match = prep(press(a, t(0))).match(combo);
        assertTrue(match.hasMatch(),
                "single press after leading wait should partially match tap");
        assertFalse(match.complete());
    }

    @Test
    void leadingNegatedWait_tapMoveSet_singlePress_partialMatch() {
        // #!{keys}-0 {*keys+} with keys=a,b: events [+a].
        // Same bug as above but with negated wait and alias-expanded taps.
        Map<String, KeyAlias> aliases = Map.of(
                "keys", new KeyAlias("keys", List.of(a, b)));
        ComboSequence combo = parseCombo("#!{keys}-0 {*keys+}", aliases);
        ComboSequenceMatch match = prep(press(a, t(0))).match(combo);
        assertTrue(match.hasMatch(),
                "single press after negated wait should partially match tap");
        assertFalse(match.complete());
    }

    @Test
    void midSequenceWait_tapMoveSet_singlePress_partialMatch() {
        // +c #!{keys}-0 {*keys+}: press c, negated wait, then tap(s) from keys.
        // Events [+c, +a]: +c matches, wait absorbs 0, +a partial match.
        Map<String, KeyAlias> aliases = Map.of(
                "keys", new KeyAlias("keys", List.of(a, b)));
        ComboSequence combo = parseCombo("+c #!{keys}-0 {*keys+}", aliases);
        ComboSequenceMatch match = prep(press(c, t(0)), press(a, t(50)))
                .match(combo);
        assertTrue(match.hasMatch(),
                "press after mid-sequence wait should partially match tap");
        assertFalse(match.complete());
    }

    // --- atLeastOne (+ suffix) tests ---

    @Test
    void expandedAlias_zeroOrMore_noMatchingEvents_complete() {
        // {*alias?} with alias={a,b}: events [+x] → all optional skipped, complete.
        Map<String, KeyAlias> aliases = Map.of(
                "keys", new KeyAlias("keys", List.of(a, b)));
        ComboSequence combo = parseCombo("{+*keys?}", aliases);
        ComboSequenceMatch match = prep(press(x, t(0))).match(combo);
        assertTrue(match.complete());
        assertEquals(0, match.matchedKeyMoves().size());
    }

    @Test
    void expandedAlias_atLeastOne_oneComplete_complete() {
        // {*alias+} with alias={a,b}: events [+a, -a] → tap(a) complete, at least one satisfied.
        Map<String, KeyAlias> aliases = Map.of(
                "keys", new KeyAlias("keys", List.of(a, b)));
        ComboSequence combo = parseCombo("{*keys+}", aliases);
        ComboSequenceMatch match = prep(press(a, t(0)), release(a, t(50)))
                .match(combo);
        assertTrue(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void expandedAlias_atLeastOne_noMatchingEvents_noMatch() {
        // {*alias+} with alias={a,b}: events [+x] → no tap matched, at least one required.
        Map<String, KeyAlias> aliases = Map.of(
                "keys", new KeyAlias("keys", List.of(a, b)));
        ComboSequence combo = parseCombo("{*keys+}", aliases);
        ComboSequenceMatch match = prep(press(x, t(0))).match(combo);
        assertFalse(match.complete());
    }

    @Test
    void expandedAlias_atLeastOne_withRequired_missingAlias_noMatch() {
        // {*alias+ +c} with alias={a,b}: events [+c] → +c matches but no
        // alias key matched, at least one required.
        Map<String, KeyAlias> aliases = Map.of(
                "keys", new KeyAlias("keys", List.of(a, b)));
        ComboSequence combo = parseCombo("{*keys+ +c}", aliases);
        ComboSequenceMatch match = prep(press(c, t(0))).match(combo);
        assertFalse(match.complete());
    }

    // --- Absorbing wait inside MoveSet should not suppress dangling tap press ---

    @Test
    void absorbingWait_tapMoveSet_singlePress_partialMatch() {
        // {a b #{*}-0-200}: tap a and tap b in any order, ignoring all keys.
        // Events [+a]: +a should partially match as dangling tap(a) press,
        // not be absorbed by #{*}.
        ComboSequence combo = parseCombo("{a b #{*}-0-200}", Map.of());
        ComboSequenceMatch match = prep(press(a, t(0))).match(combo);
        assertTrue(match.hasMatch(),
                "press should partially match tap in MoveSet with absorbing wait");
        assertFalse(match.complete());
    }

    @Test
    void absorbingWait_tapMoveSet_twoPressesDifferentKeys_partialMatch() {
        // {a b #{*}-0-200}: events [+a, +b] → both should partially match
        // as dangling tap presses, not be absorbed.
        ComboSequence combo = parseCombo("{a b #{*}-0-200}", Map.of());
        ComboSequenceMatch match = prep(press(a, t(0)), press(b, t(50)))
                .match(combo);
        assertTrue(match.hasMatch(),
                "two presses should partially match taps in MoveSet with absorbing wait");
        assertFalse(match.complete());
    }

    @Test
    void absorbingWait_tapMoveSet_fullTap_complete() {
        // {a b #{*}-0-200}: events [+a, -a, +b, -b] → full match.
        ComboSequence combo = parseCombo("{a b #{*}-0-200}", Map.of());
        ComboSequenceMatch match = prep(
                press(a, t(0)), release(a, t(50)),
                press(b, t(100)), release(b, t(150)))
                .match(combo);
        assertTrue(match.complete(),
                "full tap sequence should complete with absorbing wait");
    }

    @Test
    void absorbingWait_tapMoveSet_fullTapWithAbsorbedEvent_complete() {
        // {a b #{*}-0-200}: events [+a, +x, -a, +b, -b] → +x absorbed,
        // tap(a) and tap(b) fully matched.
        ComboSequence combo = parseCombo("{a b #{*}-0-200}", Map.of());
        ComboSequenceMatch match = prep(
                press(a, t(0)), press(x, t(50)), release(a, t(100)),
                press(b, t(150)), release(b, t(200)))
                .match(combo);
        assertTrue(match.complete(),
                "full tap sequence with absorbed event should complete");
    }

    @Test
    void absorbingWait_tapMoveSet_oneTapPlusAbsorbedTap_partialMatch() {
        // {a b #{*}-0-200}: events [+a, -a, +x, -x] → tap(a) fully matched,
        // +x/-x absorbed by #{*}. Partial match (tap(b) not matched).
        // This tests the general-path partial absorption fallback:
        // eventCount(4) == requiredSlots(4), so the non-partial path runs.
        // Full match fails (x != b). Partial absorption should succeed with
        // tap(a) matched and +x/-x absorbed.
        ComboSequence combo = parseCombo("{a b #{*}-0-200}", Map.of());
        ComboSequenceMatch match = prep(
                press(a, t(0)), release(a, t(50)),
                press(x, t(100)), release(x, t(150)))
                .match(combo);
        assertTrue(match.hasMatch(),
                "tap(a) should match with non-matching events absorbed");
        assertFalse(match.complete());
        assertEquals(2, match.matchedKeyMoves().size());
    }

    @Test
    void absorbingWait_tapMoveSet_nonMatchingPressThenMatchingPress_partialMatch() {
        // {a b #{*}-0-2000}: events [+x, +a] → larger suffix [+x, +a] produces
        // pure absorption (0 key moves); smaller suffix [+a] should be tried
        // and match as dangling tap(a) press.
        ComboSequence combo = parseCombo("{a b #{*}-0-2000}", Map.of());
        ComboSequenceMatch match = prep(press(x, t(0)), press(a, t(100)))
                .match(combo);
        assertTrue(match.hasMatch(),
                "should skip pure-absorption suffix and find tap press in smaller suffix");
        assertFalse(match.complete());
    }
}
