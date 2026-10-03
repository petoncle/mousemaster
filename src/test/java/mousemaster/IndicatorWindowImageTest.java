package mousemaster;

import io.qt.gui.QImage;
import mousemaster.renderer.IndicatorRenderer;
import mousemaster.renderer.IndicatorRenderer.WindowImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** A move that keeps the layers where they are relative to each other only moves the window. */
class IndicatorWindowImageTest {

    private static final Screen SCREEN = new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1);

    private static boolean qtAvailable;

    @BeforeAll
    static void initializeQt() {
        MousemasterApplication.tempDirectory =
                System.getProperty("java.io.tmpdir") + "/mousemaster-indicator-test";
        try {
            QtManager.initialize();
        } catch (Throwable alreadyInitializedByAnotherTest) {
        }
        try {
            new QImage(1, 1, QImage.Format.Format_ARGB32_Premultiplied).dispose();
            qtAvailable = true;
        } catch (Throwable e) {
            qtAvailable = false;
        }
    }

    private static IndicatorConfiguration indicator(String... lines) {
        return ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null)).modeMap()
                .get(Mode.IDLE_MODE_NAME).indicator();
    }

    private static WindowImage render(IndicatorRenderer renderer,
                                      IndicatorConfiguration indicator,
                                      Set<String> layerNamesToAnchor, int mouseX) {
        return renderer.renderWindowImage(indicator, layerNamesToAnchor,
                new Rectangle(mouseX, 500, 32, 32), new Point(4, 4), SCREEN, null, "#000000");
    }

    @Test
    void layersThatFollowTheMouseOnlyMove() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        IndicatorConfiguration indicator = indicator("idle-mode.indicator.enabled=true",
                "idle-mode.ripple-indicator.size=40");
        IndicatorRenderer renderer = new IndicatorRenderer();
        WindowImage before = render(renderer, indicator, Set.of("indicator", "ripple-indicator"),
                500);
        WindowImage after = render(renderer, indicator, Set.of(), 600);
        assertSame(before.image(), after.image());
        assertEquals(before.x() + 100, after.x());
    }

    @Test
    void anAnchoredLayerIsDrawnAgainWhereItWas() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        IndicatorConfiguration indicator = indicator("idle-mode.indicator.enabled=true",
                "idle-mode.ripple-indicator.size=40",
                "idle-mode.ripple-indicator.follow-mouse=false");
        IndicatorRenderer renderer = new IndicatorRenderer();
        WindowImage before = render(renderer, indicator, Set.of("indicator", "ripple-indicator"),
                500);
        WindowImage after = render(renderer, indicator, Set.of(), 600);
        assertNotSame(before.image(), after.image());
        assertEquals(before.x(), after.x());
        assertTrue(after.image().width() > before.image().width());
    }

    private static int centerArgb(WindowImage windowImage) {
        IndicatorRenderer.IndicatorImage image = windowImage.image();
        return image.argb()[image.height() / 2 * image.width() + image.width() / 2];
    }

    @Test
    void aColorAcrossTheScreenIsDrawnAgainWhereTheIndicatorMoved() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        IndicatorConfiguration indicator = indicator(
                "idle-mode.indicator.fill-color=across-screen left-to-right #FF0000 #0000FF",
                "idle-mode.indicator.fill-opacity=1");
        IndicatorRenderer renderer = new IndicatorRenderer();
        int left = centerArgb(render(renderer, indicator, Set.of("indicator"), 0));
        int right = centerArgb(render(renderer, indicator, Set.of(), 1880));
        assertTrue((left >> 16 & 0xFF) > 0xE0 && (left & 0xFF) < 0x20, Integer.toHexString(left));
        assertTrue((right & 0xFF) > 0xE0 && (right >> 16 & 0xFF) < 0x20, Integer.toHexString(right));
    }
}
