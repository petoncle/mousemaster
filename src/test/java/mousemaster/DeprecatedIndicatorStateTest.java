package mousemaster;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The deprecated indicator states are rewritten as mutation branches. */
class DeprecatedIndicatorStateTest {

    private static Configuration parse(String... lines) {
        return ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
    }

    private static String hexColor(String... lines) {
        return parse(lines).modeMap().get(Mode.IDLE_MODE_NAME).indicator().hexColor();
    }

    /** Presses the keys the runtime would press together, then reads the mutated color. */
    private static String mutatedHexColor(Set<Key> pressedKeys, String... lines) {
        Configuration configuration = parse(lines);
        ComboWatcher comboWatcher =
                new ComboWatcher(null, null, () -> new App("test.exe"), null,
                        (Clock) Instant::now, Set.of(), Set.of(), false,
                        configuration.modeMap(), configuration.initiallySetVariables(),
                        configuration.virtualKeys(),
                        configuration.initiallyPressedVirtualKeys());
        comboWatcher.setModeListeners(List.of(new ModeListener() {
            @Override
            public void modeChanged(Mode newMode) {
            }

            @Override
            public void modeTimedOut() {
            }
        }));
        comboWatcher.modeChanged(configuration.modeMap().get(Mode.IDLE_MODE_NAME));
        for (Key pressedKey : pressedKeys)
            comboWatcher.setVirtualKeyPressed(pressedKey, true);
        comboWatcher.update(0.01);
        return comboWatcher.getMutatedMode().indicator().hexColor();
    }

    @Test
    void anIdleStateBecomesThePropertyItself() {
        assertEquals("#00FF00", hexColor("idle-mode.indicator.idle.color=#00FF00"));
    }

    /** An idle value keeps its own branches, since it is the default they mutate. */
    @Test
    void anIdleStateKeepsItsBranches() {
        assertEquals("#00FF00", hexColor(
                "idle-mode.indicator.idle.color=#00FF00 | _{leftshift} -> #FF0000"));
    }

    @Test
    void aMouseStateBecomesABranchOnThatStatesKey() {
        String[] lines = {"idle-mode.indicator.idle.color=#FF0000",
                "idle-mode.indicator.wheel.color=#FFFF00"};
        assertEquals("#FF0000", hexColor(lines));
        assertEquals("#FFFF00",
                mutatedHexColor(Set.of(BuiltInVirtualKey.IS_WHEELING), lines));
    }

    /** Without an idle state there is no default, so the inherited one is left alone. */
    @Test
    void aMouseStateAloneOnlyAddsABranch() {
        String[] lines = {"idle-mode.indicator.wheel.color=#FFFF00"};
        assertEquals("#FF0000", hexColor(lines));
        assertEquals("#FFFF00",
                mutatedHexColor(Set.of(BuiltInVirtualKey.IS_WHEELING), lines));
    }

    /** mouse-press used to feed the three buttons, so a button state has to win over it. */
    @Test
    void aButtonStateWinsOverMousePress() {
        String[] lines = {"idle-mode.indicator.mouse-press.color=#00FF00",
                "idle-mode.indicator.left-mouse-press.color=#0000FF"};
        assertEquals("#00FF00", mutatedHexColor(
                Set.of(BuiltInVirtualKey.IS_MOUSE_PRESSING,
                        BuiltInVirtualKey.IS_MIDDLE_MOUSE_PRESSING), lines));
        assertEquals("#0000FF", mutatedHexColor(
                Set.of(BuiltInVirtualKey.IS_MOUSE_PRESSING,
                        BuiltInVirtualKey.IS_LEFT_MOUSE_PRESSING), lines));
    }

    @Test
    void theSamePropertyCannotBeGivenWithAndWithoutAState() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> hexColor("idle-mode.indicator.color=#FF0000",
                        "idle-mode.indicator.wheel.color=#FFFF00"));
        assertTrue(e.getMessage().contains("both with and without a state"),
                e.getMessage());
    }

    @Test
    void aStateCannotBeGivenTwice() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> hexColor("idle-mode.indicator.wheel.color=#FFFF00",
                        "idle-mode.indicator.wheel.color=#00FF00"));
        assertTrue(e.getMessage().contains("defined twice"), e.getMessage());
    }

    @Test
    void aMouseStateCannotCarryBranches() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> hexColor("idle-mode.indicator.idle.color=#FF0000",
                        "idle-mode.indicator.wheel.color=#FFFF00 | _{leftshift} -> #FF00FF"));
        assertTrue(e.getMessage().contains("cannot carry branches"), e.getMessage());
    }

    @Test
    void anUnknownStateIsStillRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> hexColor("idle-mode.indicator.hover.color=#FFFF00"));
        assertTrue(e.getMessage().contains("Invalid indicator property key"),
                e.getMessage());
    }
}
