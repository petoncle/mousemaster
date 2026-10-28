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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class HintSelectionKeysTest {

    private static final String gridMode = """
            idle-mode.to.grid-mode=+g
            grid-mode.to.idle-mode=+esc
            grid-mode.hint.grid-cell-sizing=fit
            grid-mode.hint.grid-max-row-count=2
            grid-mode.hint.grid-max-column-count=2
            """;

    private static final ScreenManager screenManager = new ScreenManager(
            () -> Set.of(new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1)));

    private static ModeMap parse(String properties) throws IOException {
        return ConfigurationParser.parse(PropertiesReader.readPropertiesFile(
                        new BufferedReader(new StringReader(properties))),
                KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty")).modeMap();
    }

    private static boolean selects(Mode mode) {
        return mode.comboMap().commandsByCombo().values().stream()
                   .flatMap(List::stream)
                   .anyMatch(command -> command instanceof Command.SelectHintKey);
    }

    @Test
    void aMeshWithoutSelectionKeysSelectsNothing() throws IOException {
        assertFalse(selects(parse(gridMode).get("grid-mode")));
    }

    @Test
    void aMeshWithSelectionKeysSelectsWithThem() throws IOException {
        assertTrue(selects(parse(gridMode + "grid-mode.hint.selection-keys=a b c d\n")
                .get("grid-mode")));
    }

    @Test
    void selectionKeysNoneMeansNoSelectionKeys() throws IOException {
        assertFalse(selects(parse(gridMode + "grid-mode.hint.selection-keys=none\n")
                .get("grid-mode")));
    }

    @Test
    void aMeshSelectsWithTheSelectionKeysOfEveryBranch() throws IOException {
        assertTrue(selects(parse(gridMode + """
                virtual-key.labels=released
                grid-mode.hint.selection-keys=none | _{labels} -> a b c d
                """).get("grid-mode")));
    }

    @Test
    void aMeshWithoutSelectionKeysLetsTheirKeysThrough() throws IOException {
        HintManager hintManager = hintManager(
                new MouseManager(screenManager, proxy(MouseController.class)));
        hintManager.modeChanged(parse(gridMode + """
                virtual-key.labels=released
                grid-mode.hint.selection-keys=none | _{labels} -> a b c d
                """).get("grid-mode"));
        hintManager.selectHintKey(Key.ofName("a"));
        assertFalse(hintManager.pollLastHintCommandSupercedesOtherCommands());
    }

    @Test
    void selectWithoutSelectionKeysIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> parse(gridMode + "grid-mode.hint.select=+a\n"));
    }

    @Test
    void aMeshWithoutSelectionKeysHasCellsWithoutLabels() throws IOException {
        HintManager hintManager = hintManager(
                new MouseManager(screenManager, proxy(MouseController.class)));
        hintManager.modeChanged(parse(gridMode).get("grid-mode"));
        assertEquals(4, hintManager.hintMesh().hints().size());
        assertTrue(hintManager.hintMesh().hints().stream()
                              .allMatch(hint -> hint.keySequence().isEmpty()));
        assertFalse(hintManager.showingHintMesh());
    }

    @Test
    void theMouseFollowsTheCenterOfAMeshWithoutSelectionKeys() throws IOException {
        List<Object> mousePosition = new ArrayList<>();
        MouseManager mouseManager = new MouseManager(screenManager,
                (MouseController) Proxy.newProxyInstance(
                        MouseController.class.getClassLoader(),
                        new Class<?>[] {MouseController.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("synchronousMoveTo"))
                                mousePosition.addAll(List.of(args));
                            return null;
                        }));
        Mode mode = parse(gridMode + """
                grid-mode.mouse.smooth-jump-enabled=false
                grid-mode.hint.mouse-movement=mouse-follows-hint-grid-center
                """).get("grid-mode");
        mouseManager.modeChanged(mode);
        hintManager(mouseManager).modeChanged(mode);
        assertEquals(List.of(960, 540), mousePosition);
    }

    private static HintManager hintManager(MouseManager mouseManager) {
        return new HintManager(Map.of(), screenManager, mouseManager,
                proxy(Overlay.class), new UiAutomation() {
                    @Override
                    public Future<List<UiElement>> startFindActiveWindowUiElements() {
                        return CompletableFuture.completedFuture(List.of());
                    }

                    @Override
                    public Future<List<UiElement>> startFindUiElementsInArea(Rectangle area) {
                        return CompletableFuture.completedFuture(List.of());
                    }
                }, () -> new App("test.exe"), new KeyRedactor(KeyRedaction.NONE), null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> null);
    }

}
