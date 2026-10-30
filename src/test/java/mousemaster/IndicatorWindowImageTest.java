package mousemaster;

import io.qt.gui.QImage;
import mousemaster.IndicatorPosition.PointIndicatorPosition;
import mousemaster.IndicatorPosition.PositionHistoryIndicatorPosition;
import mousemaster.renderer.IndicatorRenderer;
import mousemaster.renderer.IndicatorRenderer.WindowImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
        return renderer.renderWindowImage(indicator, layerNamesToAnchor, Map.of(), Map.of(),
                Map.of(), new Rectangle(mouseX, 500, 32, 32), new Point(4, 4), SCREEN, null,
                "#000000");
    }

    private static WindowImage render(IndicatorConfiguration indicator,
                                      Map<IndicatorPosition, List<Point>> pointsByPosition) {
        return render(indicator, pointsByPosition, Map.of());
    }

    private static WindowImage render(IndicatorConfiguration indicator,
                                      Map<IndicatorPosition, List<Point>> pointsByPosition,
                                      Map<Point, HintRectangles> hintRectanglesByPoint) {
        return new IndicatorRenderer().renderWindowImage(indicator, Set.of("indicator"),
                pointsByPosition, Map.of(), hintRectanglesByPoint, new Rectangle(100, 100, 32, 32),
                new Point(4, 4), SCREEN, null, "#000000");
    }

    private static Map<IndicatorPosition, List<Point>> hintAndTarget(Point hint, Point target) {
        return Map.of(PointIndicatorPosition.LAST_SELECTED_HINT, List.of(hint),
                PointIndicatorPosition.TARGET, List.of(target));
    }

    private static boolean inked(WindowImage image, int x, int y) {
        return image.image().argb()[(y - image.y()) * image.image().width() + x - image.x()] >>> 24 != 0;
    }

    @Test
    void aLayerSweptAcrossTheScreenIsDrawnAgainOnlyWhenItMoves() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        IndicatorConfiguration indicator = indicator("idle-mode.indicator.enabled=true",
                "idle-mode.indicator.follow-mouse=false",
                "idle-mode.indicator.fill-color=across-screen per-pixel center-to-edge" +
                " #FF0000 #0000FF");
        IndicatorRenderer renderer = new IndicatorRenderer();
        WindowImage image = render(renderer, indicator, Set.of("indicator"), 500);
        assertSame(image.image(), render(renderer, indicator, Set.of(), 600).image());
        assertNotSame(image.image(),
                render(renderer, indicator, Set.of("indicator"), 600).image());
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
                    indicator, Set.of(), Map.of(), Map.of(), Map.of(),
                    new Rectangle(500, 500, 32, 32), new Point(4, 4),
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
    void aTextIsKeptOnTheScreenAndAShapeIsNot() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        String[] label = {"idle-mode.indicator.shape=text",
                "idle-mode.indicator.text=a label near the edge",
                "idle-mode.indicator.font-size=16", "idle-mode.indicator.fill-opacity=1"};
        assertTrue(inkRight(render(new IndicatorRenderer(), indicator(label), Set.of(), 1910)) <= 1920);
        String[] rotatedLabel = Arrays.copyOf(label, label.length + 1);
        rotatedLabel[label.length] = "idle-mode.indicator.rotation=30";
        assertTrue(inkRight(render(new IndicatorRenderer(), indicator(rotatedLabel), Set.of(),
                1910)) <= 1920);
        WindowImage zoomed = new IndicatorRenderer().renderWindowImage(indicator(label), Set.of(),
                Map.of(), Map.of(), Map.of(), new Rectangle(1700, 500, 32, 32), new Point(4, 4),
                SCREEN, new Zoom(3, new Point(1400, 540), SCREEN.rectangle()), "#000000");
        assertTrue(inkRight(zoomed) <= 1920);
        assertTrue(inkRight(render(new IndicatorRenderer(), indicator(
                "idle-mode.indicator.size=100", "idle-mode.indicator.fill-opacity=1"), Set.of(),
                1910)) > 1920);
    }

    @Test
    void aLayerWithTwoPositionsRunsFromTheFirstToTheSecond() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        Rectangle lineInk = inkBox(render(indicator(
                        "idle-mode.indicator.enabled=true", "idle-mode.indicator.shape=line",
                        "idle-mode.indicator.position=last-selected-hint target"),
                hintAndTarget(new Point(500, 500), new Point(700, 600))));
        assertEquals(500, lineInk.x(), 1);
        assertEquals(500, lineInk.y(), 1);
        assertEquals(700, lineInk.x() + lineInk.width(), 1);
        assertEquals(600, lineInk.y() + lineInk.height(), 1);
        Rectangle circleInk = inkBox(render(indicator(
                        "idle-mode.indicator.enabled=true",
                        "idle-mode.indicator.position=last-selected-hint target",
                        "idle-mode.indicator.size=40", "idle-mode.indicator.fill-opacity=1"),
                hintAndTarget(new Point(500, 500), new Point(600, 500))));
        assertEquals(500, circleInk.x(), 1);
        assertEquals(480, circleInk.y(), 1);
        assertEquals(600, circleInk.x() + circleInk.width(), 1);
        assertEquals(520, circleInk.y() + circleInk.height(), 1);
    }

    @Test
    void anArrowEndsOnTheSecondPositionWithAHeadAsWideAsItsSize() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        IndicatorConfiguration arrow = indicator("idle-mode.indicator.enabled=true",
                "idle-mode.indicator.shape=arrow", "idle-mode.indicator.size=20",
                "idle-mode.indicator.position=last-selected-hint target");
        for (int targetX : new int[]{700, 900}) {
            WindowImage image = render(arrow,
                    hintAndTarget(new Point(500, 500), new Point(targetX, 500)));
            assertTrue(inked(image, 550, 502));
            assertFalse(inked(image, 550, 505));
            Rectangle ink = inkBox(image);
            assertEquals(500, ink.x(), 1);
            assertEquals(targetX, ink.x() + ink.width(), 1);
            assertEquals(490, ink.y(), 1);
            assertEquals(510, ink.y() + ink.height(), 1);
        }
        Rectangle bar = inkBox(render(arrow,
                hintAndTarget(new Point(500, 500), new Point(500, 500))));
        assertEquals(500, bar.x() + bar.width() / 2.0, 1);
        assertEquals(490, bar.y(), 1);
        assertEquals(510, bar.y() + bar.height(), 1);
    }

    @Test
    void aLayerThroughSeveralPositionsRunsFromEachToTheNext() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        WindowImage lines = render(indicator("idle-mode.save.position-history=+f1",
                        "idle-mode.indicator.enabled=true", "idle-mode.indicator.shape=line",
                        "idle-mode.indicator.position=position-history"),
                Map.of(new PositionHistoryIndicatorPosition("position-history"),
                        List.of(new Point(500, 500), new Point(700, 500), new Point(700, 700))));
        Rectangle ink = inkBox(lines);
        assertEquals(500, ink.x(), 1);
        assertEquals(700, ink.x() + ink.width(), 1);
        assertEquals(500, ink.y(), 1);
        assertEquals(700, ink.y() + ink.height(), 1);
        assertTrue(inked(lines, 600, 500));
        assertTrue(inked(lines, 700, 600));
        assertFalse(inked(lines, 600, 600));
    }

    private static WindowImage renderThroughHints(String positionAnchor,
                                                  Map<Point, HintRectangles> hintRectanglesByPoint) {
        return render(indicator("idle-mode.save.position-history=+f1",
                        "idle-mode.indicator.enabled=true", "idle-mode.indicator.shape=arrow",
                        "idle-mode.indicator.size=8",
                        "idle-mode.indicator.position=position-history",
                        "idle-mode.indicator.position-anchor=" + positionAnchor),
                Map.of(new PositionHistoryIndicatorPosition("position-history"),
                        List.of(new Point(500, 500), new Point(500, 700), new Point(700, 700))),
                hintRectanglesByPoint);
    }

    @Test
    void anEdgeAnchorRunsALayerBetweenTheEdgesOfTheHintBoxes() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        WindowImage arrows = renderThroughHints("hint-box-edge", Map.of(
                new Point(500, 500), new HintRectangles(new Rectangle(450, 450, 100, 100),
                        new Rectangle(480, 490, 40, 20)),
                new Point(500, 700), new HintRectangles(new Rectangle(450, 650, 100, 100),
                        new Rectangle(480, 690, 40, 20)),
                new Point(700, 700), new HintRectangles(new Rectangle(650, 650, 100, 100),
                        new Rectangle(680, 690, 40, 20))));
        assertFalse(inked(arrows, 500, 505));
        assertTrue(inked(arrows, 500, 515));
        assertTrue(inked(arrows, 500, 685));
        assertFalse(inked(arrows, 500, 695));
        assertFalse(inked(arrows, 515, 700));
        assertTrue(inked(arrows, 525, 700));
        assertTrue(inked(arrows, 675, 700));
        assertFalse(inked(arrows, 685, 700));
    }

    @Test
    void aBoxEdgeAnchorTakesTheCellWhenNoBoxIsDrawnAndAPointAnchorTakesThePoints() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        Map<Point, HintRectangles> cells = Map.of(
                new Point(500, 500), new HintRectangles(new Rectangle(450, 450, 100, 100), null),
                new Point(500, 700), new HintRectangles(new Rectangle(450, 650, 100, 100), null),
                new Point(700, 700), new HintRectangles(new Rectangle(650, 650, 100, 100), null));
        WindowImage cellEdges = renderThroughHints("hint-box-edge", cells);
        assertFalse(inked(cellEdges, 500, 545));
        assertTrue(inked(cellEdges, 500, 560));
        assertFalse(inked(cellEdges, 545, 700));
        assertTrue(inked(cellEdges, 560, 700));
        WindowImage points = renderThroughHints("point", cells);
        assertTrue(inked(points, 500, 545));
        assertTrue(inked(points, 545, 700));
    }

    @Test
    void aRotationTurnsALayerWithTwoPositionsFromTheirDirection() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        Rectangle lineInk = inkBox(render(indicator(
                        "idle-mode.indicator.enabled=true", "idle-mode.indicator.shape=line",
                        "idle-mode.indicator.position=last-selected-hint target",
                        "idle-mode.indicator.rotation=90"),
                hintAndTarget(new Point(500, 500), new Point(700, 500))));
        assertEquals(600, lineInk.x() + lineInk.width() / 2.0, 1);
        assertEquals(400, lineInk.y(), 1);
        assertEquals(600, lineInk.y() + lineInk.height(), 1);
    }

    @Test
    void aLayerWithAnAreaPutsItsStrokeOnTheInsetEdges() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        IndicatorConfiguration indicator = indicator("idle-mode.indicator.enabled=true",
                "idle-mode.indicator.shape=rectangle", "idle-mode.indicator.fill-opacity=0",
                "idle-mode.indicator.area=last-selected-hint-cell",
                "idle-mode.indicator.area-top-inset=-0.5",
                "idle-mode.indicator.area-bottom-inset=-0.5",
                "idle-mode.indicator.area-left-inset=-0.5",
                "idle-mode.indicator.area-right-inset=-0.5");
        WindowImage box = new IndicatorRenderer().renderWindowImage(indicator,
                Set.of("indicator"), Map.of(),
                Map.of(indicator.layerByName().get("indicator").area(),
                        new Rectangle(500, 400, 101, 50)),
                Map.of(), new Rectangle(100, 100, 32, 32), new Point(4, 4), SCREEN, null,
                "#000000");
        assertEquals(new Rectangle(499, 399, 103, 52), inkBox(box));
        assertEquals(255, alpha(box, 499, 420));
        assertEquals(0, alpha(box, 500, 420));
        assertEquals(255, alpha(box, 601, 420));
        assertEquals(0, alpha(box, 600, 420));
    }

    private static int alpha(WindowImage windowImage, int x, int y) {
        IndicatorRenderer.IndicatorImage image = windowImage.image();
        return image.argb()[(y - windowImage.y()) * image.width() + x - windowImage.x()] >>> 24;
    }

    private static Rectangle inkBox(WindowImage image) {
        Rectangle ink = ink(image.image());
        return new Rectangle(image.x() + ink.x(), image.y() + ink.y(), ink.width(),
                ink.height());
    }

    private static int inkRight(WindowImage image) {
        Rectangle ink = ink(image.image());
        return image.x() + ink.x() + ink.width();
    }

    @Test
    void aTextKeepsItsBaselineWhateverItsLetters() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        int[] inkTops = new int[2];
        String[] texts = {"x", "xg"};
        for (int textIndex = 0; textIndex < 2; textIndex++) {
            WindowImage image = render(new IndicatorRenderer(), indicator(
                    "idle-mode.indicator.shape=text", "idle-mode.indicator.text=" + texts[textIndex],
                    "idle-mode.indicator.font-size=16", "idle-mode.indicator.fill-opacity=1",
                    "idle-mode.indicator.stroke-thickness=0"), Set.of(), 500);
            inkTops[textIndex] = image.y() + ink(image.image()).y();
        }
        assertEquals(inkTops[0], inkTops[1]);
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
    void aLayerIsOffsetByADistanceAtAnAngleClockwiseFromTwelveOClock() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        String[][] polarAndCartesian = {{"distance=30", "angle=90", "x=30"},
                {"distance=30", "angle=180", "y=30"}};
        for (String[] offsets : polarAndCartesian) {
            WindowImage polar = render(new IndicatorRenderer(), indicator(
                    "idle-mode.indicator.enabled=true", "idle-mode.indicator." + offsets[0],
                    "idle-mode.indicator." + offsets[1]), Set.of(), 500);
            WindowImage cartesian = render(new IndicatorRenderer(), indicator(
                    "idle-mode.indicator.enabled=true", "idle-mode.indicator." + offsets[2]),
                    Set.of(), 500);
            assertEquals(cartesian.x(), polar.x(), offsets[2]);
            assertEquals(cartesian.y(), polar.y(), offsets[2]);
        }
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

    private static int alphaAt(WindowImage windowImage, int x, int y) {
        IndicatorRenderer.IndicatorImage image = windowImage.image();
        return image.argb()[(y - windowImage.y()) * image.width() + x - windowImage.x()] >>> 24;
    }

    @Test
    void aLayerCastsOnlyItsOwnShadow() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        IndicatorConfiguration indicator = indicator("idle-mode.indicator.enabled=true",
                "idle-mode.indicator.shadow-opacity=1",
                "idle-mode.ripple-indicator.enabled=true", "idle-mode.ripple-indicator.x=100");
        WindowImage image = render(new IndicatorRenderer(), indicator,
                Set.of("indicator", "ripple-indicator"), 500);
        assertTrue(alphaAt(image, 504, 504 + 14) > 0x20);
        assertEquals(0, alphaAt(image, 604, 504 + 14));
    }

    @Test
    void theLastSelectedHintBoxColorIsDrawnAgainWhenItChanges() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        IndicatorConfiguration indicator = indicator(
                "idle-mode.indicator.fill-color=last-selected-hint-box-color",
                "idle-mode.indicator.fill-opacity=1");
        IndicatorRenderer renderer = new IndicatorRenderer();
        int red = centerArgb(renderer.renderWindowImage(indicator, Set.of("indicator"), Map.of(),
                Map.of(), Map.of(), new Rectangle(500, 500, 32, 32), new Point(4, 4), SCREEN,
                null, "#FF0000"));
        int blue = centerArgb(renderer.renderWindowImage(indicator, Set.of(), Map.of(), Map.of(),
                Map.of(), new Rectangle(500, 500, 32, 32), new Point(4, 4), SCREEN, null,
                "#0000FF"));
        assertTrue((red >> 16 & 0xFF) > 0xE0 && (red & 0xFF) < 0x20, Integer.toHexString(red));
        assertTrue((blue & 0xFF) > 0xE0 && (blue >> 16 & 0xFF) < 0x20, Integer.toHexString(blue));
    }
}
