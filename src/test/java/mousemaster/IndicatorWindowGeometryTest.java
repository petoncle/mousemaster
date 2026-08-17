package mousemaster;

import io.qt.gui.QImage;
import mousemaster.renderer.IndicatorRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Resizing the indicator's translucent window makes the compositor show the old surface at the
 * new size, so the indicator jumps. The window is sized for the swell to come instead, once and
 * for all: the frames of a swell must all find the same geometry, however big the overshoot.
 */
class IndicatorWindowGeometryTest {

    private static boolean qtAvailable;

    @BeforeAll
    static void initializeQt() {
        MousemasterApplication.tempDirectory =
                System.getProperty("java.io.tmpdir") + "/mousemaster-indicator-window-test";
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

    @Test
    void theWindowIsTheSameThroughoutASwell() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        // An implosion reserves nothing, and an overshoot that big used to compound frame after
        // frame until the window was too big for the compositor to allocate.
        for (double overshoot : new double[] {0.5, 1.6, 20})
            assertEquals(1, geometriesDuringSwells(overshoot).size(),
                    "the window moved or resized during a swell of " + overshoot);
    }

    /** The geometries the window takes over two swells, the second of which finds the window
     *  the first left behind. */
    private Set<String> geometriesDuringSwells(double overshoot) {
        IndicatorConfiguration indicator = ConfigurationParser.parse(List.of(
                "idle-mode.to.normal-mode=+leftshift",
                "normal-mode.indicator.size=26",
                "normal-mode.indicator.shadow-blur-radius=10",
                "normal-mode.indicator.transition-animation-overshoot=" + overshoot),
                KeyboardLayout.keyboardLayout("00000409", null)).modeMap()
                .get("normal-mode").indicator();
        IndicatorRenderer renderer = new IndicatorRenderer();
        renderer.preWarm();
        Screen screen = new Screen(new Rectangle(0, 0, 3840, 2160), 96, 1);
        Set<String> geometries = new LinkedHashSet<>();
        double half = 1 + (overshoot - 1) / 2;
        for (int swellIndex = 0; swellIndex < 2; swellIndex++)
            for (double swell : new double[] {1, half, overshoot, half, 1}) {
                renderer.setIndicator(swell == 1 ? indicator : indicator.scaled(swell), false,
                        new Rectangle(500, 500, 32, 32), new Point(16, 16), screen, null);
                geometries.add(renderer.window().xInPixels() + "," +
                               renderer.window().yInPixels() + " " +
                               renderer.window().widthInPixels() + "x" +
                               renderer.window().heightInPixels());
            }
        return geometries;
    }
}
