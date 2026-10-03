package mousemaster;

import mousemaster.platform.ActiveAppFinder;
import mousemaster.platform.MouseController;
import mousemaster.platform.Overlay;
import mousemaster.platform.UiAutomation;
import mousemaster.platform.UiAutomation.UiElement;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/** A ui hint query that finds nothing presses ishintmeshempty, which a to combo can read. */
class HintMeshEmptyVirtualKeyTest {

    private static final String CONFIGURATION = """
            idle-mode.to.ui-hint-mode=+u
            ui-hint-mode.to.all-screens-ui-hint-mode=+g
            ui-hint-mode.hint.selection-keys=a b c d
            ui-hint-mode.hint.type=ui
            ui-hint-mode.to.normal-mode=_{ishintmeshempty}
            all-screens-ui-hint-mode.hint.selection-keys=a b c d
            all-screens-ui-hint-mode.hint.type=ui
            all-screens-ui-hint-mode.hint.ui-area=all-screens
            all-screens-ui-hint-mode.to.idle-mode=+esc
            normal-mode.to.idle-mode=+esc
            """;

    private final List<Command> ranCommands = new ArrayList<>();
    private final List<String> overlayCalls = new ArrayList<>();
    private final Deque<Future<List<UiElement>>> queries = new ArrayDeque<>();
    private ModeMap modeMap;
    private ComboWatcher comboWatcher;
    private HintManager hintManager;

    @SafeVarargs
    private void load(Future<List<UiElement>>... uiElementQueries) {
        queries.addAll(List.of(uiElementQueries));
        Configuration configuration =
                ConfigurationParser.parse(CONFIGURATION.lines().toList(),
                        KeyboardLayout.keyboardLayout("00000409", null));
        modeMap = configuration.modeMap();
        ActiveAppFinder noApp = () -> new App("test.exe");
        ScreenManager screenManager = new ScreenManager(
                () -> Set.of(new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1)));
        hintManager = new HintManager(
                configuration.positionHistoryConfigurationByName(), screenManager,
                new MouseManager(screenManager, proxy(MouseController.class)),
                overlay(), uiAutomation(), noApp,
                new KeyRedactor(KeyRedaction.NONE), null);
        CommandRunner commandRunner = new CommandRunner(null, null, hintManager, null) {
            @Override
            public boolean runningAtomicCommand() {
                return false;
            }

            @Override
            public void run(Command command, Key eventKey) {
                ranCommands.add(command);
            }
        };
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
        comboWatcher = new ComboWatcher(commandRunner, hintManager, noApp, screenManager,
                (Clock) Instant::now, unpressedPreconditionKeys, pressedPreconditionKeys,
                new KeyRedactor(KeyRedaction.NONE), modeMap,
                configuration.initiallySetVariables(), configuration.virtualKeys(),
                configuration.initiallyPressedVirtualKeys());
        comboWatcher.setModeListeners(List.of());
        switchMode(Mode.IDLE_MODE_NAME);
        // The first update sees the active app change, which runs the combos without a move.
        comboWatcher.update(0.01);
        switchMode("ui-hint-mode");
    }

    private void switchMode(String modeName) {
        comboWatcher.modeChanged(modeMap.get(modeName));
        hintManager.modeChanged(modeMap.get(modeName));
    }

    private void tick() {
        hintManager.completePendingUiHintQuery();
        comboWatcher.updateBuiltInVirtualKeys(
                new MouseState(new MouseManager(null, proxy(MouseController.class))),
                new KeyboardState(null) {
                    @Override
                    public boolean pressingUnhandledKeyInCurrentMode() {
                        return false;
                    }
                });
        comboWatcher.update(0.01);
    }

    private UiAutomation uiAutomation() {
        return new UiAutomation() {
            @Override
            public Future<List<UiElement>> startFindActiveWindowUiElements() {
                return queries.poll();
            }

            @Override
            public Future<List<UiElement>> startFindUiElementsInArea(Rectangle area) {
                return queries.poll();
            }
        };
    }

    private Overlay overlay() {
        return (Overlay) Proxy.newProxyInstance(Overlay.class.getClassLoader(),
                new Class<?>[] {Overlay.class}, (proxy, method, args) -> {
                    overlayCalls.add(method.getName());
                    return null;
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> null);
    }

    private static Future<List<UiElement>> found(UiElement... uiElements) {
        return CompletableFuture.completedFuture(List.of(uiElements));
    }

    @Test
    void anEmptyMeshRunsTheComboThatReadsTheKey() {
        load(found());
        tick();
        assertTrue(hintManager.hintMeshEmpty());
        assertEquals(List.of(new Command.SwitchMode("normal-mode")), ranCommands);
    }

    /** Showing it would flash the background of a mesh the combo is about to leave. */
    @Test
    void anEmptyMeshIsNotShown() {
        load(found());
        tick();
        assertFalse(overlayCalls.contains("setHintMesh"), overlayCalls.toString());
    }

    @Test
    void aMeshWithAHintDoesNot() {
        load(found(new UiElement(10, 10)));
        tick();
        assertFalse(hintManager.hintMeshEmpty());
        assertEquals(List.of(), ranCommands);
        assertTrue(overlayCalls.contains("setHintMesh"), overlayCalls.toString());
    }

    @Test
    void theMeshLeftOnScreenByAQueryStillRunningIsNotAnEmptyMesh() {
        load(found(), new CompletableFuture<>());
        tick();
        switchMode("all-screens-ui-hint-mode");
        assertFalse(hintManager.hintMeshEmpty());
    }
}
