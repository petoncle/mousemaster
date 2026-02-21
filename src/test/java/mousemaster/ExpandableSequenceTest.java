package mousemaster;

import mousemaster.ComboAliasMove.WaitComboAliasMove;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

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

    // --- ignore-all ---

    @Test
    void ignoreAllWithExplicitMinMax() {
        WaitComboAliasMove move = parseWait("ignore-all-200-500");
        assertEquals(Duration.ofMillis(200), move.duration().min());
        assertEquals(Duration.ofMillis(500), move.duration().max());
        assertEquals(Set.of(), move.keyAliasOrKeyNames());
        assertFalse(move.listedKeysAreIgnored());
    }

    @Test
    void ignoreAllWithExplicitMin() {
        WaitComboAliasMove move = parseWait("ignore-all-200");
        assertEquals(Duration.ofMillis(200), move.duration().min());
        assertNull(move.duration().max());
    }

    @Test
    void ignoreAllShorthandDefaultsToZero() {
        WaitComboAliasMove move = parseWait("ignore-all");
        assertEquals(Duration.ZERO, move.duration().min());
        assertNull(move.duration().max());
        assertEquals(Set.of(), move.keyAliasOrKeyNames());
        assertFalse(move.listedKeysAreIgnored());
    }

    // --- ignore-all-except-{keys} ---

    @Test
    void ignoreAllExceptWithExplicitMinMax() {
        WaitComboAliasMove move =
                parseWait("ignore-all-except-{capslock}-200-500");
        assertEquals(Duration.ofMillis(200), move.duration().min());
        assertEquals(Duration.ofMillis(500), move.duration().max());
        assertEquals(Set.of("capslock"), move.keyAliasOrKeyNames());
        assertFalse(move.listedKeysAreIgnored());
    }

    @Test
    void ignoreAllExceptShorthandDefaultsToZero() {
        WaitComboAliasMove move =
                parseWait("ignore-all-except-{capslock}");
        assertEquals(Duration.ZERO, move.duration().min());
        assertNull(move.duration().max());
        assertEquals(Set.of("capslock"), move.keyAliasOrKeyNames());
        assertFalse(move.listedKeysAreIgnored());
    }

    // --- ignore-{keys} ---

    @Test
    void ignoreKeysWithExplicitMinMax() {
        WaitComboAliasMove move = parseWait("ignore-{a b}-200-500");
        assertEquals(Duration.ofMillis(200), move.duration().min());
        assertEquals(Duration.ofMillis(500), move.duration().max());
        assertEquals(Set.of("a", "b"), move.keyAliasOrKeyNames());
        assertTrue(move.listedKeysAreIgnored());
    }

    @Test
    void ignoreKeysShorthandDefaultsToZero() {
        WaitComboAliasMove move = parseWait("ignore-{a b}");
        assertEquals(Duration.ZERO, move.duration().min());
        assertNull(move.duration().max());
        assertEquals(Set.of("a", "b"), move.keyAliasOrKeyNames());
        assertTrue(move.listedKeysAreIgnored());
    }

    // --- + prefix ---

    @Test
    void plusPrefixEatsEvents() {
        WaitComboAliasMove move = parseWait("+ignore-all");
        assertTrue(move.ignoredKeysEatEvents());
        assertEquals(Duration.ZERO, move.duration().min());
    }

    @Test
    void noPrefixDoesNotEatEvents() {
        WaitComboAliasMove move = parseWait("ignore-all");
        assertFalse(move.ignoredKeysEatEvents());
    }

    @Test
    void plusPrefixIgnoreAllExceptShorthand() {
        WaitComboAliasMove move =
                parseWait("+ignore-all-except-{capslock}");
        assertTrue(move.ignoredKeysEatEvents());
        assertEquals(Duration.ZERO, move.duration().min());
        assertEquals(Set.of("capslock"), move.keyAliasOrKeyNames());
    }

}
