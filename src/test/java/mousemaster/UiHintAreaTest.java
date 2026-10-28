package mousemaster;

import mousemaster.platform.MouseController;
import mousemaster.platform.Overlay;
import mousemaster.platform.UiAutomation;
import mousemaster.platform.UiAutomation.UiElement;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

public class UiHintAreaTest {

    private static Configuration parse(String properties) throws IOException {
        List<String> lines = PropertiesReader.readPropertiesFile(
                new BufferedReader(new StringReader(properties)));
        return ConfigurationParser.parse(lines,
                KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty"));
    }

    private static final String uiMode = """
            idle-mode.to.ui-hint-mode=+u
            ui-hint-mode.to.idle-mode=+esc
            ui-hint-mode.hint.selection-keys=a b c d
            ui-hint-mode.hint.type=ui
            """;

    private static HintGridArea uiHintArea(Configuration configuration) {
        HintMeshType type = configuration.modeMap()
                                         .get("ui-hint-mode")
                                         .hintMesh()
                                         .type();
        return ((HintMeshType.UiAccessibilityHintMesh) type).area();
    }

    /** Same default as a grid hint mesh: the area does not depend on the hint type. */
    @Test
    void uiHintAreaDefaultsToActiveScreen() throws IOException {
        assertEquals(HintGridAreaSizeSource.ACTIVE_SCREEN,
                uiHintArea(parse(uiMode)).size().source());
    }

    @Test
    void uiHintAreaCanBeAWindowOrEveryScreen() throws IOException {
        assertEquals(HintGridAreaSizeSource.ACTIVE_WINDOW, uiHintArea(
                parse(uiMode + "ui-hint-mode.hint.area=active-window\n")).size().source());
        assertEquals(HintGridAreaSizeSource.ALL_SCREENS, uiHintArea(
                parse(uiMode + "ui-hint-mode.hint.area=all-screens\n")).size().source());
    }

    @Test
    void uiHintAreaCanBeScaledAndCentered() throws IOException {
        assertEquals(new HintGridArea(
                        new HintGridAreaSize(HintGridAreaSizeSource.ACTIVE_SCREEN, 0.3, 0.4, 0, 0, 0, 0),
                        HintGridAreaCenter.MOUSE),
                uiHintArea(parse(uiMode + """
                        ui-hint-mode.hint.area-width-percent=0.3
                        ui-hint-mode.hint.area-height-percent=0.4
                        ui-hint-mode.hint.area-center=mouse
                        """)));
    }

    @Test
    void theElementsAreLookedForInTheScaledAreaAroundItsCenter() throws IOException {
        Mode mode = parse(uiMode + """
                ui-hint-mode.hint.area-width-percent=0.5
                ui-hint-mode.hint.area-height-percent=0.5
                ui-hint-mode.hint.area-center=mouse
                """).modeMap().get("ui-hint-mode");
        List<Rectangle> areas = new ArrayList<>();
        HintManager hintManager = hintManager(areas);
        hintManager.mouseMoved(300, 200);
        hintManager.modeChanged(mode);
        assertEquals(List.of(new Rectangle(-180, -70, 960, 540)), areas);
    }

    @Test
    void theElementsAreLookedForInTheLastSelectedHintCell() throws IOException {
        ModeMap modeMap = parse("""
                idle-mode.to.hint-mode=+h
                hint-mode.to.ui-hint-mode=+a
                hint-mode.hint.selection-keys=a b c d
                hint-mode.hint.mouse-movement=no-movement
                hint-mode.hint.grid-cell-sizing=fit
                hint-mode.hint.grid-max-row-count=2
                hint-mode.hint.grid-max-column-count=2
                ui-hint-mode.to.idle-mode=+esc
                ui-hint-mode.hint.selection-keys=a b c d
                ui-hint-mode.hint.type=ui
                ui-hint-mode.hint.area=last-selected-hint-cell
                """).modeMap();
        List<Rectangle> areas = new ArrayList<>();
        HintManager hintManager = hintManager(areas);
        hintManager.modeChanged(modeMap.get("hint-mode"));
        for (Key key : hintManager.hintMesh().hints().getLast().keySequence())
            hintManager.selectHintKey(key);
        hintManager.modeChanged(modeMap.get("ui-hint-mode"));
        assertEquals(List.of(new Rectangle(960, 540, 960, 540)), areas);
    }

    @Test
    void theInsetsCutMarginsFromTheArea() throws IOException {
        ModeMap modeMap = parse(uiMode + """
                ui-hint-mode.hint.area-top-inset=15
                ui-hint-mode.hint.area-left-inset=10
                """).modeMap();
        List<Rectangle> areas = new ArrayList<>();
        hintManager(areas).modeChanged(modeMap.get("ui-hint-mode"));
        assertEquals(List.of(new Rectangle(10, 15, 1910, 1065)), areas);
    }

    private static HintManager hintManager(List<Rectangle> areas) {
        ScreenManager screenManager = new ScreenManager(
                () -> Set.of(new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1)));
        return new HintManager(Map.of(), screenManager,
                new MouseManager(screenManager, proxy(MouseController.class)),
                proxy(Overlay.class), new UiAutomation() {
                    @Override
                    public Future<List<UiElement>> startFindActiveWindowUiElements() {
                        return CompletableFuture.completedFuture(List.of());
                    }

                    @Override
                    public Future<List<UiElement>> startFindUiElementsInArea(Rectangle area) {
                        areas.add(area);
                        return CompletableFuture.completedFuture(List.of());
                    }
                }, () -> new App("test.exe"), new KeyRedactor(KeyRedaction.NONE), null);
    }

