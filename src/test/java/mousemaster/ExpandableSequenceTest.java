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

}
