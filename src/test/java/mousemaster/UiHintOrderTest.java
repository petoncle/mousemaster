package mousemaster;

import mousemaster.platform.UiAutomation.UiElement;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

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
    void uiHintOrderCanStartFromAnyEdge() throws IOException {
        assertEquals(UiHintOrder.LEFT_TO_RIGHT, uiHintOrder(
                parse(uiMode + "ui-hint-mode.hint.ui-order=left-to-right\n")));
        assertEquals(UiHintOrder.BOTTOM_TO_TOP, uiHintOrder(
                parse(uiMode + "ui-hint-mode.hint.ui-order=bottom-to-top\n")));
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
        UiElement top = new UiElement(26, 100);
        UiElement middle = new UiElement(21, 200);
        UiElement bottom = new UiElement(24, 300);
        UiElement right = new UiElement(500, 50);
        assertEquals(List.of(top, middle, bottom, right),
                HintManager.sortedUiElements(List.of(right, bottom, top, middle),
                        UiHintOrder.LEFT_TO_RIGHT, 1));
    }

    @Test
    void anElementFurtherThanTheGapIsInAnotherColumn() {
        UiElement left = new UiElement(10, 150);
        UiElement top = new UiElement(21, 100);
        UiElement bottom = new UiElement(24, 300);
        assertEquals(List.of(left, top, bottom),
                HintManager.sortedUiElements(List.of(bottom, top, left),
                        UiHintOrder.LEFT_TO_RIGHT, 1));
    }

    @Test
    void theGapIsScaledWithTheScreen() {
        UiElement left = new UiElement(40, 150);
        UiElement top = new UiElement(60, 100);
        UiElement bottom = new UiElement(60, 300);
        assertEquals(List.of(top, left, bottom),
                HintManager.sortedUiElements(List.of(bottom, top, left),
                        UiHintOrder.LEFT_TO_RIGHT, 4));
    }

    @Test
    void aRowOfTabsOfDifferentHeightsIsSortedFromLeftToRight() {
        UiElement first = new UiElement(100, 19);
        UiElement second = new UiElement(200, 23);
        UiElement third = new UiElement(300, 22);
        UiElement below = new UiElement(150, 60);
        assertEquals(List.of(first, second, third, below),
                HintManager.sortedUiElements(List.of(below, third, first, second),
                        UiHintOrder.TOP_TO_BOTTOM, 1));
        assertEquals(List.of(below, first, second, third),
                HintManager.sortedUiElements(List.of(below, third, first, second),
                        UiHintOrder.BOTTOM_TO_TOP, 1));
    }

}
