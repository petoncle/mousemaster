package mousemaster;

import mousemaster.ComboAliasMove.WaitComboAliasMove;
import mousemaster.ComboAliasMove.WaitComboAliasMove.KeyWaitComboAliasMove;
import mousemaster.ComboAliasMove.WaitComboAliasMove.PressWaitComboAliasMove;
import mousemaster.ComboAliasMove.WaitComboAliasMove.ReleaseWaitComboAliasMove;
import mousemaster.ComboMove.WaitComboMove;
import mousemaster.MoveSet.KeyMoveSet;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ExpandableSequenceTest {

    static final ComboMoveDuration defaultDuration =
            new ComboMoveDuration(Duration.ZERO, null);

    private static WaitComboAliasMove parseWait(String input) {
        ExpandableSequence seq =
                ExpandableSequence.parseSequence(input, defaultDuration, Map.of());
        assertEquals(1, seq.moveSets().size());
        ComboAliasMove move = seq.moveSets().getFirst().iterator().next();
        assertInstanceOf(WaitComboAliasMove.class, move);
        return (WaitComboAliasMove) move;
    }

    private static KeyWaitComboAliasMove parseKeyWait(String input) {
        WaitComboAliasMove wait = parseWait(input);
        assertInstanceOf(KeyWaitComboAliasMove.class, wait);
        return (KeyWaitComboAliasMove) wait;
    }

    // --- wait (plain, no ignore) ---

    @Test
    void waitWithExplicitMinMax() {
        KeyWaitComboAliasMove move = parseKeyWait("wait-200-500");
        assertEquals(Duration.ofMillis(200), move.duration().min());
        assertEquals(Duration.ofMillis(500), move.duration().max());
    }

    @Test
    void waitWithExplicitMin() {
        KeyWaitComboAliasMove move = parseKeyWait("wait-200");
        assertEquals(Duration.ofMillis(200), move.duration().min());
        assertNull(move.duration().max());
    }

    @Test
    void waitWithExplicitZero() {
        KeyWaitComboAliasMove move = parseKeyWait("wait-0");
        assertEquals(Duration.ZERO, move.duration().min());
        assertNull(move.duration().max());
    }

    @Test
    void waitShorthandDefaultsToZero() {
        KeyWaitComboAliasMove move = parseKeyWait("wait");
        assertEquals(Duration.ZERO, move.duration().min());
        assertNull(move.duration().max());
    }

    // --- #{*} (ignore all) ---

    @Test
    void ignoreAllWithExplicitMinMax() {
        KeyWaitComboAliasMove move = parseKeyWait("#{*}-200-500");
        assertEquals(Duration.ofMillis(200), move.duration().min());
        assertEquals(Duration.ofMillis(500), move.duration().max());
        assertEquals(Set.of(), move.keyAliasOrKeyNames());
        assertFalse(move.listedKeysAreIgnored());
    }

    @Test
    void ignoreAllWithExplicitMin() {
        KeyWaitComboAliasMove move = parseKeyWait("#{*}-200");
        assertEquals(Duration.ofMillis(200), move.duration().min());
        assertNull(move.duration().max());
    }

    @Test
    void ignoreAllShorthandDefaultsToZero() {
        KeyWaitComboAliasMove move = parseKeyWait("#{*}");
        assertEquals(Duration.ZERO, move.duration().min());
        assertNull(move.duration().max());
        assertEquals(Set.of(), move.keyAliasOrKeyNames());
        assertFalse(move.listedKeysAreIgnored());
    }

    // --- #!{keys} (ignore all except) ---

    @Test
    void ignoreAllExceptWithExplicitMinMax() {
        KeyWaitComboAliasMove move = parseKeyWait("#!{capslock}-200-500");
        assertEquals(Duration.ofMillis(200), move.duration().min());
        assertEquals(Duration.ofMillis(500), move.duration().max());
        assertEquals(Set.of("capslock"), move.keyAliasOrKeyNames());
        assertFalse(move.listedKeysAreIgnored());
    }

    @Test
    void ignoreAllExceptShorthandDefaultsToZero() {
        KeyWaitComboAliasMove move = parseKeyWait("#!{capslock}");
        assertEquals(Duration.ZERO, move.duration().min());
        assertNull(move.duration().max());
        assertEquals(Set.of("capslock"), move.keyAliasOrKeyNames());
        assertFalse(move.listedKeysAreIgnored());
    }

    // --- #{keys} (ignore listed) ---

    @Test
    void ignoreKeysWithExplicitMinMax() {
        KeyWaitComboAliasMove move = parseKeyWait("#{a b}-200-500");
        assertEquals(Duration.ofMillis(200), move.duration().min());
        assertEquals(Duration.ofMillis(500), move.duration().max());
        assertEquals(Set.of("a", "b"), move.keyAliasOrKeyNames());
        assertTrue(move.listedKeysAreIgnored());
    }

    @Test
    void ignoreKeysShorthandDefaultsToZero() {
        KeyWaitComboAliasMove move = parseKeyWait("#{a b}");
        assertEquals(Duration.ZERO, move.duration().min());
        assertNull(move.duration().max());
        assertEquals(Set.of("a", "b"), move.keyAliasOrKeyNames());
        assertTrue(move.listedKeysAreIgnored());
    }

    // --- + prefix (eat events) ---

    @Test
    void plusPrefixEatsEvents() {
        WaitComboAliasMove move = parseWait("+{*}");
        assertTrue(move.ignoredKeysEatEvents());
        assertEquals(Duration.ZERO, move.duration().min());
    }

    @Test
    void hashPrefixDoesNotEatEvents() {
        WaitComboAliasMove move = parseWait("#{*}");
        assertFalse(move.ignoredKeysEatEvents());
    }

    @Test
    void plusPrefixIgnoreAllExceptShorthand() {
        KeyWaitComboAliasMove move = parseKeyWait("+!{capslock}");
        assertTrue(move.ignoredKeysEatEvents());
        assertEquals(Duration.ZERO, move.duration().min());
        assertEquals(Set.of("capslock"), move.keyAliasOrKeyNames());
    }

    // --- bare key shorthand (press + release) ---

    @Test
    void singleBareKey() {
        ExpandableSequence seq =
                ExpandableSequence.parseSequence("leftctrl", defaultDuration, Map.of());
        assertEquals(1, seq.moveSets().size());
        ComboAliasMove first = seq.moveSets().get(0).iterator().next();
        assertInstanceOf(ComboAliasMove.TapComboAliasMove.class, first);
        assertEquals("leftctrl", first.aliasOrKeyName());
    }

    @Test
    void multipleBareKeysOutsideBraces() {
        ExpandableSequence seq =
                ExpandableSequence.parseSequence("a b", defaultDuration, Map.of());
        assertEquals(2, seq.moveSets().size());
        assertInstanceOf(ComboAliasMove.TapComboAliasMove.class,
                seq.moveSets().get(0).iterator().next());
        assertInstanceOf(ComboAliasMove.TapComboAliasMove.class,
                seq.moveSets().get(1).iterator().next());
        assertEquals("a", seq.moveSets().get(0).iterator().next().aliasOrKeyName());
        assertEquals("b", seq.moveSets().get(1).iterator().next().aliasOrKeyName());
    }

    @Test
    void bareKeysInsideBraces() {
        ExpandableSequence seq =
                ExpandableSequence.parseSequence("{a b}", defaultDuration, Map.of());
        assertEquals(1, seq.moveSets().size());
        Set<ComboAliasMove> moveSet = seq.moveSets().getFirst();
        // Should contain 2 tap moves (one per key)
        assertEquals(2, moveSet.size());
        long tapCount = moveSet.stream()
                .filter(m -> m instanceof ComboAliasMove.TapComboAliasMove).count();
        assertEquals(2, tapCount);
    }

    @Test
    void bareExpandAliasOutsideBraces() {
        Map<String, KeyAlias> aliases = Map.of("alias1",
                new KeyAlias("alias1", List.of(Key.ofName("a"), Key.ofName("b"))));
        ExpandableSequence seq =
                ExpandableSequence.parseSequence("*alias1", defaultDuration, aliases);
        // *alias1 with alias1=a b → tap(a) tap(b) (2 sequential MoveSets)
        assertEquals(2, seq.moveSets().size());
        assertInstanceOf(ComboAliasMove.TapComboAliasMove.class,
                seq.moveSets().get(0).iterator().next());
        assertEquals("a", seq.moveSets().get(0).iterator().next().aliasOrKeyName());
        assertInstanceOf(ComboAliasMove.TapComboAliasMove.class,
                seq.moveSets().get(1).iterator().next());
        assertEquals("b", seq.moveSets().get(1).iterator().next().aliasOrKeyName());
    }

    @Test
    void bareExpandAliasInsideBraces() {
        Map<String, KeyAlias> aliases = Map.of("alias1",
                new KeyAlias("alias1", List.of(Key.ofName("a"), Key.ofName("b"))));
        ExpandableSequence seq =
                ExpandableSequence.parseSequence("{*alias1}", defaultDuration, aliases);
        // {*alias1} with alias1=a b → {tap(a) tap(b)} (single MoveSet, 2 taps)
        assertEquals(1, seq.moveSets().size());
        Set<ComboAliasMove> moveSet = seq.moveSets().getFirst();
        assertEquals(2, moveSet.size());
        Set<String> tapKeys = moveSet.stream()
                .filter(m -> m instanceof ComboAliasMove.TapComboAliasMove)
                .map(ComboAliasMove::aliasOrKeyName).collect(Collectors.toSet());
        assertEquals(Set.of("a", "b"), tapKeys);
    }

    @Test
    void mixedBareAndPrefixedTokens() {
        ExpandableSequence seq =
                ExpandableSequence.parseSequence("+a b", defaultDuration, Map.of());
        // +a → 1 MoveSet (press only), b → 1 MoveSet (tap)
        assertEquals(2, seq.moveSets().size());
        assertInstanceOf(ComboAliasMove.PressComboAliasMove.class,
                seq.moveSets().get(0).iterator().next());
        assertEquals("a", seq.moveSets().get(0).iterator().next().aliasOrKeyName());
        assertInstanceOf(ComboAliasMove.TapComboAliasMove.class,
                seq.moveSets().get(1).iterator().next());
        assertEquals("b", seq.moveSets().get(1).iterator().next().aliasOrKeyName());
    }

    @Test
    void prefixedTokensUnchanged() {
        ExpandableSequence seq =
                ExpandableSequence.parseSequence("+a -a", defaultDuration, Map.of());
        assertEquals(2, seq.moveSets().size());
        assertInstanceOf(ComboAliasMove.PressComboAliasMove.class,
                seq.moveSets().get(0).iterator().next());
        assertInstanceOf(ComboAliasMove.ReleaseComboAliasMove.class,
                seq.moveSets().get(1).iterator().next());
    }

    // --- bare key with duration suffix ---

    @Test
    void bareTapWithMinMaxDuration() {
        ExpandableSequence seq =
                ExpandableSequence.parseSequence("g-0-2000", defaultDuration, Map.of());
        assertEquals(1, seq.moveSets().size());
        ComboAliasMove move = seq.moveSets().getFirst().iterator().next();
        assertInstanceOf(ComboAliasMove.TapComboAliasMove.class, move);
        assertEquals("g", move.aliasOrKeyName());
        assertEquals(Duration.ZERO, move.duration().min());
        assertEquals(Duration.ofMillis(2000), move.duration().max());
    }

    @Test
    void bareTapWithMinOnlyDuration() {
        ExpandableSequence seq =
                ExpandableSequence.parseSequence("g-200", defaultDuration, Map.of());
        assertEquals(1, seq.moveSets().size());
        ComboAliasMove move = seq.moveSets().getFirst().iterator().next();
        assertInstanceOf(ComboAliasMove.TapComboAliasMove.class, move);
        assertEquals("g", move.aliasOrKeyName());
        assertEquals(Duration.ofMillis(200), move.duration().min());
        assertNull(move.duration().max());
    }

    @Test
    void bareTapsWithDurationInsideBraces() {
        ExpandableSequence seq =
                ExpandableSequence.parseSequence("{g-0-2000 i-0-2000}", defaultDuration,
                        Map.of());
        assertEquals(1, seq.moveSets().size());
        Set<ComboAliasMove> moveSet = seq.moveSets().getFirst();
        assertEquals(2, moveSet.size());
        for (ComboAliasMove move : moveSet) {
            assertInstanceOf(ComboAliasMove.TapComboAliasMove.class, move);
            assertEquals(Duration.ZERO, move.duration().min());
            assertEquals(Duration.ofMillis(2000), move.duration().max());
        }
        Set<String> keyNames = moveSet.stream()
                .map(ComboAliasMove::aliasOrKeyName).collect(Collectors.toSet());
        assertEquals(Set.of("g", "i"), keyNames);
    }

    @Test
    void braceLevelDurationAppliedToBareTaps() {
        ExpandableSequence seq =
                ExpandableSequence.parseSequence("{g i a}-0-2000", defaultDuration,
                        Map.of());
        assertEquals(1, seq.moveSets().size());
        Set<ComboAliasMove> moveSet = seq.moveSets().getFirst();
        assertEquals(3, moveSet.size());
        for (ComboAliasMove move : moveSet) {
            assertInstanceOf(ComboAliasMove.TapComboAliasMove.class, move);
            assertEquals(Duration.ZERO, move.duration().min());
            assertEquals(Duration.ofMillis(2000), move.duration().max());
        }
    }

    @Test
    void braceLevelDurationOverriddenByTokenDuration() {
        ExpandableSequence seq =
                ExpandableSequence.parseSequence("{g i-0-200 a}-0-2000", defaultDuration,
                        Map.of());
        assertEquals(1, seq.moveSets().size());
        Set<ComboAliasMove> moveSet = seq.moveSets().getFirst();
        assertEquals(3, moveSet.size());
        for (ComboAliasMove move : moveSet) {
            assertInstanceOf(ComboAliasMove.TapComboAliasMove.class, move);
            if (move.aliasOrKeyName().equals("i")) {
                assertEquals(Duration.ZERO, move.duration().min());
                assertEquals(Duration.ofMillis(200), move.duration().max());
            }
            else {
                assertEquals(Duration.ZERO, move.duration().min());
                assertEquals(Duration.ofMillis(2000), move.duration().max());
            }
        }
        Set<String> keyNames = moveSet.stream()
                .map(ComboAliasMove::aliasOrKeyName).collect(Collectors.toSet());
        assertEquals(Set.of("g", "i", "a"), keyNames);
    }

    @Test
    void bareTapWithoutDurationUsesDefault() {
        ComboMoveDuration custom =
                new ComboMoveDuration(Duration.ofMillis(100), Duration.ofMillis(500));
        ExpandableSequence seq =
                ExpandableSequence.parseSequence("g", custom, Map.of());
        assertEquals(1, seq.moveSets().size());
        ComboAliasMove move = seq.moveSets().getFirst().iterator().next();
        assertEquals("g", move.aliasOrKeyName());
        assertEquals(Duration.ofMillis(100), move.duration().min());
        assertEquals(Duration.ofMillis(500), move.duration().max());
    }

    // --- #{}/+{} inside braces (ignored keys in KeyMoveSet) ---

    static final KeyResolver identityKeyResolver = new KeyResolver(
            new KeyboardLayout("test", "test", "test", "test", List.of()),
            new KeyboardLayout("test", "test", "test", "test", List.of()));

    private static KeyMoveSet parseMoveSetWithIgnoredKeys(String input) {
        ExpandableSequence seq =
                ExpandableSequence.parseSequence(input, defaultDuration, Map.of());
        assertEquals(1, seq.moveSets().size());
        Set<ComboAliasMove> moveSet = seq.moveSets().getFirst();
        // Should have at least a key move and a wait alias move.
        assertTrue(moveSet.size() >= 2);
        // Resolve to ComboSequence to get a KeyMoveSet with ignored key fields.
        ComboSequence comboSeq = seq.toComboSequence(Map.of(), identityKeyResolver);
        assertEquals(1, comboSeq.moveSets().size());
        assertInstanceOf(KeyMoveSet.class, comboSeq.moveSets().getFirst());
        return (KeyMoveSet) comboSeq.moveSets().getFirst();
    }

    @Test
    void moveSetIgnoreAll() {
        KeyMoveSet kms = parseMoveSetWithIgnoredKeys("{-a -b #{*}}");
        assertEquals(2, kms.requiredMoves().size());
        assertTrue(kms.canAbsorbEvents());
        assertNotNull(kms.waitMove());
        assertInstanceOf(WaitComboMove.KeyWaitComboMove.class, kms.waitMove());
        WaitComboMove.KeyWaitComboMove kwm = (WaitComboMove.KeyWaitComboMove) kms.waitMove();
        assertEquals(KeySet.ALL, kwm.ignoredKeySet());
        assertFalse(kms.waitMove().ignoredKeysEatEvents());
    }

    @Test
    void moveSetIgnoreAllWithBraceDuration() {
        KeyMoveSet kms = parseMoveSetWithIgnoredKeys("{g i #{*}}-0-2000");
        assertEquals(2, kms.requiredMoves().size());
        for (var move : kms.requiredMoves()) {
            assertEquals(Duration.ZERO, move.duration().min());
            assertEquals(Duration.ofMillis(2000), move.duration().max());
        }
        assertNotNull(kms.waitMove());
        assertInstanceOf(WaitComboMove.KeyWaitComboMove.class, kms.waitMove());
        WaitComboMove.KeyWaitComboMove kwm = (WaitComboMove.KeyWaitComboMove) kms.waitMove();
        assertEquals(KeySet.ALL, kwm.ignoredKeySet());
        assertEquals(Duration.ZERO, kms.waitMove().duration().min());
        assertEquals(Duration.ofMillis(2000), kms.waitMove().duration().max());
    }

    @Test
    void moveSetIgnoreAllEat() {
        KeyMoveSet kms = parseMoveSetWithIgnoredKeys("{+x +{*}}");
        assertEquals(1, kms.requiredMoves().size());
        assertTrue(kms.canAbsorbEvents());
        assertNotNull(kms.waitMove());
        assertInstanceOf(WaitComboMove.KeyWaitComboMove.class, kms.waitMove());
        WaitComboMove.KeyWaitComboMove kwm = (WaitComboMove.KeyWaitComboMove) kms.waitMove();
        assertEquals(KeySet.ALL, kwm.ignoredKeySet());
        assertTrue(kms.waitMove().ignoredKeysEatEvents());
    }

    @Test
    void moveSetIgnoreKeysWithDuration() {
        KeyMoveSet kms = parseMoveSetWithIgnoredKeys("{+x #{a b}-0-500}");
        assertEquals(1, kms.requiredMoves().size());
        assertTrue(kms.canAbsorbEvents());
        assertNotNull(kms.waitMove());
        assertInstanceOf(WaitComboMove.KeyWaitComboMove.class, kms.waitMove());
        WaitComboMove.KeyWaitComboMove kwm = (WaitComboMove.KeyWaitComboMove) kms.waitMove();
        assertInstanceOf(KeySet.Only.class, kwm.ignoredKeySet());
        Set<Key> ignoredKeys = ((KeySet.Only) kwm.ignoredKeySet()).keys();
        assertEquals(Set.of(Key.ofName("a"), Key.ofName("b")), ignoredKeys);
        assertEquals(Duration.ZERO, kms.waitMove().duration().min());
        assertEquals(Duration.ofMillis(500), kms.waitMove().duration().max());
    }

    @Test
    void moveSetIgnoredKeysDoNotInterfereWithStandaloneIgnore() {
        // Standalone #{*} should still parse as a WaitMoveSet.
        ExpandableSequence seq =
                ExpandableSequence.parseSequence("+a #{*} +b", defaultDuration, Map.of());
        assertEquals(3, seq.moveSets().size());
        ComboSequence comboSeq = seq.toComboSequence(Map.of(), identityKeyResolver);
        assertInstanceOf(KeyMoveSet.class, comboSeq.moveSets().get(0));
        assertInstanceOf(MoveSet.WaitMoveSet.class, comboSeq.moveSets().get(1));
        assertInstanceOf(KeyMoveSet.class, comboSeq.moveSets().get(2));
    }

    // --- #{-} (ignore all releases) ---

    @Test
    void ignoreAllReleases_standalone() {
        WaitComboAliasMove move = parseWait("#{-}-0-200");
        assertInstanceOf(ReleaseWaitComboAliasMove.class, move);
        assertFalse(move.ignoredKeysEatEvents());
        assertEquals(Duration.ZERO, move.duration().min());
        assertEquals(Duration.ofMillis(200), move.duration().max());
    }

    @Test
    void ignoreAllReleases_resolved() {
        ExpandableSequence seq =
                ExpandableSequence.parseSequence("#{-}-0-200", defaultDuration, Map.of());
        ComboSequence comboSeq = seq.toComboSequence(Map.of(), identityKeyResolver);
        assertEquals(1, comboSeq.moveSets().size());
        assertInstanceOf(MoveSet.WaitMoveSet.class, comboSeq.moveSets().getFirst());
        MoveSet.WaitMoveSet wms = (MoveSet.WaitMoveSet) comboSeq.moveSets().getFirst();
        assertInstanceOf(WaitComboMove.ReleaseWaitComboMove.class, wms.waitMove());
        assertTrue(wms.canAbsorbEvents());
    }

    // --- #{+} (ignore all presses) ---

    @Test
    void ignoreAllPresses_standalone() {
        WaitComboAliasMove move = parseWait("#{+}-0-200");
        assertInstanceOf(PressWaitComboAliasMove.class, move);
        assertFalse(move.ignoredKeysEatEvents());
        assertEquals(Duration.ZERO, move.duration().min());
        assertEquals(Duration.ofMillis(200), move.duration().max());
    }

    // --- +{+} (eat all presses) ---

    @Test
    void eatAllPresses_standalone() {
        WaitComboAliasMove move = parseWait("+{+}");
        assertInstanceOf(PressWaitComboAliasMove.class, move);
        assertTrue(move.ignoredKeysEatEvents());
    }

    // --- #{-} / #{+} inside braces ---

    @Test
    void ignoreAllReleases_insideBraces() {
        KeyMoveSet kms = parseMoveSetWithIgnoredKeys("{+a +b #{-}-0-200}");
        assertEquals(2, kms.requiredMoves().size());
        assertTrue(kms.canAbsorbEvents());
        assertNotNull(kms.waitMove());
        assertInstanceOf(WaitComboMove.ReleaseWaitComboMove.class, kms.waitMove());
        assertEquals(Duration.ZERO, kms.waitMove().duration().min());
        assertEquals(Duration.ofMillis(200), kms.waitMove().duration().max());
    }

    @Test
    void ignoreAllPresses_insideBraces() {
        KeyMoveSet kms = parseMoveSetWithIgnoredKeys("{-a -b #{+}-0-200}");
        assertEquals(2, kms.requiredMoves().size());
        assertTrue(kms.canAbsorbEvents());
        assertNotNull(kms.waitMove());
        assertInstanceOf(WaitComboMove.PressWaitComboMove.class, kms.waitMove());
    }

    @Test
    void ignoreAllReleases_insideBraces_braceDuration() {
        // {g i #{-}}-0-2000: brace duration applies to both taps and the release wait.
        KeyMoveSet kms = parseMoveSetWithIgnoredKeys("{g i #{-}}-0-2000");
        assertEquals(2, kms.requiredMoves().size());
        assertNotNull(kms.waitMove());
        assertInstanceOf(WaitComboMove.ReleaseWaitComboMove.class, kms.waitMove());
        assertEquals(Duration.ZERO, kms.waitMove().duration().min());
        assertEquals(Duration.ofMillis(2000), kms.waitMove().duration().max());
    }

    // --- wait inside braces (sets brace duration, equivalent to suffix) ---

    @Test
    void waitInsideBraces_setsBraceDuration() {
        // {+a +b wait-0-200} is equivalent to {+a +b}-0-200:
        // wait duration is applied to key moves, no wait move is created.
        ExpandableSequence seq =
                ExpandableSequence.parseSequence("{+a +b wait-0-200}", defaultDuration, Map.of());
        assertEquals(1, seq.moveSets().size());
        Set<ComboAliasMove> moveSet = seq.moveSets().getFirst();
        assertEquals(2, moveSet.size()); // only key moves, no wait move
        for (ComboAliasMove move : moveSet) {
            assertInstanceOf(ComboAliasMove.PressComboAliasMove.class, move);
            assertEquals(Duration.ZERO, move.duration().min());
            assertEquals(Duration.ofMillis(200), move.duration().max());
        }
    }

}
