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
    void aTextOnAShapeIsColoredByItsStyleSheetAndLaidOutTheSameAtEveryScale() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        int[] inkWidths = new int[2];
        for (int scaleIndex = 0; scaleIndex < 2; scaleIndex++) {
            double scale = scaleIndex == 0 ? 1 : 3;
            IndicatorConfiguration indicator = indicator("idle-mode.indicator.shape=rectangle",
                    "idle-mode.indicator.text-style-sheet=td { color: #0000FF } .key { color: #00FF00 }",
                    "idle-mode.indicator.text=<table><tr><td class=key>abc<td>de</table>",
                    "idle-mode.indicator.fill-opacity=0",
                    "idle-mode.indicator.stroke-thickness=0");
            IndicatorRenderer.IndicatorImage image = new IndicatorRenderer().renderWindowImage(
                    indicator, Set.of(), new Rectangle(500, 500, 32, 32), new Point(4, 4),
                    new Screen(new Rectangle(0, 0, 3840, 2160), (int) (96 * scale), scale),
                    null, "#000000").image();
            boolean green = false, blue = false;
            for (int argb : image.argb()) {
                green |= (argb >> 8 & 255) > 200 && (argb & 255) < 50;
                blue |= (argb & 255) > 200 && (argb >> 8 & 255) < 50;
            }
            assertTrue(green && blue, "scale " + scale);
            inkWidths[scaleIndex] = ink(image).width();
        }
        assertEquals(inkWidths[0] * 3, inkWidths[1], 3);
    }

    @Test
    void aTextWrapsAtTheWidthOfItsTable() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        Rectangle[] boxes = new Rectangle[2];
        String[] texts = {"<table><tr><td>aaa bbb ccc ddd eee</table>",
                "<table width=60><tr><td>aaa bbb ccc ddd eee</table>"};
        for (int textIndex = 0; textIndex < 2; textIndex++)
            boxes[textIndex] = ink(render(new IndicatorRenderer(), indicator(
                    "idle-mode.indicator.shape=rectangle",
                    "idle-mode.indicator.text=" + texts[textIndex],
                    "idle-mode.indicator.fill-opacity=1",
                    "idle-mode.indicator.stroke-thickness=0"), Set.of(), 500).image());
        assertEquals(60, boxes[1].width(), 1);
        assertTrue(boxes[1].height() > boxes[0].height() * 2,
                boxes[0].height() + " " + boxes[1].height());
    }

    @Test
    void aShapeWithATextIsSizedByIt() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        int[] boxWidths = new int[2];
        String[] texts = {"ab", "abcdef"};
        for (int textIndex = 0; textIndex < 2; textIndex++) {
            IndicatorRenderer.IndicatorImage image = render(new IndicatorRenderer(), indicator(
                    "idle-mode.indicator.shape=rectangle",
                    "idle-mode.indicator.text=" + texts[textIndex],
                    "idle-mode.indicator.fill-color=#FF0000", "idle-mode.indicator.fill-opacity=1",
                    "idle-mode.indicator.stroke-thickness=0"), Set.of(), 500).image();
            Rectangle box = ink(image);
            assertEquals(0xFFFF0000, image.argb()[box.y() * image.width() + box.x()]);
            boxWidths[textIndex] = box.width();
        }
        assertTrue(boxWidths[1] > boxWidths[0] * 2, boxWidths[0] + " " + boxWidths[1]);
    }

    private static Rectangle ink(IndicatorRenderer.IndicatorImage image) {
        int minX = image.width(), maxX = -1, minY = image.height(), maxY = -1;
        for (int y = 0; y < image.height(); y++)
            for (int x = 0; x < image.width(); x++)
                if (image.argb()[y * image.width() + x] >>> 24 != 0) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    @Test
    void aScreenCenterLayerIsCenteredOnTheScreenWhereverTheMouseIs() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        IndicatorConfiguration indicator = indicator("idle-mode.indicator.size=40",
                "idle-mode.indicator.position=screen-center");
        for (int mouseX : new int[] {100, 1500}) {
            WindowImage image = render(new IndicatorRenderer(), indicator, Set.of("indicator"),
                    mouseX);
            assertEquals(960, image.x() + image.image().width() / 2);
            assertEquals(540, image.y() + image.image().height() / 2);
        }
    }

    @Test
    void aBorderRadiusRoundsTheCornersOfATriangle() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        int[] inkHeights = new int[2];
        for (int borderRadius = 0; borderRadius < 2; borderRadius++)
            inkHeights[borderRadius] = ink(render(new IndicatorRenderer(), indicator(
                    "idle-mode.indicator.shape=triangle", "idle-mode.indicator.size=40",
                    "idle-mode.indicator.border-radius=" + borderRadius * 8,
                    "idle-mode.indicator.fill-opacity=1",
                    "idle-mode.indicator.stroke-thickness=0"), Set.of(), 500).image()).height();
        assertTrue(inkHeights[1] < inkHeights[0], inkHeights[0] + " " + inkHeights[1]);
    }

    @Test
    void aPathKeepsTheProportionsOfItsPoints() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        Rectangle ink = ink(render(new IndicatorRenderer(), indicator(
                "idle-mode.indicator.shape=path", "idle-mode.indicator.points=0,0 10,0 5,8",
                "idle-mode.indicator.size=40", "idle-mode.indicator.fill-opacity=1",
                "idle-mode.indicator.stroke-thickness=0"), Set.of(), 500).image());
        assertEquals(40, ink.width(), 1);
        assertEquals(32, ink.height(), 1);
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
