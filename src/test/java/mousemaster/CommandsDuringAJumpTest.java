package mousemaster;

import mousemaster.platform.KeyboardController;
import mousemaster.platform.MouseController;
import mousemaster.platform.Overlay;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * While the mouse jumps, a command that acts outside mousemaster waits for the jump to end, with
 * the commands after it. The others run at once, before the mode switch of their key.
 */
class CommandsDuringAJumpTest {

    private static final String hintModeThatSavesItsHints = """
            idle-mode.to.hint-mode=+h
            hint-mode.mouse.smooth-jump-velocity=1000
            hint-mode.overlay.selection-keys=a b c d
            hint-mode.overlay.grid-cell-sizing=fit
            hint-mode.overlay.grid-max-row-count=2
            hint-mode.overlay.grid-max-column-count=2
            hint-mode.overlay.target-follows-overlay-center=true
            hint-mode.toggle-save.position-history=+a
            hint-mode.to.save-after-hint-mode=+a
            save-after-hint-mode.overlay=hint-mode.overlay
            save-after-hint-mode.to.hint-mode=wait-0
            """;

    private Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private Point mouseMove;
    private MouseManager mouseManager;
    private HintManager hintManager;
    private KeyboardManager keyboardManager;
    private MacroPlayer macroPlayer;
    private ModeController modeController;

    private void start(String properties) throws IOException {
        Configuration configuration = ConfigurationParser.parse(
                PropertiesReader.readPropertiesFile(
                        new BufferedReader(new StringReader(properties))),
                KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty"));
        ModeMap modeMap = configuration.modeMap();
        ScreenManager screenManager = new ScreenManager(
                () -> Set.of(new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1)));
        mouseManager = new MouseManager(screenManager, (MouseController) Proxy.newProxyInstance(
                MouseController.class.getClassLoader(), new Class<?>[] {MouseController.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("synchronousMoveTo"))
                        mouseMove = new Point((int) args[0], (int) args[1]);
                    return method.getReturnType() == boolean.class ? false : null;
                }));
        KeyRedactor keyRedactor = new KeyRedactor(KeyRedaction.NONE);
        hintManager = new HintManager(configuration.positionHistoryConfigurationByName(),
                screenManager, mouseManager, proxy(Overlay.class), null,
                () -> new App("test.exe"), keyRedactor, null);
        CommandRunner commandRunner = new CommandRunner(mouseManager, hintManager, null);
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
        ComboWatcher comboWatcher = new ComboWatcher(commandRunner, hintManager,
                () -> new App("test.exe"), screenManager, () -> now, unpressedPreconditionKeys,
                pressedPreconditionKeys, keyRedactor, modeMap,
                configuration.initiallySetVariables(), configuration.virtualKeys(),
                configuration.initiallyPressedVirtualKeys(),
                configuration.activeKeyboardLayout());
        KeyboardController keyboard = proxy(KeyboardController.class);
        keyboardManager = new KeyboardManager(comboWatcher, hintManager,
                new KeyRegurgitator(keyboard, keyRedactor), () -> now);
        macroPlayer = new MacroPlayer(() -> now, comboWatcher, keyboardManager, keyboard,
                keyRedactor, configuration.activeKeyboardLayout());
        keyboardManager.setMacroPlayer(macroPlayer);
        AnimationPlayer animationPlayer =
                new AnimationPlayer(configuration.animationConfigurationByName());
        comboWatcher.setModeListeners(List.of(mouseManager, hintManager));
        modeController = new ModeController(modeMap, mouseManager, new MouseState(mouseManager),
                new KeyboardState(keyboardManager), hintManager, comboWatcher, animationPlayer);
        commandRunner.setModeController(modeController);
        commandRunner.setMacroPlayer(macroPlayer);
        commandRunner.setAnimationPlayer(animationPlayer);
        hintManager.setModeController(modeController);
        modeController.switchMode(Mode.IDLE_MODE_NAME);
        mouseManager.mouseMoved(100, 100);
        hintManager.mouseMoved(100, 100);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> null);
    }

    /** One iteration of the main loop: the platform reports the move after the managers ran. */
    private void tick() {
        now = now.plusMillis(10);
        modeController.update(0.01);
        mouseManager.update(0.01);
        hintManager.update(0.01);
        keyboardManager.update(0.01);
        if (mouseMove != null) {
            mouseManager.mouseMoved((int) mouseMove.x(), (int) mouseMove.y());
            hintManager.mouseMoved((int) mouseMove.x(), (int) mouseMove.y());
            mouseMove = null;
        }
        macroPlayer.update(0.01);
    }

    private void tap(String keyName) {
        Key key = Key.ofName(keyName);
        keyboardManager.keyEvent(new KeyEvent.PressKeyEvent(now, key));
        tick();
        keyboardManager.keyEvent(new KeyEvent.ReleaseKeyEvent(now, key));
        tick();
    }

    private void tickUntilTheJumpEnds() {
        while (mouseManager.jumping())
            tick();
        tick();
    }

    private List<Point> savedPositions() {
        return hintManager.positionHistory("position-history").positions();
    }

    @Test
    void aHintPickedDuringAJumpIsSavedBeforeItsModeSwitch() throws IOException {
        start(hintModeThatSavesItsHints);
        tap("h");
        assertTrue(mouseManager.jumping());
        tap("a");
        tickUntilTheJumpEnds();
        assertEquals(List.of(new Point(480, 270)), savedPositions());
    }

    @Test
    void aVirtualKeyMacroDoesNotHoldBackTheSaveAfterIt() throws IOException {
        start(hintModeThatSavesItsHints + """
                virtual-key.ismarked=released
                hint-mode.macro.mark=^{ismarked} wait-0 -> #ismarked
                """);
        tap("h");
        assertTrue(mouseManager.jumping());
        tap("a");
        tickUntilTheJumpEnds();
        assertEquals(List.of(new Point(480, 270)), savedPositions());
    }

    @Test
    void aSaveWaitsBehindATargetMoveThatWaitsForTheJump() throws IOException {
        start("""
                idle-mode.to.grid-mode=+g
                grid-mode.mouse.smooth-jump-velocity=1000
                grid-mode.overlay.grid-cell-sizing=fit
                grid-mode.overlay.grid-max-row-count=2
                grid-mode.overlay.grid-max-column-count=2
                grid-mode.move-target-to.cell-right=+r
                grid-mode.save.position-history=+s
                """);
        tap("g");
        tap("r");
        assertTrue(mouseManager.jumping());
        tap("r");
        tap("s");
        tickUntilTheJumpEnds();
        tickUntilTheJumpEnds();
        assertEquals(List.of(new Point(1919, 100)), savedPositions());
    }

}
