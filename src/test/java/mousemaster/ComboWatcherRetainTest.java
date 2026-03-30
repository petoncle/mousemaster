package mousemaster;

import mousemaster.ComboPrecondition.ComboAppPrecondition;
import mousemaster.ComboPrecondition.ComboKeyPrecondition;
import mousemaster.ComboPrecondition.PressedKeyPrecondition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ComboWatcherRetainTest {

    static final ComboMoveDuration defaultDuration =
            new ComboMoveDuration(Duration.ZERO, null);

    static final KeyResolver identityKeyResolver = new KeyResolver(
            new KeyboardLayout("test", "test", "test", "test", List.of()),
            new KeyboardLayout("test", "test", "test", "test", List.of()));

    static final ComboPrecondition emptyPrecondition = new ComboPrecondition(
            new ComboKeyPrecondition(Set.of(), new PressedKeyPrecondition(List.of())),
            new ComboAppPrecondition(Set.of(), Set.of()));

    static ComboSequence parseSequence(String comboString) {
        ExpandableSequence expandable = ExpandableSequence.parseSequence(
                comboString, defaultDuration, Map.of());
        return expandable.toComboSequence(Map.of(), identityKeyResolver);
    }

    static ModeMap modeMap(String... comboStrings) {
        Map<Combo, List<Command>> map = new LinkedHashMap<>();
        for (String comboString : comboStrings) {
            Combo combo = new Combo("test", emptyPrecondition,
                    parseSequence(comboString));
            map.put(combo, List.of());
        }
        Mode mode = new Mode("test-mode", false, false, null,
                new ComboMap(map), null, null, null, null, null, null, null, null);
        return new ModeMap(Set.of(mode));
    }

    static Duration retainDuration(String... comboStrings) {
        return ComboWatcher.comboPreparationRetainDuration(modeMap(comboStrings));
    }

    static int retainEventCount(String... comboStrings) {
        return ComboWatcher.comboPreparationMinRetainEventCount(modeMap(comboStrings));
    }

    // ===== comboPreparationRetainDuration tests =====

    @Test
    void retainDuration_noWaits_zero() {
        // +a +b: no waits
        assertEquals(Duration.ZERO, retainDuration("+a +b"));
    }

    @Test
    void retainDuration_singleBoundedAbsorbingWait_usesMax() {
        // #{*}-0-500 +a: bounded absorbing wait, max = 500ms
        assertEquals(Duration.ofMillis(500), retainDuration("#{*}-0-500 +a"));
    }

    @Test
    void retainDuration_multipleBoundedAbsorbingWaits_sumsMaxes() {
        // #{*}-0-500 +a #{*}-0-250: two bounded absorbing waits, 500 + 250 = 750ms
        assertEquals(Duration.ofMillis(750), retainDuration("#{*}-0-500 +a #{*}-0-250"));
    }

    @Test
    void retainDuration_trailingUnboundedAbsorbingWait_usesMin() {
        // +a #{*}-500: trailing unbounded absorbing wait, uses min = 500ms
        assertEquals(Duration.ofMillis(500), retainDuration("+a #{*}-500"));
    }

    @Test
    void retainDuration_nonTrailingUnboundedAbsorbingWait_returnsNull() {
        // #{*}-500 +a: non-trailing unbounded absorbing wait
        assertNull(retainDuration("#{*}-500 +a"));
    }

    @Test
    void retainDuration_nonAbsorbingWaitMoveSet_bounded_usesMax() {
        // +a wait-0-200 +b: non-absorbing WaitMoveSet with bounded duration
        assertEquals(Duration.ofMillis(200), retainDuration("+a wait-0-200 +b"));
    }

    @Test
    void retainDuration_nonAbsorbingWaitMoveSet_unbounded_nonTrailing_returnsNull() {
        // +a wait-0 +b: non-absorbing WaitMoveSet with unbounded duration, non-trailing
        assertNull(retainDuration("+a wait-0 +b"));
    }

    @Test
    void retainDuration_nonAbsorbingWaitMoveSet_unbounded_trailing_usesMin() {
        // +a wait-500: trailing non-absorbing WaitMoveSet with unbounded max, uses min
        assertEquals(Duration.ofMillis(500), retainDuration("+a wait-500"));
    }

    @Test
    void retainDuration_allWaitCombo_skipped() {
        // #{*}-0-500: all-wait combo (no key moves), skipped
        assertEquals(Duration.ZERO, retainDuration("#{*}-0-500"));
    }

    @Test
    void retainDuration_keyMoveSetWithAbsorbingWait_usesWaitDuration() {
        // {+a +b #{*}-0-200}: KeyMoveSet with absorbing wait, uses waitMove's max
        assertEquals(Duration.ofMillis(200), retainDuration("{+a +b #{*}-0-200}"));
    }

    @Test
    void retainDuration_pressWaitComboMove_bounded_usesMax() {
        // #{+}-0-300 +a: PressWaitComboMove bounded, max = 300ms
        assertEquals(Duration.ofMillis(300), retainDuration("#{+}-0-300 +a"));
    }

    @Test
    void retainDuration_releaseWaitComboMove_bounded_usesMax() {
        // #{-}-0-400 -a: ReleaseWaitComboMove bounded, max = 400ms
        assertEquals(Duration.ofMillis(400), retainDuration("#{-}-0-400 -a"));
    }

    @Test
    void retainDuration_mixedBoundedAndTrailingUnbounded_sumsCorrectly() {
        // #{*}-0-500 +a #{*}-250: bounded(500) + trailing unbounded(min=250) = 750ms
        assertEquals(Duration.ofMillis(750), retainDuration("#{*}-0-500 +a #{*}-250"));
    }

    @Test
    void retainDuration_plainWaitInsideBraces_doesNotContribute() {
        // {+a +b wait-0-200}: plain wait inside braces sets braceDuration on key moves,
        // does not create a waitMove on the KeyMoveSet.
        assertEquals(Duration.ZERO, retainDuration("{+a +b wait-0-200}"));
    }

    @Test
    void retainDuration_nonAbsorbingWaitBetweenBoundedAbsorbing_sumsAll() {
        // #{*}-0-100 +a wait-0-200 +b #{*}-0-300: three waits, 100 + 200 + 300 = 600ms
        assertEquals(Duration.ofMillis(600),
                retainDuration("#{*}-0-100 +a wait-0-200 +b #{*}-0-300"));
    }

    // ===== comboPreparationMinRetainEventCount tests =====

    @Test
    void minRetainEventCount_singlePress_one() {
        assertEquals(1, retainEventCount("+a"));
    }

    @Test
    void minRetainEventCount_twoSequentialPresses_two() {
        assertEquals(2, retainEventCount("+a +b"));
    }

    @Test
    void minRetainEventCount_chord_countsAllMoves() {
        // {+a +b}: 2 key events in one MoveSet
        assertEquals(2, retainEventCount("{+a +b}"));
    }

    @Test
    void minRetainEventCount_tap_two() {
        // a (tap): press + release = 2
        assertEquals(2, retainEventCount("a"));
    }

    @Test
    void minRetainEventCount_waitMoveSet_contributesZero() {
        // #{*}-0-500 +a: WaitMoveSet has maxMoveCount = 0, so 0 + 1 = 1
        assertEquals(1, retainEventCount("#{*}-0-500 +a"));
    }

    @Test
    void minRetainEventCount_withOptionals_usesMaxMoveCount() {
        // {+a #b?}: maxMoveCount = 2 (required a + optional b)
        assertEquals(2, retainEventCount("{+a #b?}"));
    }

    @Test
    void minRetainEventCount_tapAndPress_mixed() {
        // {a +b}: tap(a) = 2 slots + press(b) = 1 slot, maxMoveCount = 3
        assertEquals(3, retainEventCount("{a +b}"));
    }

}
