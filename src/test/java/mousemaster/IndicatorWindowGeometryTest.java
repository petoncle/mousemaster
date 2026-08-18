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
 * new size, so the indicator jumps. The window is sized for the indicator the transition ends
 * on instead, so all the frames of a transition find the same geometry.
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
    void theWindowIsTheSameThroughoutATransition() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        // A transition down to a smaller indicator reserves nothing, and one up to a bigger
        // one used to grow the window frame after frame.
        for (int size : new int[] {12, 40, 100})
            assertEquals(1, geometriesDuringTransitions(size).size(),
                    "the window moved or resized during a transition to " + size);
    }

    /** The geometries the window takes over two transitions, the second of which finds the
     *  window the first left behind. */
    private Set<String> geometriesDuringTransitions(int size) {
        ModeMap modeMap = ConfigurationParser.parse(List.of(
                "idle-mode.to.normal-mode=+leftshift",
                "normal-mode.indicator.size=26",
                "normal-mode.indicator.shadow-blur-radius=10",
                "idle-mode.indicator.size=" + size,
                "idle-mode.indicator.shadow-blur-radius=10"),
                KeyboardLayout.keyboardLayout("00000409", null)).modeMap();
        IndicatorConfiguration from = modeMap.get("normal-mode").indicator();
        IndicatorConfiguration to = modeMap.get(Mode.IDLE_MODE_NAME).indicator();
        IndicatorRenderer renderer = new IndicatorRenderer();
        renderer.preWarm();
        Screen screen = new Screen(new Rectangle(0, 0, 3840, 2160), 96, 1);
        Set<String> geometries = new LinkedHashSet<>();
        for (int transitionIndex = 0; transitionIndex < 2; transitionIndex++)
            for (double t : new double[] {0, 0.25, 0.5, 0.75, 1}) {
                renderer.setIndicator(IndicatorConfiguration.lerp(from, to, t), to, false,
                        new Rectangle(500, 500, 32, 32), new Point(16, 16), screen, null);
                geometries.add(renderer.window().xInPixels() + "," +
                               renderer.window().yInPixels() + " " +
                               renderer.window().widthInPixels() + "x" +
                               renderer.window().heightInPixels());
            }
        return geometries;
    }
}
