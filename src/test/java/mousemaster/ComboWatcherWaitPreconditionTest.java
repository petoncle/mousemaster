package mousemaster;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A wait does not hide the keys its combo's precondition names.
 */
class ComboWatcherWaitPreconditionTest {

    private static final List<Command> PRESS = List.of(new Command.PressMiddle());

    private final List<Command> ranCommands = new ArrayList<>();
    private Instant now = Instant.parse("2020-01-01T00:00:00Z");
    private ComboWatcher comboWatcher;

    private void load(String... lines) {
        Configuration configuration = ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
        ModeMap modeMap = configuration.modeMap();
        Set<Key> pressedPreconditionKeys = new HashSet<>();
        for (Mode mode : modeMap.modes())
            for (Combo combo : mode.comboMap().commandsByCombo().keySet())
                pressedPreconditionKeys.addAll(combo.precondition()
                                                    .keyPrecondition()
                                                    .pressedKeyPrecondition()
                                                    .allKeys());
        CommandRunner commandRunner = new CommandRunner(null, null, null) {
            @Override
            public boolean runningAtomicCommand() {
                return false;
            }

            @Override
            public void run(Command command, Key eventKey) {
                ranCommands.add(command);
            }
        };
        comboWatcher = new ComboWatcher(commandRunner, null, () -> new App("test.exe"), null,
                () -> now, Set.of(), pressedPreconditionKeys, new KeyRedactor(KeyRedaction.NONE),
                modeMap, configuration.initiallySetVariables(), configuration.virtualKeys(),
                configuration.initiallyPressedVirtualKeys(),
                configuration.activeKeyboardLayout());
        comboWatcher.setModeListeners(List.of());
        comboWatcher.modeChanged(modeMap.get(Mode.IDLE_MODE_NAME));
    }

    private void press(String keyName) {
        comboWatcher.keyEvent(new KeyEvent.PressKeyEvent(now, Key.ofName(keyName)));
    }

    private void elapse(long millis) {
        now = now.plus(Duration.ofMillis(millis));
        comboWatcher.update(millis / 1000d);
    }

    @Test
    void aKeyAfterTheWaitRunsWithThePreconditionHeld() {
        load("idle-mode.press.middle=_{leftctrl} #{*}-0-500 +b");
        press("leftctrl");
        press("b");
        elapse(10);
        assertEquals(PRESS, ranCommands);
    }

    @Test
    void aKeyAfterTheWaitDoesNotRunWithoutThePrecondition() {
        load("idle-mode.press.middle=_{leftctrl} #{*}-0-500 +b");
        press("b");
        elapse(10);
        assertEquals(List.of(), ranCommands);
    }

    @Test
    void aWaitAloneRunsOnceThePreconditionIsHeld() {
        load("idle-mode.press.middle=_{leftctrl} #{*}-0");
        elapse(10);
        assertEquals(List.of(), ranCommands);

        press("leftctrl");
        elapse(10);
        assertEquals(PRESS, ranCommands);
    }

    @Test
    void aTrailingWaitRunsWithThePreconditionHeld() {
        load("idle-mode.press.middle=_{leftctrl} +b #{*}-0-500");
        press("leftctrl");
        press("b");
        elapse(10);
        assertEquals(PRESS, ranCommands);
    }

    @Test
    void theWaitStillHidesAKeyThePreconditionDoesNotName() {
        load("idle-mode.press.middle=_{leftctrl} #{*}-0-500 +b");
        press("z");
        press("leftctrl");
        press("b");
        elapse(10);
        assertEquals(PRESS, ranCommands, "z is ignored by the wait, so it is not an extra key");
    }
}
