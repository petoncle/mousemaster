package mousemaster;

import mousemaster.platform.MouseController;
import mousemaster.platform.Overlay;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hint command that deselects a key cancels the other commands of its key event, so that
 * backspace deselects or leaves. A wait has no key: its other commands still run.
 */
class HintCommandFromAWaitTest {

    @Test
    void aDeselectionFromAWaitCancelsNoOtherCommand() throws IOException {
        Configuration configuration = ConfigurationParser.parse(PropertiesReader.readPropertiesFile(
                new BufferedReader(new StringReader("""
                        idle-mode.to.grid-mode=+g
                        grid-mode.overlay.grid-cell-sizing=fit
                        grid-mode.overlay.grid-max-row-count=2
                        grid-mode.overlay.grid-max-column-count=2
                        grid-mode.overlay.selection-keys=a b
                        grid-mode.to.other-grid-mode=+o
                        other-grid-mode.overlay=grid-mode.overlay
                        other-grid-mode.deselect-all=wait-0
                        other-grid-mode.press.middle=wait-0
                        """))), KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty"));
        ModeMap modeMap = configuration.modeMap();
        ScreenManager screenManager = new ScreenManager(
                () -> Set.of(new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1)));
        HintManager hintManager = new HintManager(Map.of(), screenManager,
                new MouseManager(screenManager, proxy(MouseController.class)),
                proxy(Overlay.class), null, () -> new App("test.exe"),
                new KeyRedactor(KeyRedaction.NONE), null);
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
        List<Command> ranCommands = new ArrayList<>();
        Instant[] now = {Instant.parse("2026-01-01T00:00:00Z")};
        ComboWatcher[] comboWatcher = new ComboWatcher[1];
        CommandRunner commandRunner = new CommandRunner(null, null, null) {
            @Override
            public boolean runningAtomicCommand() {
                return false;
            }

            @Override
            public void run(Command command, Key eventKey) {
                ranCommands.add(command);
                switch (command) {
                    case Command.SelectHintKey c -> hintManager.selectHintKey(eventKey);
                    case Command.DeselectAllHintKeys c -> hintManager.deselectAllHintKeys();
                    case Command.SwitchMode switchMode ->
                            comboWatcher[0].modeChanged(modeMap.get(switchMode.modeName()));
                    default -> {}
                }
            }
        };
        comboWatcher[0] = new ComboWatcher(commandRunner, hintManager,
                () -> new App("test.exe"), null, () -> now[0], unpressedPreconditionKeys,
                pressedPreconditionKeys, new KeyRedactor(KeyRedaction.NONE), modeMap,
                configuration.initiallySetVariables(), configuration.virtualKeys(),
                configuration.initiallyPressedVirtualKeys(),
                configuration.activeKeyboardLayout());
        comboWatcher[0].setModeListeners(List.of(hintManager));
        comboWatcher[0].modeChanged(modeMap.get(Mode.IDLE_MODE_NAME));
        for (String keyName : List.of("g", "a", "o")) {
            Key key = Key.ofName(keyName);
            comboWatcher[0].keyEvent(new KeyEvent.PressKeyEvent(now[0], key));
            now[0] = now[0].plusMillis(50);
            comboWatcher[0].keyEvent(new KeyEvent.ReleaseKeyEvent(now[0], key));
        }
        assertEquals(List.of(Key.ofName("a")), hintManager.hintMesh().selectedKeySequence());
        now[0] = now[0].plusMillis(10);
        comboWatcher[0].update(0.01);
        assertEquals(List.of(), hintManager.hintMesh().selectedKeySequence());
        assertTrue(ranCommands.contains(new Command.PressMiddle()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
    }

}
