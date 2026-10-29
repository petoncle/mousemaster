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

class HintSelectionCarryOverTest {

    @Test
    void aSelectedHintIsNotSelectedInTheModeItLeadsTo() throws IOException {
        ModeMap modeMap = ConfigurationParser.parse(PropertiesReader.readPropertiesFile(
                new BufferedReader(new StringReader("""
                        idle-mode.to.ui-hint-mode=+u
                        ui-hint-mode.to.idle-mode=+esc
                        ui-hint-mode.to.click-mode=+x
                        ui-hint-mode.hint.selection-keys=a b
                        ui-hint-mode.hint.type=ui-accessibility
                        ui-hint-mode.hint.mouse-movement=no-movement
                        click-mode.hint=ui-hint-mode.hint
                        click-mode.to.idle-mode=+esc
                        """))), KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty"))
                                                 .modeMap();
        Future<List<UiElement>> found = CompletableFuture.completedFuture(
                List.of(new UiElement(new Rectangle(10, 10, 0, 0)), new UiElement(new Rectangle(500, 300, 0, 0)), new UiElement(new Rectangle(900, 700, 0, 0))));
        ScreenManager screenManager = new ScreenManager(
                () -> Set.of(new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1)));
        List<HintMesh> shownHintMeshes = new ArrayList<>();
        Overlay overlay = (Overlay) Proxy.newProxyInstance(Overlay.class.getClassLoader(),
                new Class<?>[] {Overlay.class}, (proxy, method, args) -> {
                    if (method.getName().equals("setHintMesh"))
                        shownHintMeshes.add((HintMesh) args[0]);
                    return null;
                });
        HintManager hintManager = new HintManager(Map.of(), screenManager,
                new MouseManager(screenManager, proxy(MouseController.class)),
                overlay, new UiAutomation() {
                    @Override
                    public Future<List<UiElement>> startFindActiveWindowUiElements() {
                        return found;
                    }

                    @Override
                    public Future<List<UiElement>> startFindUiElementsInArea(Rectangle area) {
                        return found;
                    }
                }, () -> new App("test.exe"), new KeyRedactor(KeyRedaction.NONE), null);
        hintManager.modeChanged(modeMap.get("ui-hint-mode"));
        hintManager.completePendingUiHintQuery();
        List<Hint> hints = hintManager.hintMesh().hints();
        for (Key key : hints.getLast().keySequence())
            hintManager.selectHintKey(key);
        hintManager.modeChanged(modeMap.get("click-mode"));
        assertEquals(List.of(), hintManager.hintMesh().selectedKeySequence());
        assertEquals(hints, hintManager.hintMesh().hints());
        assertEquals(hintManager.hintMesh(), shownHintMeshes.getLast());
    }

    @Test
    void backFromTheGridOfASelectedHintTheHintsKeepItsPrefix() throws IOException {
        ModeMap modeMap = ConfigurationParser.parse(PropertiesReader.readPropertiesFile(
                new BufferedReader(new StringReader("""
                        idle-mode.to.ui-hint-mode=+u
                        ui-hint-mode.to.idle-mode=+esc
                        ui-hint-mode.to.grid-mode=+x
                        ui-hint-mode.overlay.selection-keys=a b
                        ui-hint-mode.overlay.type=ui-accessibility
                        ui-hint-mode.overlay.mouse-movement=no-movement
                        grid-mode.to.ui-hint-mode=+h
                        grid-mode.overlay.area=last-selected-hint-cell
                        """))), KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty"))
                                                 .modeMap();
        List<Rectangle> queries = new ArrayList<>();
        Future<List<UiElement>> found = CompletableFuture.completedFuture(
                List.of(new UiElement(new Rectangle(10, 10, 40, 20)), new UiElement(new Rectangle(500, 300, 40, 20)), new UiElement(new Rectangle(900, 700, 40, 20))));
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
                        queries.add(area);
                        return found;
                    }
                }, () -> new App("test.exe"), new KeyRedactor(KeyRedaction.NONE), null);
        hintManager.modeChanged(modeMap.get("ui-hint-mode"));
        hintManager.completePendingUiHintQuery();
        List<Hint> hints = hintManager.hintMesh().hints();
        List<Key> keySequence = hints.getLast().keySequence();
        for (Key key : keySequence)
            hintManager.selectHintKey(key);
        hintManager.modeChanged(modeMap.get("grid-mode"));
        hintManager.modeChanged(modeMap.get("ui-hint-mode"));
        assertEquals(1, queries.size());
        assertEquals(hints, hintManager.hintMesh().hints());
        assertEquals(keySequence.subList(0, keySequence.size() - 1),
                hintManager.hintMesh().selectedKeySequence());
    }

    @Test
    void aPositionHistoryClearedWhileTheUiHintsAreFoundIsShownOnceTheyAre() throws IOException {
        Configuration configuration = ConfigurationParser.parse(PropertiesReader.readPropertiesFile(
                new BufferedReader(new StringReader("""
                        idle-mode.to.grid-mode=+g
                        grid-mode.to.ui-hint-mode=+u
                        grid-mode.overlay.selection-keys=a b
                        ui-hint-mode.to.idle-mode=+esc
                        ui-hint-mode.overlay.selection-keys=a b
                        ui-hint-mode.overlay.type=ui-accessibility
                        ui-hint-mode.overlay.saved-position-history=position-history
                        ui-hint-mode.clear.position-history=+c
                        """))), KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty"));
        ModeMap modeMap = configuration.modeMap();
        Future<List<UiElement>> found = CompletableFuture.completedFuture(
                List.of(new UiElement(new Rectangle(10, 10, 40, 20)), new UiElement(new Rectangle(500, 300, 40, 20)), new UiElement(new Rectangle(900, 700, 40, 20))));
        ScreenManager screenManager = new ScreenManager(
                () -> Set.of(new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1)));
        HintManager hintManager = new HintManager(configuration.positionHistoryConfigurationByName(),
                screenManager, new MouseManager(screenManager, proxy(MouseController.class)),
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
        hintManager.modeChanged(modeMap.get("grid-mode"));
        hintManager.modeChanged(modeMap.get("ui-hint-mode"));
        hintManager.clearPositionHistory("position-history");
        hintManager.completePendingUiHintQuery();
        assertEquals(3, hintManager.hintMesh().hints().size());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> null);
    }

}
