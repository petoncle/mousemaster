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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LabelLengthTest {

    private static Mode mode(String labelLength) throws IOException {
        List<String> lines = PropertiesReader.readPropertiesFile(
                new BufferedReader(new StringReader("""
                        idle-mode.to.ui-hint-mode=+u
                        ui-hint-mode.to.idle-mode=+esc
                        ui-hint-mode.overlay.selection-keys=a b c d
                        ui-hint-mode.overlay.type=ui-accessibility
                        """ + labelLength)));
        return ConfigurationParser.parse(lines,
                                         KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty"))
                                  .modeMap().get("ui-hint-mode");
    }

    private static List<String> labels(Mode mode, int elementCount) {
        List<UiElement> elements = new ArrayList<>();
        for (int i = 0; i < elementCount; i++)
            elements.add(new UiElement(new Rectangle(100, 10 + 50 * i, 0, 0)));
        HintManager hintManager = hintManager(elements);
        hintManager.modeChanged(mode);
        hintManager.completePendingUiHintQuery();
        return hintManager.hintMesh()
                          .hints()
                          .stream()
                          .map(hint -> String.join("",
                                  hint.keySequence().stream().map(Key::name).toList()))
                          .toList();
    }

    @Test
    void labelLengthDefaultsToShortest() throws IOException {
        assertEquals(LabelLength.SHORTEST, mode("").hintMesh().type().labelLength());
        assertEquals(LabelLength.EQUAL, mode("ui-hint-mode.overlay.label-length=equal\n")
                .hintMesh().type().labelLength());
    }

    @Test
    void theShortestLabelsComeFirst() throws IOException {
        assertEquals(List.of("a", "b", "c", "da", "db", "dc"), labels(mode(""), 6));
    }

    @Test
    void fewerHintsThanKeysGetOneKeyEach() throws IOException {
        assertEquals(List.of("a", "b", "c"),
                labels(mode("ui-hint-mode.overlay.label-length=equal\n"), 3));
    }

    @Test
    void equalLabelsOfTheFirstKeysComeFirst() throws IOException {
        assertEquals(List.of("aa", "ab", "ba", "bb", "ac", "ca", "bc", "cb", "cc", "ad"),
                labels(mode("ui-hint-mode.overlay.label-length=equal\n"), 10));
    }

    @Test
    void moreHintsThanTwoKeyLabelsGetThreeKeysEach() throws IOException {
        assertEquals(List.of("aaa", "aab", "aba", "baa", "abb", "bab", "bba", "bbb", "aac"),
                labels(mode("ui-hint-mode.overlay.label-length=equal\n"), 17).subList(0, 9));
    }

    private static HintManager hintManager(List<UiElement> elements) {
        Future<List<UiElement>> found = CompletableFuture.completedFuture(elements);
        ScreenManager screenManager = new ScreenManager(
                () -> Set.of(new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1)));
        return new HintManager(Map.of(), screenManager,
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
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> null);
    }

}
