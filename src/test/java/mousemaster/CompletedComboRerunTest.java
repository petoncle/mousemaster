package mousemaster;

import mousemaster.platform.ActiveAppFinder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A combo runs its command once for the events it matched. The preparation keeps those events
 * for a while, so the evaluations that are not driven by a key event, a mode switch and a
 * precondition refresh, must not run the command again.
 */
class CompletedComboRerunTest {

    private static final List<Command> TAP =
            List.of(new Command.PressMiddle(), new Command.ReleaseMiddle());

    private final List<Command> ranCommands = new ArrayList<>();
    private Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private ModeMap modeMap;
    private ComboWatcher comboWatcher;

    private void load(String... lines) {
        Configuration configuration = ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
        modeMap = configuration.modeMap();
        ActiveAppFinder noApp = () -> new App("test.exe");
        Set<Key> unpressedPreconditionKeys = new HashSet<>();
        Set<Key> pressedPreconditionKeys = new HashSet<>();
        for (Mode mode : modeMap.modes()) {
            for (Combo combo : mode.comboMap().commandsByCombo().keySet()) {
                unpressedPreconditionKeys.addAll(
                        combo.precondition().keyPrecondition().unpressedKeySet());
                pressedPreconditionKeys.addAll(combo.precondition()
                                                    .keyPrecondition()
                                                    .pressedKeyPrecondition()
                                                    .allKeys());
            }
        }
        CommandRunner commandRunner = new CommandRunner(null, null, null, null) {
            @Override
            public boolean runningAtomicCommand() {
                return false;
            }

            @Override
            public void run(Command command, Key eventKey) {
                ranCommands.add(command);
                if (command instanceof Command.SwitchMode switchMode)
                    comboWatcher.modeChanged(modeMap.get(switchMode.modeName()));
            }
        };
        comboWatcher = new ComboWatcher(commandRunner, null, noApp, null, () -> now,
                unpressedPreconditionKeys, pressedPreconditionKeys,
                new KeyRedactor(KeyRedaction.NONE), modeMap, configuration.initiallySetVariables(),
                configuration.virtualKeys(), configuration.initiallyPressedVirtualKeys());
        comboWatcher.setModeListeners(List.of());
        comboWatcher.modeChanged(modeMap.get(Mode.IDLE_MODE_NAME));
        ranCommands.clear();
    }

    private void tap(String keyName) {
        Key key = Key.ofName(keyName);
        comboWatcher.keyEvent(new KeyEvent.PressKeyEvent(now, key));
        now = now.plusMillis(50);
        comboWatcher.keyEvent(new KeyEvent.ReleaseKeyEvent(now, key));
    }

    private void tick(long millis) {
        now = now.plusMillis(millis);
        comboWatcher.update(millis / 1000d);
    }

    private List<Command> middleButtonCommands() {
        return ranCommands.stream()
                          .filter(command -> command instanceof Command.PressMiddle ||
                                             command instanceof Command.ReleaseMiddle)
                          .toList();
    }

    /** normal-mode inherits the combos that idle-mode already ran, so switching to it must not
     *  press the button again: the release ends with a wait, so it would not follow. */
    @Test
    void aModeSwitchDoesNotRerunACompletedCombo() {
        load("idle-mode.press.middle=+a-0-150 -a",
                "idle-mode.release.middle=+a-0-150 -a-1",
                "normal-mode.press.middle=+a-0-150 -a",
                "normal-mode.release.middle=+a-0-150 -a-1",
                "idle-mode.to.normal-mode=wait-1000");
        tap("a");
        tick(10);
        assertEquals(TAP, middleButtonCommands());

        tick(1000);
        assertEquals(modeMap.get("normal-mode"), comboWatcher.getMutatedMode());
        assertEquals(TAP, middleButtonCommands(), "the button stays pressed");
    }

    @Test
    void aPreconditionRefreshDoesNotRerunACompletedCombo() {
        load("idle-mode.press.middle=+a-0-150 -a",
                "idle-mode.release.middle=+a-0-150 -a-1",
                "idle-mode.noop.onidling=^{isidling}");
        tap("a");
        tick(10);
        assertEquals(TAP, middleButtonCommands());

        comboWatcher.setVirtualKeyPressed(BuiltInVirtualKey.IS_IDLING, true);
        tick(10);
        assertEquals(TAP, middleButtonCommands(), "the button stays pressed");
    }

    /** The key event that switches the mode still runs the combo the new mode shares with the
     *  old one, on its second pass: only an evaluation without a key event is held back. */
    @Test
    void theSecondPassInTheNewModeStillRunsTheCombo() {
        load("idle-mode.press.middle=+a-0-150 -a",
                "idle-mode.to.normal-mode=+a-0-150 -a",
                "normal-mode.press.middle=+a-0-150 -a");
        tap("a");
        assertEquals(modeMap.get("normal-mode"), comboWatcher.getMutatedMode());
        assertEquals(List.of(new Command.PressMiddle(), new Command.PressMiddle()),
                middleButtonCommands());
    }

    /** The next tap presses the button again: the block is lifted by its key events. */
    @Test
    void theNextTapRunsTheComboAgain() {
        load("idle-mode.press.middle=+a-0-150 -a",
                "idle-mode.release.middle=+a-0-150 -a-1");
        tap("a");
        tick(10);
        tap("a");
        tick(10);
        List<Command> twoTaps = new ArrayList<>(TAP);
        twoTaps.addAll(TAP);
        assertEquals(twoTaps, middleButtonCommands());
    }
}
