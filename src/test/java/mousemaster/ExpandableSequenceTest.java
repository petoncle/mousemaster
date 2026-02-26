package mousemaster;

import mousemaster.ComboAliasMove.WaitComboAliasMove;
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

    // --- wait (plain, no ignore) ---

    @Test
    void waitWithExplicitMinMax() {
        WaitComboAliasMove move = parseWait("wait-200-500");
        assertEquals(Duration.ofMillis(200), move.duration().min());
        assertEquals(Duration.ofMillis(500), move.duration().max());
    }

    @Test
    void waitWithExplicitMin() {
        WaitComboAliasMove move = parseWait("wait-200");
        assertEquals(Duration.ofMillis(200), move.duration().min());
        assertNull(move.duration().max());
    }

    @Test
    void waitWithExplicitZero() {
        WaitComboAliasMove move = parseWait("wait-0");
        assertEquals(Duration.ZERO, move.duration().min());
        assertNull(move.duration().max());
    }

    @Test
    void waitShorthandDefaultsToZero() {
        WaitComboAliasMove move = parseWait("wait");
        assertEquals(Duration.ZERO, move.duration().min());
        assertNull(move.duration().max());
    }

    // --- #{*} (ignore all) ---

    @Test
    void ignoreAllWithExplicitMinMax() {
        WaitComboAliasMove move = parseWait("#{*}-200-500");
        assertEquals(Duration.ofMillis(200), move.duration().min());
        assertEquals(Duration.ofMillis(500), move.duration().max());
        assertEquals(Set.of(), move.keyAliasOrKeyNames());
        assertFalse(move.listedKeysAreIgnored());
    }

    @Test
    void ignoreAllWithExplicitMin() {
        WaitComboAliasMove move = parseWait("#{*}-200");
        assertEquals(Duration.ofMillis(200), move.duration().min());
        assertNull(move.duration().max());
    }

    @Test
    void ignoreAllShorthandDefaultsToZero() {
        WaitComboAliasMove move = parseWait("#{*}");
        assertEquals(Duration.ZERO, move.duration().min());
        assertNull(move.duration().max());
        assertEquals(Set.of(), move.keyAliasOrKeyNames());
        assertFalse(move.listedKeysAreIgnored());
    }

    // --- #!{keys} (ignore all except) ---

    @Test
    void ignoreAllExceptWithExplicitMinMax() {
        WaitComboAliasMove move =
                parseWait("#!{capslock}-200-500");
        assertEquals(Duration.ofMillis(200), move.duration().min());
        assertEquals(Duration.ofMillis(500), move.duration().max());
        assertEquals(Set.of("capslock"), move.keyAliasOrKeyNames());
        assertFalse(move.listedKeysAreIgnored());
    }

    @Test
    void ignoreAllExceptShorthandDefaultsToZero() {
        WaitComboAliasMove move =
                parseWait("#!{capslock}");
        assertEquals(Duration.ZERO, move.duration().min());
        assertNull(move.duration().max());
        assertEquals(Set.of("capslock"), move.keyAliasOrKeyNames());
        assertFalse(move.listedKeysAreIgnored());
    }

    // --- #{keys} (ignore listed) ---

    @Test
    void ignoreKeysWithExplicitMinMax() {
        WaitComboAliasMove move = parseWait("#{a b}-200-500");
        assertEquals(Duration.ofMillis(200), move.duration().min());
        assertEquals(Duration.ofMillis(500), move.duration().max());
        assertEquals(Set.of("a", "b"), move.keyAliasOrKeyNames());
        assertTrue(move.listedKeysAreIgnored());
    }

    @Test
    void ignoreKeysShorthandDefaultsToZero() {
        WaitComboAliasMove move = parseWait("#{a b}");
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
        WaitComboAliasMove move =
                parseWait("+!{capslock}");
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
        assertEquals(KeySet.ALL, kms.waitMove().ignoredKeySet());
        assertFalse(kms.waitMove().ignoredKeysEatEvents());
    }

    @Test
    void moveSetIgnoreAllEat() {
        KeyMoveSet kms = parseMoveSetWithIgnoredKeys("{+x +{*}}");
        assertEquals(1, kms.requiredMoves().size());
        assertTrue(kms.canAbsorbEvents());
        assertNotNull(kms.waitMove());
        assertEquals(KeySet.ALL, kms.waitMove().ignoredKeySet());
        assertTrue(kms.waitMove().ignoredKeysEatEvents());
    }

    @Test
    void moveSetIgnoreKeysWithDuration() {
        KeyMoveSet kms = parseMoveSetWithIgnoredKeys("{+x #{a b}-0-500}");
        assertEquals(1, kms.requiredMoves().size());
        assertTrue(kms.canAbsorbEvents());
        assertNotNull(kms.waitMove());
        assertInstanceOf(KeySet.Only.class, kms.waitMove().ignoredKeySet());
        Set<Key> ignoredKeys = ((KeySet.Only) kms.waitMove().ignoredKeySet()).keys();
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

}
