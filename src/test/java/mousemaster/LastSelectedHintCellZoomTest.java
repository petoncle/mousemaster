package mousemaster;

import mousemaster.platform.Overlay;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * screen-hint-mode is a 2x2 grid of 960x540 cells on a 1920x1080 screen. zoom-hint-mode
 * zooms into the selected cell (2x) and shows a 2x2 grid over it, whose 480x270 cells
 * deeper-zoom-hint-mode zooms into (4x).
 */
class LastSelectedHintCellZoomTest {

    private static final String configuration = """
            idle-mode.to.screen-hint-mode=+x
            screen-hint-mode.to.zoom-hint-mode=+a
            screen-hint-mode.hint.selection-keys=a b c d
            screen-hint-mode.hint.mouse-movement=no-movement
            screen-hint-mode.hint.grid-cell-sizing=fit
            screen-hint-mode.hint.grid-max-row-count=2
            screen-hint-mode.hint.grid-max-column-count=2
            zoom-hint-mode.to.screen-hint-mode=+backspace
            zoom-hint-mode.to.deeper-zoom-hint-mode=+a
            zoom-hint-mode.to.click-mode=+leftalt
            zoom-hint-mode.hint=screen-hint-mode.hint
            zoom-hint-mode.hint.area-center=last-selected-hint
            zoom-hint-mode.zoom.center=last-selected-hint
            zoom-hint-mode.zoom.area-size-source=last-selected-hint-cell
            click-mode.hint=zoom-hint-mode.hint
            click-mode.zoom=zoom-hint-mode.zoom
            deeper-zoom-hint-mode.to.zoom-hint-mode=+backspace
            deeper-zoom-hint-mode.hint=zoom-hint-mode.hint
            deeper-zoom-hint-mode.hint.selection-keys=e f g h
            deeper-zoom-hint-mode.zoom=zoom-hint-mode.zoom
            """;

    private ModeMap modeMap;
    private HintManager hintManager;
    private ZoomManager zoomManager;
    private Zoom zoom;

    @Test
    void zoomsIntoTheCellSelectedInThePreviousMode() throws IOException {
        start("");
        switchMode("screen-hint-mode");
        selectBottomRightHint();
        switchMode("zoom-hint-mode");
        assertEquals(2, zoom.percent());
    }

    @Test
    void keepsTheZoomInAModeEnteredWithoutSelecting() throws IOException {
        start("");
        switchMode("screen-hint-mode");
        selectBottomRightHint();
        switchMode("zoom-hint-mode");
        switchMode("click-mode");
        assertEquals(2, zoom.percent());
    }

    @Test
    void goingBackToAModeZoomsOutToTheCellItZoomedInto() throws IOException {
        start("");
        switchMode("screen-hint-mode");
        selectBottomRightHint();
        switchMode("zoom-hint-mode");
        selectBottomRightHint();
        switchMode("deeper-zoom-hint-mode");
        assertEquals(4, zoom.percent());
        switchMode("zoom-hint-mode");
        assertEquals(2, zoom.percent());
    }

    @Test
    void aZoomNotCenteredOnTheSelectionIgnoresTheSelectedCell() throws IOException {
        start("screen-hint-mode.zoom.area-size-source=last-selected-hint-cell");
        switchMode("screen-hint-mode");
        selectBottomRightHint();
        switchMode("zoom-hint-mode");
        switchMode("screen-hint-mode");
        assertNull(zoom);
    }

    private void start(String extraProperties) throws IOException {
        modeMap = ConfigurationParser.parse(PropertiesReader.readPropertiesFile(
                new BufferedReader(new StringReader(configuration + extraProperties))),
                KeyboardLayout.keyboardLayoutByShortName.get("uk-qwerty")).modeMap();
        ScreenManager screenManager = new ScreenManager(
                () -> Set.of(new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1)));
        Overlay overlay = (Overlay) Proxy.newProxyInstance(Overlay.class.getClassLoader(),
                new Class<?>[] {Overlay.class}, (proxy, method, args) -> {
                    if (method.getName().equals("setZoom"))
                        zoom = (Zoom) args[0];
                    return method.getName().equals("canZoom") ? true : null;
                });
        hintManager = new HintManager(Map.of(), screenManager, null, overlay, null, null,
                new KeyRedactor(KeyRedaction.NONE), null);
        zoomManager = new ZoomManager(screenManager, hintManager, overlay);
    }

    private void switchMode(String modeName) {
        Mode mode = modeMap.get(modeName);
        hintManager.modeChanged(mode);
        zoomManager.modeChanged(mode);
    }

    private void selectBottomRightHint() {
        for (Key key : hintManager.hintMesh().hints().getLast().keySequence())
            hintManager.selectHintKey(key);
    }

}
