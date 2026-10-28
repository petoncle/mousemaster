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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

public class UiHintOrderTest {

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
            ui-hint-mode.hint.type=ui-vision
            """;

    private static UiHintOrder uiHintOrder(Configuration configuration) {
        HintMeshType type = configuration.modeMap()
                                         .get("ui-hint-mode")
                                         .hintMesh()
                                         .type();
        return ((HintMeshType.UiVisionHintMesh) type).order();
    }

    @Test
    void uiHintOrderDefaultsToTopToBottom() throws IOException {
        assertEquals(UiHintOrder.TOP_TO_BOTTOM, uiHintOrder(parse(uiMode)));
    }

    @Test
    void uiHintOrderCanStartFromAnEdgeOrTheMouse() throws IOException {
        assertEquals(UiHintOrder.LEFT_TO_RIGHT, uiHintOrder(
                parse(uiMode + "ui-hint-mode.hint.ui-order=left-to-right\n")));
        assertEquals(UiHintOrder.BOTTOM_TO_TOP, uiHintOrder(
                parse(uiMode + "ui-hint-mode.hint.ui-order=bottom-to-top\n")));
        assertEquals(UiHintOrder.MOUSE, uiHintOrder(
                parse(uiMode + "ui-hint-mode.hint.ui-order=mouse\n")));
    }

    @Test
    void uiOrderDoesNotApplyToGridHints() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> parse("""
                        idle-mode.to.hint-mode=+h
                        hint-mode.to.idle-mode=+esc
                        hint-mode.hint.selection-keys=a b c d
                        hint-mode.hint.type=grid
                        hint-mode.hint.ui-order=left-to-right
                        """));
        assertTrue(exception.getMessage().contains("hint.ui-order"),
                exception.getMessage());
    }

    @Test
    void aColumnOfIconsAFewPixelsApartIsSortedFromTopToBottom() {
        UiElement top = new UiElement(new Rectangle(26, 100, 0, 0));
        UiElement middle = new UiElement(new Rectangle(21, 200, 0, 0));
        UiElement bottom = new UiElement(new Rectangle(24, 300, 0, 0));
        UiElement right = new UiElement(new Rectangle(500, 50, 0, 0));
        assertEquals(List.of(top, middle, bottom, right),
                HintManager.sortedUiElements(List.of(right, bottom, top, middle),
                        UiHintOrder.LEFT_TO_RIGHT, 1, new Point(0, 0)));
    }

    @Test
    void anElementFurtherThanTheGapIsInAnotherColumn() {
        UiElement left = new UiElement(new Rectangle(10, 150, 0, 0));
        UiElement top = new UiElement(new Rectangle(21, 100, 0, 0));
        UiElement bottom = new UiElement(new Rectangle(24, 300, 0, 0));
        assertEquals(List.of(left, top, bottom),
                HintManager.sortedUiElements(List.of(bottom, top, left),
                        UiHintOrder.LEFT_TO_RIGHT, 1, new Point(0, 0)));
    }

    @Test
    void theGapIsScaledWithTheScreen() {
        UiElement left = new UiElement(new Rectangle(40, 150, 0, 0));
        UiElement top = new UiElement(new Rectangle(60, 100, 0, 0));
        UiElement bottom = new UiElement(new Rectangle(60, 300, 0, 0));
        assertEquals(List.of(top, left, bottom),
                HintManager.sortedUiElements(List.of(bottom, top, left),
                        UiHintOrder.LEFT_TO_RIGHT, 4, new Point(0, 0)));
    }

    @Test
    void aRowOfTabsOfDifferentHeightsIsSortedFromLeftToRight() {
        UiElement first = new UiElement(new Rectangle(100, 19, 0, 0));
        UiElement second = new UiElement(new Rectangle(200, 23, 0, 0));
        UiElement third = new UiElement(new Rectangle(300, 22, 0, 0));
        UiElement below = new UiElement(new Rectangle(150, 60, 0, 0));
        assertEquals(List.of(first, second, third, below),
                HintManager.sortedUiElements(List.of(below, third, first, second),
                        UiHintOrder.TOP_TO_BOTTOM, 1, new Point(0, 0)));
        assertEquals(List.of(below, first, second, third),
                HintManager.sortedUiElements(List.of(below, third, first, second),
                        UiHintOrder.BOTTOM_TO_TOP, 1, new Point(0, 0)));
    }

    @Test
    void theElementsNearestTheMouseComeFirst() {
        UiElement near = new UiElement(new Rectangle(510, 505, 0, 0));
        UiElement middle = new UiElement(new Rectangle(600, 500, 0, 0));
        UiElement far = new UiElement(new Rectangle(10, 10, 0, 0));
        assertEquals(List.of(near, middle, far),
                HintManager.sortedUiElements(List.of(far, middle, near),
                        UiHintOrder.MOUSE, 1, new Point(500, 500)));
    }

    @Test
    void theMouseOrderStaysWhenTheModeIsRebuiltAfterTheMouseMoved() throws IOException {
        Mode mode = parse("""
                idle-mode.to.ui-hint-mode=+u
                ui-hint-mode.to.idle-mode=+esc
                ui-hint-mode.hint.selection-keys=a b c d
                ui-hint-mode.hint.type=ui-accessibility
                ui-hint-mode.hint.ui-order=mouse
                """).modeMap().get("ui-hint-mode");
        Future<List<UiElement>> found = CompletableFuture.completedFuture(
                List.of(new UiElement(new Rectangle(10, 10, 0, 0)), new UiElement(new Rectangle(510, 505, 0, 0))));
        ScreenManager screenManager = new ScreenManager(
                () -> Set.of(new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1)));
        HintManager hintManager = new HintManager(Map.of(), screenManager,
                new MouseManager(screenManager, proxy(MouseController.class)),
                proxy(Overlay.class), new UiAutomation() {
                    @Override
                    public Future<List<UiElement>> startFindActiveWindowUiElements() {
                        return found;
                    }

                    @Override
                    public Future<List<UiElement>> startFindUiElementsInArea(Rectangle area) {
                        return found;
                    }
                }, () -> new App("test.exe"), new KeyRedactor(KeyRedaction.NONE), null);
        hintManager.mouseMoved(500, 500);
        hintManager.modeChanged(mode);
        hintManager.completePendingUiHintQuery();
        List<Hint> hints = hintManager.hintMesh().hints();
        assertEquals(510, hints.getFirst().centerX());
        hintManager.mouseMoved(10, 10);
        hintManager.modeChanged(mode);
        assertEquals(hints, hintManager.hintMesh().hints());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> null);
    }

}