    @Test
    void theDeprecatedGridAreaAndUiAreaStillSetTheArea() throws IOException {
        assertEquals(HintGridAreaSizeSource.ACTIVE_WINDOW, uiHintArea(
                parse(uiMode + "ui-hint-mode.hint.ui-area=active-window\n")).size().source());
        HintMeshType type = parse("""
                idle-mode.to.hint-mode=+h
                hint-mode.to.idle-mode=+esc
                hint-mode.hint.selection-keys=a b c d
                hint-mode.hint.type=grid
                hint-mode.hint.grid-area-center=mouse
                """).modeMap().get("hint-mode").hintMesh().type();
        assertEquals(HintGridAreaCenter.MOUSE,
                ((HintMeshType.GridHintMesh) type).area().center());
    }

    /** The area is a component of the ui hint mesh, so it is mutated in place. */
    @Test
    void uiHintAreaFollowsAVariablePrecondition() {
        Configuration configuration = ConfigurationParser.parse(List.of(
                        "idle-mode.hint.type=ui",
                        "idle-mode.hint.selection-keys=a b c d",
                        "idle-mode.hint.area=active-window | _{isidling} -> all-screens"),
                KeyboardLayout.keyboardLayout("00000409", null));
        ComboWatcher comboWatcher =
                new ComboWatcher(null, null, () -> new App("test.exe"), null, Instant::now,
                        Set.of(), Set.of(), new KeyRedactor(KeyRedaction.NONE),
                        configuration.modeMap(),
                        configuration.initiallySetVariables(), configuration.virtualKeys(),
                        configuration.initiallyPressedVirtualKeys(),
                        configuration.activeKeyboardLayout());
        comboWatcher.setModeListeners(List.of(new ModeListener() {
            @Override
            public void modeChanged(Mode newMode) {
            }

            @Override
            public void modeTimedOut() {
            }
        }));
        comboWatcher.modeChanged(configuration.modeMap().get(Mode.IDLE_MODE_NAME));
        comboWatcher.update(0.01);
        assertEquals(HintGridAreaSizeSource.ACTIVE_WINDOW, mutatedUiHintAreaSource(comboWatcher));
        comboWatcher.setVirtualKeyPressed(BuiltInVirtualKey.IS_IDLING, true);
        comboWatcher.update(0.01);
        assertEquals(HintGridAreaSizeSource.ALL_SCREENS, mutatedUiHintAreaSource(comboWatcher));
    }

    private static HintGridAreaSizeSource mutatedUiHintAreaSource(ComboWatcher comboWatcher) {
        return ((HintMeshType.UiAccessibilityHintMesh) comboWatcher.getMutatedMode()
                                                      .hintMesh()
                                                      .type()).area().size().source();
    }

    @Test
    void gridHintAreaStillDefaultsToActiveScreen() throws IOException {
        Configuration configuration = parse("""
                idle-mode.to.hint-mode=+h
                hint-mode.to.idle-mode=+esc
                hint-mode.hint.selection-keys=a b c d
                hint-mode.hint.type=grid
                """);
        HintMeshType type =
                configuration.modeMap().get("hint-mode").hintMesh().type();
        assertEquals(HintGridAreaSizeSource.ACTIVE_SCREEN,
                ((HintMeshType.GridHintMesh) type).area().size().source());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> null);
    }

}
