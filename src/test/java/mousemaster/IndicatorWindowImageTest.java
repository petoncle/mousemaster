package mousemaster;

import io.qt.gui.QImage;
import mousemaster.IndicatorPosition.PointIndicatorPosition;
import mousemaster.IndicatorPosition.PositionHistoryIndicatorPosition;
import mousemaster.renderer.IndicatorRenderer;
import mousemaster.renderer.IndicatorRenderer.WindowImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
        return argb(image).argb()[(y - image.y()) * image.image().width() + x - image.x()] >>> 24 != 0;
    }

    @Test
    void aLayerSweptAcrossTheScreenIsDrawnAgainOnlyWhenItMoves() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        IndicatorConfiguration indicator = indicator("idle-mode.indicator.enabled=true",
                "idle-mode.indicator.follow=false",
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
            IndicatorRenderer.IndicatorImage image = argb(new IndicatorRenderer().renderWindowImage(
                    indicator, Set.of(), Map.of(), Map.of(), Map.of(),
                    new Rectangle(500, 500, 32, 32), new Point(4, 4),
                    new Screen(new Rectangle(0, 0, 3840, 2160), (int) (96 * scale), scale),
                    null, "#000000"));
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
    void aLayerWithAnEndPositionRunsFromItsPositionToItsEndPosition() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        Rectangle lineInk = inkBox(render(indicator(
                        "idle-mode.indicator.enabled=true", "idle-mode.indicator.shape=line",
                        "idle-mode.indicator.position=last-selected-hint",
                        "idle-mode.indicator.end-position=target"),
                hintAndTarget(new Point(500, 500), new Point(700, 600))));
        assertEquals(500, lineInk.x(), 1);
        assertEquals(500, lineInk.y(), 1);
        assertEquals(700, lineInk.x() + lineInk.width(), 1);
        assertEquals(600, lineInk.y() + lineInk.height(), 1);
        Rectangle circleInk = inkBox(render(indicator(
                        "idle-mode.indicator.enabled=true",
                        "idle-mode.indicator.position=last-selected-hint",
                        "idle-mode.indicator.end-position=target",
                        "idle-mode.indicator.size=40", "idle-mode.indicator.fill-opacity=1"),
                hintAndTarget(new Point(500, 500), new Point(600, 500))));
        assertEquals(500, circleInk.x(), 1);
        assertEquals(480, circleInk.y(), 1);
        assertEquals(600, circleInk.x() + circleInk.width(), 1);
        assertEquals(520, circleInk.y() + circleInk.height(), 1);
    }

    @Test
    void aLineEndsOnItsEndPosition() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        IndicatorConfiguration line = indicator("idle-mode.indicator.enabled=true",
                "idle-mode.indicator.shape=line",
                "idle-mode.indicator.position=last-selected-hint",
                "idle-mode.indicator.end-position=target");
        for (int targetX : new int[]{700, 900}) {
            Rectangle ink = inkBox(render(line,
                    hintAndTarget(new Point(500, 500), new Point(targetX, 500))));
            assertEquals(500, ink.x(), 1);
            assertEquals(targetX, ink.x() + ink.width(), 1);
        }
    }

    @Test
    void aLayerFromAPositionHistoryToItselfRunsFromEachPointToTheNext() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        WindowImage lines = render(indicator("idle-mode.save.position-history=+f1",
                        "idle-mode.indicator.enabled=true", "idle-mode.indicator.shape=line",
                        "idle-mode.indicator.position=position-history",
                        "idle-mode.indicator.end-position=position-history"),
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

    @Test
    void aLayerFromAPositionHistoryToAPositionRunsFromEachPointToIt() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        WindowImage lines = render(indicator("idle-mode.save.position-history=+f1",
                        "idle-mode.indicator.enabled=true", "idle-mode.indicator.shape=line",
                        "idle-mode.indicator.position=position-history",
                        "idle-mode.indicator.end-position=target"),
                Map.of(new PositionHistoryIndicatorPosition("position-history"),
                        List.of(new Point(500, 500), new Point(500, 700)),
                        PointIndicatorPosition.TARGET, List.of(new Point(700, 600))));
        assertTrue(inked(lines, 600, 550));
        assertTrue(inked(lines, 600, 650));
        assertFalse(inked(lines, 600, 600));
        assertFalse(inked(lines, 500, 600));
    }

    @Test
    void aLayerWithoutAnEndPositionIsDrawnOnEachPointOfAPositionHistory() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        WindowImage circles = render(indicator("idle-mode.save.position-history=+f1",
                        "idle-mode.indicator.enabled=true", "idle-mode.indicator.size=40",
                        "idle-mode.indicator.fill-opacity=1",
                        "idle-mode.indicator.position=position-history"),
                Map.of(new PositionHistoryIndicatorPosition("position-history"),
                        List.of(new Point(500, 500), new Point(700, 500))));
        assertTrue(inked(circles, 500, 500));
        assertTrue(inked(circles, 700, 500));
        assertFalse(inked(circles, 600, 500));
    }

    private static WindowImage renderThroughHints(String positionAnchor,
                                                  String endPositionAnchor,
                                                  Map<Point, HintRectangles> hintRectanglesByPoint) {
        return render(indicator("idle-mode.save.position-history=+f1",
                        "idle-mode.indicator.enabled=true", "idle-mode.indicator.shape=line",
                        "idle-mode.indicator.position=position-history",
                        "idle-mode.indicator.end-position=position-history",
                        "idle-mode.indicator.position-anchor=" + positionAnchor,
                        "idle-mode.indicator.end-position-anchor=" + endPositionAnchor),
                Map.of(new PositionHistoryIndicatorPosition("position-history"),
                        List.of(new Point(500, 500), new Point(500, 700), new Point(700, 700))),
                hintRectanglesByPoint);
    }

    @Test
    void anEdgeAnchorRunsALayerBetweenTheEdgesOfTheHintBoxes() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        WindowImage lines = renderThroughHints("hint-box-edge", "hint-box-edge", boxes());
        assertFalse(inked(lines, 500, 505));
        assertTrue(inked(lines, 500, 515));
        assertTrue(inked(lines, 500, 685));
        assertFalse(inked(lines, 500, 695));
        assertFalse(inked(lines, 515, 700));
        assertTrue(inked(lines, 525, 700));
        assertTrue(inked(lines, 675, 700));
        assertFalse(inked(lines, 685, 700));
    }

    @Test
    void anEndPositionAnchorSetsOnlyWhereTheLayerStops() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        WindowImage lines = renderThroughHints("hint-box-edge", "point", boxes());
        assertFalse(inked(lines, 500, 505));
        assertTrue(inked(lines, 500, 695));
        assertFalse(inked(lines, 515, 700));
        assertTrue(inked(lines, 695, 700));
    }

    private static Map<Point, HintRectangles> boxes() {
        return Map.of(
                new Point(500, 500), new HintRectangles(new Rectangle(450, 450, 100, 100),
                        new Rectangle(480, 490, 40, 20)),
                new Point(500, 700), new HintRectangles(new Rectangle(450, 650, 100, 100),
                        new Rectangle(480, 690, 40, 20)),
                new Point(700, 700), new HintRectangles(new Rectangle(650, 650, 100, 100),
                        new Rectangle(680, 690, 40, 20)));
    }

    @Test
    void aBoxEdgeAnchorTakesTheCellWhenNoBoxIsDrawnAndAPointAnchorTakesThePoints() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        Map<Point, HintRectangles> cells = Map.of(
                new Point(500, 500), new HintRectangles(new Rectangle(450, 450, 100, 100), null),
                new Point(500, 700), new HintRectangles(new Rectangle(450, 650, 100, 100), null),
                new Point(700, 700), new HintRectangles(new Rectangle(650, 650, 100, 100), null));
        WindowImage cellEdges = renderThroughHints("hint-box-edge", "hint-box-edge", cells);
        assertFalse(inked(cellEdges, 500, 545));
        assertTrue(inked(cellEdges, 500, 560));
        assertFalse(inked(cellEdges, 545, 700));
        assertTrue(inked(cellEdges, 560, 700));
        WindowImage points = renderThroughHints("point", "point", cells);
        assertTrue(inked(points, 500, 545));
        assertTrue(inked(points, 545, 700));
    }

    @Test
    void anEdgeAnchorPutsALayerWithOnePositionOverTheCellOrTheBoxOfEachHint() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        WindowImage cells = renderOverHints("hint-cell-edge");
        assertTrue(inked(cells, 450, 500));
        assertTrue(inked(cells, 650, 700));
        assertFalse(inked(cells, 500, 500));
        assertFalse(inked(cells, 480, 500));
        WindowImage boxes = renderOverHints("hint-box-edge");
        assertTrue(inked(boxes, 480, 500));
        assertTrue(inked(boxes, 680, 700));
        assertFalse(inked(boxes, 450, 500));
        assertFalse(inked(boxes, 500, 500));
    }

    @Test
    void anAreaInsetMovesTheEdgesOfALayerOverAHint() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        WindowImage cells = renderOverHints("hint-cell-edge",
                "idle-mode.indicator.area-left-inset=-1", "idle-mode.indicator.area-right-inset=-1",
                "idle-mode.indicator.area-top-inset=-1", "idle-mode.indicator.area-bottom-inset=-1");
        assertTrue(inked(cells, 448, 500));
        assertFalse(inked(cells, 450, 500));
        assertTrue(inked(cells, 551, 500));
        assertFalse(inked(cells, 549, 500));
    }

    @Test
    void aLayerTakesTheColorOfTheGradientAtItsCenter() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        WindowImage outlines = renderOnATopAndABottomCell(
                "idle-mode.indicator.fill-opacity=0",
                "idle-mode.indicator.stroke-thickness=4",
                "idle-mode.indicator.stroke-color=across-screen top-to-bottom #FF0000 #0000FF");
        int top = argbAt(outlines, 450, 100);
        int bottom = argbAt(outlines, 450, 900);
        assertEquals(top, argbAt(outlines, 500, 50));
        assertEquals(top, argbAt(outlines, 500, 150));
        assertEquals(bottom, argbAt(outlines, 500, 850));
        assertTrue((top >> 16 & 0xFF) > (top & 0xFF), Integer.toHexString(top));
        assertTrue((bottom & 0xFF) > (bottom >> 16 & 0xFF), Integer.toHexString(bottom));
    }

    @Test
    void aLayerCastsAShadowInTheColorOfTheGradientAtItsCenter() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        WindowImage boxes = renderOnATopAndABottomCell(
                "idle-mode.indicator.fill-opacity=1",
                "idle-mode.indicator.shadow-color=across-screen top-to-bottom #FF0000 #0000FF",
                "idle-mode.indicator.shadow-opacity=1",
                "idle-mode.indicator.shadow-blur-radius=2",
                "idle-mode.indicator.shadow-horizontal-offset=20");
        int top = argbAt(boxes, 560, 100);
        int bottom = argbAt(boxes, 560, 900);
        assertTrue((top >> 16 & 0xFF) > (top & 0xFF), Integer.toHexString(top));
        assertTrue((bottom & 0xFF) > (bottom >> 16 & 0xFF), Integer.toHexString(bottom));
    }

    @Test
    void aLayerThatFollowsTheMouseTakesTheColorOfTheGradientAtItsCenterNotAtTheCursor() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        WindowImage box = render(new IndicatorRenderer(), indicator(
                "idle-mode.indicator.enabled=true", "idle-mode.indicator.shape=rectangle",
                "idle-mode.indicator.size=40", "idle-mode.indicator.fill-opacity=1",
                "idle-mode.indicator.fill-color=across-screen top-to-bottom #FF0000 #0000FF",
                "idle-mode.indicator.y=-450"), Set.of("indicator"), 500);
        int center = argbAt(box, 504, 54);
        assertTrue((center >> 16 & 0xFF) > (center & 0xFF), Integer.toHexString(center));
    }

    private static WindowImage renderOnATopAndABottomCell(String... lines) {
        List<String> allLines = new ArrayList<>(List.of("idle-mode.save.position-history=+f1",
                "idle-mode.indicator.enabled=true",
                "idle-mode.indicator.shape=rectangle",
                "idle-mode.indicator.position=position-history",
                "idle-mode.indicator.position-anchor=hint-cell-edge"));
        allLines.addAll(List.of(lines));
        return render(indicator(allLines.toArray(String[]::new)),
                Map.of(new PositionHistoryIndicatorPosition("position-history"),
                        List.of(new Point(500, 100), new Point(500, 900))),
                Map.of(new Point(500, 100), new HintRectangles(new Rectangle(450, 50, 100, 100), null),
                        new Point(500, 900), new HintRectangles(new Rectangle(450, 850, 100, 100), null)));
    }

    private static int argbAt(WindowImage windowImage, int x, int y) {
        IndicatorRenderer.IndicatorImage image = argb(windowImage);
        return image.argb()[(y - windowImage.y()) * image.width() + x - windowImage.x()];
    }

    private static WindowImage renderOverHints(String positionAnchor, String... lines) {
        List<String> allLines = new ArrayList<>(List.of("idle-mode.save.position-history=+f1",
                "idle-mode.indicator.enabled=true",
                "idle-mode.indicator.shape=rectangle",
                "idle-mode.indicator.fill-opacity=0",
                "idle-mode.indicator.stroke-thickness=2",
                "idle-mode.indicator.position=position-history",
                "idle-mode.indicator.position-anchor=" + positionAnchor));
        allLines.addAll(List.of(lines));
        return render(indicator(allLines.toArray(String[]::new)),
                Map.of(new PositionHistoryIndicatorPosition("position-history"),
                        List.of(new Point(500, 500), new Point(500, 700), new Point(700, 700))),
                boxes());
    }

    @Test
    void aRotationTurnsALayerWithAnEndPositionFromTheDirectionOfItsPositions() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        Rectangle lineInk = inkBox(render(indicator(
                        "idle-mode.indicator.enabled=true", "idle-mode.indicator.shape=line",
                        "idle-mode.indicator.position=last-selected-hint",
                        "idle-mode.indicator.end-position=target",
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
        IndicatorRenderer.IndicatorImage image = argb(windowImage);
        return image.argb()[(y - windowImage.y()) * image.width() + x - windowImage.x()] >>> 24;
    }

    private static Rectangle inkBox(WindowImage image) {
        Rectangle ink = ink(argb(image));
        return new Rectangle(image.x() + ink.x(), image.y() + ink.y(), ink.width(),
                ink.height());
    }

    private static int inkRight(WindowImage image) {
        Rectangle ink = ink(argb(image));
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
            inkTops[textIndex] = image.y() + ink(argb(image)).y();
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
            boxes[textIndex] = ink(argb(render(new IndicatorRenderer(), indicator(
                    "idle-mode.indicator.shape=rectangle",
                    "idle-mode.indicator.text=" + texts[textIndex],
                    "idle-mode.indicator.fill-opacity=1",
                    "idle-mode.indicator.stroke-thickness=0"), Set.of(), 500)));
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
            IndicatorRenderer.IndicatorImage image = argb(render(new IndicatorRenderer(), indicator(
                    "idle-mode.indicator.shape=rectangle",
                    "idle-mode.indicator.text=" + texts[textIndex],
                    "idle-mode.indicator.fill-color=#FF0000", "idle-mode.indicator.fill-opacity=1",
                    "idle-mode.indicator.stroke-thickness=0"), Set.of(), 500));
            Rectangle box = ink(image);
            assertEquals(0xFFFF0000, image.argb()[box.y() * image.width() + box.x()]);
            boxWidths[textIndex] = box.width();
        }
        assertTrue(boxWidths[1] > boxWidths[0] * 2, boxWidths[0] + " " + boxWidths[1]);
    }

    private static IndicatorRenderer.IndicatorImage argb(WindowImage windowImage) {
        return IndicatorRenderer.indicatorImage(windowImage.image());
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
                "idle-mode.indicator.position=active-screen-center");
        for (int mouseX : new int[] {100, 1500}) {
            WindowImage image = render(new IndicatorRenderer(), indicator, Set.of("indicator"),
                    mouseX);
            assertEquals(960, image.x() + image.image().width() / 2);
            assertEquals(540, image.y() + image.image().height() / 2);
        }
    }

    @Test
    void aScreenCornerLayerIsInThatCornerOfTheScreenAndMovedAwayFromTheEdgesByItsOffset() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        Rectangle ink = inkBox(render(new IndicatorRenderer(), indicator(
                "idle-mode.indicator.enabled=true", "idle-mode.indicator.shape=rectangle",
                "idle-mode.indicator.size=40", "idle-mode.indicator.fill-opacity=1",
                "idle-mode.indicator.position=active-screen-bottom-left",
                "idle-mode.indicator.x=50", "idle-mode.indicator.y=-100"), Set.of("indicator"),
                1500));
        assertEquals(50, ink.x(), 1);
        assertEquals(1080 - 100, ink.y() + ink.height(), 1);
    }

    @Test
    void aBorderRadiusRoundsTheCornersOfATriangle() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        int[] inkHeights = new int[2];
        for (int borderRadius = 0; borderRadius < 2; borderRadius++)
            inkHeights[borderRadius] = ink(argb(render(new IndicatorRenderer(), indicator(
                    "idle-mode.indicator.shape=triangle", "idle-mode.indicator.size=40",
                    "idle-mode.indicator.border-radius=" + borderRadius * 8,
                    "idle-mode.indicator.fill-opacity=1",
                    "idle-mode.indicator.stroke-thickness=0"), Set.of(), 500))).height();
        assertTrue(inkHeights[1] < inkHeights[0], inkHeights[0] + " " + inkHeights[1]);
    }

    @Test
    void aPathKeepsTheProportionsOfItsPoints() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        Rectangle ink = ink(argb(render(new IndicatorRenderer(), indicator(
                "idle-mode.indicator.shape=path", "idle-mode.indicator.points=0,0 10,0 5,8",
                "idle-mode.indicator.size=40", "idle-mode.indicator.fill-opacity=1",
                "idle-mode.indicator.stroke-thickness=0"), Set.of(), 500)));
        assertEquals(40, ink.width(), 1);
        assertEquals(32, ink.height(), 1);
    }

    @Test
    void anAnchoredLayerIsDrawnAgainWhereItWas() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        IndicatorConfiguration indicator = indicator("idle-mode.indicator.enabled=true",
                "idle-mode.ripple-indicator.size=40",
                "idle-mode.ripple-indicator.follow=false");
        IndicatorRenderer renderer = new IndicatorRenderer();
        WindowImage before = render(renderer, indicator, Set.of("indicator", "ripple-indicator"),
                500);
        int beforeWidth = before.image().width();
        WindowImage after = render(renderer, indicator, Set.of(), 600);
        assertNotSame(before.image(), after.image());
        assertEquals(before.x(), after.x());
        assertTrue(after.image().width() > beforeWidth);
    }

    @Test
    void anAnchoredLayerKeepsItsAnchorInEveryRendererOfTheIndicatorAndInItsCopy() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        IndicatorConfiguration indicator = indicator("idle-mode.ripple-indicator.size=40",
                "idle-mode.ripple-indicator.follow=false");
        IndicatorRenderer renderer = new IndicatorRenderer();
        WindowImage anchored = render(renderer, indicator, Set.of("ripple-indicator"), 500);
        assertEquals(anchored.x(),
                render(new IndicatorRenderer(renderer), indicator, Set.of(), 600).x());
        IndicatorRenderer copyRenderer = new IndicatorRenderer();
        copyRenderer.anchor("ripple-indicator", renderer);
        render(renderer, indicator, Set.of("ripple-indicator"), 700);
        assertEquals(anchored.x(), render(copyRenderer, indicator, Set.of(), 600).x());
    }

    private static int centerArgb(WindowImage windowImage) {
        IndicatorRenderer.IndicatorImage image = argb(windowImage);
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
        IndicatorRenderer.IndicatorImage image = argb(windowImage);
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
