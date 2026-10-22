package mousemaster;

import mousemaster.KeyboardManager.EatAndRegurgitates;
import mousemaster.KeyboardManager.Regurgitate;
import mousemaster.platform.KeyboardController;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The modifier key of a key typed with it, as shift for ? on us-qwerty, is passed through
 * without breaking the combo, and a macro presses it to type the key.
 */
class TypedKeyKeyboardTest {

    private final Instant now = Instant.parse("2020-01-01T00:00:00Z");
    private final List<Command> ranCommands = new ArrayList<>();
    private final List<ResolvedMacroMove> sentMoves = new ArrayList<>();
    private KeyboardManager keyboardManager;
    private MacroPlayer macroPlayer;
    private ModeMap modeMap;

    private void load(String... lines) {
        Configuration configuration = ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayoutByShortName.get("us-qwerty"));
        modeMap = configuration.modeMap();
        Set<Key> pressedPreconditionKeys = new HashSet<>();
        for (Mode mode : modeMap.modes())
            for (Combo combo : mode.comboMap().commandsByCombo().keySet())
                pressedPreconditionKeys.addAll(combo.precondition()
                                                    .keyPrecondition()
                                                    .pressedKeyPrecondition()
                                                    .allKeys());
        CommandRunner commandRunner = new CommandRunner(null, null, null, null) {
            @Override
            public boolean runningAtomicCommand() {
                return false;
            }

            @Override
            public void run(Command command, Key eventKey) {
                ranCommands.add(command);
            }
        };
        ComboWatcher comboWatcher = new ComboWatcher(commandRunner, null,
                () -> new App("test.exe"), null, () -> now, Set.of(), pressedPreconditionKeys,
                new KeyRedactor(KeyRedaction.NONE), modeMap,
                configuration.initiallySetVariables(), configuration.virtualKeys(),
                configuration.initiallyPressedVirtualKeys(),
                configuration.activeKeyboardLayout());
        comboWatcher.setModeListeners(List.of());
        comboWatcher.modeChanged(modeMap.get(Mode.IDLE_MODE_NAME));
        KeyboardController keyboard = new KeyboardController() {
            @Override
            public void update(double delta) {
            }

            @Override
            public void reset() {
            }

            @Override
            public void sendInputMoves(List<ResolvedMacroMove> moves, boolean startRepeat) {
                sentMoves.addAll(moves);
            }

            @Override
            public void keyPressedNotEaten(Key key) {
            }

            @Override
            public void keyReleasedNotEaten(Key key) {
            }

            @Override
            public void recordEarlyReleaseForQueuedPress(Key key) {
            }

            @Override
            public void clearEarlyReleaseForQueuedPress(Key key) {
            }
        };
        keyboardManager = new KeyboardManager(comboWatcher, null,
                new KeyRegurgitator(keyboard, new KeyRedactor(KeyRedaction.NONE)), () -> now);
        macroPlayer = new MacroPlayer(() -> now, comboWatcher, keyboardManager, keyboard,
                new KeyRedactor(KeyRedaction.NONE), configuration.activeKeyboardLayout());
        keyboardManager.setMacroPlayer(macroPlayer);
    }

    private EatAndRegurgitates press(String keyName) {
        return keyboardManager.keyEvent(new KeyEvent.PressKeyEvent(now, Key.ofName(keyName)));
    }

    @Test
    void shiftIsPassedThroughWithoutRegurgitatingTheKeyBeforeIt() {
        load("idle-mode.press.middle=+a +questionmark");
        assertTrue(press("a").mustBeEaten());
        EatAndRegurgitates shift = press("leftshift");
        assertFalse(shift.mustBeEaten());
        assertEquals(List.of(), shift.regurgitates());
        assertTrue(press("/").mustBeEaten());
        assertEquals(List.of(new Command.PressMiddle()), ranCommands);
    }

    @Test
    void shiftAloneIsNotAnUnhandledKey() {
        load("idle-mode.press.middle=+questionmark");
        press("leftshift");
        assertFalse(keyboardManager.pressingUnhandledKeyInCurrentMode());
    }

    @Test
    void anotherKeyRegurgitatesTheKeyBeforeIt() {
        load("idle-mode.press.middle=+a +questionmark");
        press("a");
        assertEquals(List.of(new Regurgitate(Key.ofName("a"), false)),
                press("b").regurgitates());
    }

    @Test
    void aMacroTypesTheCharacterWithShift() {
        load("idle-mode.macro.question=+f1 -> +questionmark -questionmark");
        Macro macro = modeMap.get(Mode.IDLE_MODE_NAME)
                             .comboMap()
                             .commandsByCombo()
                             .values()
                             .stream()
                             .flatMap(List::stream)
                             .map(command -> ((Command.MacroCommand) command).macro())
                             .findFirst()
                             .orElseThrow();
        macroPlayer.submit(macro.resolve(new AliasResolution(Map.of())));
        macroPlayer.update(0.01);
        assertEquals("[+leftshift, +/, -/, -leftshift]", sentMoves.toString());
    }

}
