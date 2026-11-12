package mousemaster.renderer;

import io.qt.core.Qt;
import io.qt.gui.QImage;
import io.qt.gui.QPainter;
import io.qt.gui.QPixmap;
import io.qt.widgets.QApplication;
import mousemaster.Configuration;
import mousemaster.ConfigurationParser;
import mousemaster.Hint;
import mousemaster.HintMesh;
import mousemaster.Key;
import mousemaster.KeyboardLayout;
import mousemaster.Mousemaster;
import mousemaster.MousemasterApplication;
import mousemaster.Point;
import mousemaster.PropertiesReader;
import mousemaster.QtManager;
import mousemaster.Rectangle;
import mousemaster.Screen;
import mousemaster.Zoom;
import mousemaster.qt.ImageChunks;
import mousemaster.qt.TransparentWindow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Hint a is on the element at (200, 200, 200, 40), and its hotspot is at (380, 300). */
class HotspotBoxTest {

    private static final Screen screen = new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1);
    private static final Hint hint = new Hint(300, 220, -1, -1, List.of(Key.ofName("a")),
            new Rectangle(200, 200, 200, 40));
    private static final List<Point> probes =
            List.of(new Point(380, 300), new Point(360, 280), new Point(300, 220));
    private static boolean qtAvailable;

    @BeforeAll
    static void initializeQt() {
        MousemasterApplication.tempDirectory =
                System.getProperty("java.io.tmpdir") + "/mousemaster-hotspot-box-test";
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

    /** Paints into its pixels as the layered window on Windows does. */
    private static class BufferLayeredWindow implements LayeredWindow {

        private final ByteBuffer pixels = ByteBuffer.allocateDirect(
                4 * screen.rectangle().width() * screen.rectangle().height());

        @Override
        public void draw(Rectangle rectangle, Rectangle dirty,
                         BiConsumer<QPainter, Rectangle> paintArea) {
            Rectangle area = dirty.intersection(rectangle);
            ImageChunks.paintChunks(pixels, rectangle.width(),
                    new Rectangle(area.x() - rectangle.x(), area.y() - rectangle.y(),
                            area.width(), area.height()), paintArea);
        }

        @Override
        public void clear() {
            pixels.put(0, new byte[pixels.capacity()]);
        }

        @Override
        public void setOpacity(double opacity) {
        }
    }

    /** The colors at the probes, after the mesh is shown with its hotspot at each of the points in
     *  turn: the same whether the hotspots are drawn in a layered window or not. */
    private static List<Integer> colors(String properties, List<Hint> savedHints,
                                        List<Point> points, List<Point> probes)
            throws IOException {
        Configuration configuration = ConfigurationParser.parse(
                PropertiesReader.readPropertiesFile(new BufferedReader(new StringReader("""
                        idle-mode.to.hint-mode=+u
                        hint-mode.to.idle-mode=+esc
                        hint-mode.overlay.selection-keys=a b
                        hint-mode.overlay.box-color=#0000FF
                        hint-mode.overlay.box-opacity=1
                        hint-mode.overlay.box-border-opacity=0
                        hint-mode.overlay.font-opacity=0
                        """ + properties))),
                KeyboardLayout.keyboardLayoutByShortName.get("us-qwerty"));
        List<Integer> colors = colors(configuration, savedHints, points, probes, false);
        assertEquals(colors, colors(configuration, savedHints, points, probes, true));
        return colors;
    }

    private static List<Integer> colors(Configuration configuration, List<Hint> savedHints,
                                        List<Point> points, List<Point> probes,
                                        boolean withLayeredWindow) {
        List<BufferLayeredWindow> layeredWindows = new ArrayList<>();
        HintMeshRenderer hintMeshRenderer =
                hintMeshRenderer(withLayeredWindow ? layeredWindows : null, () -> {
                });
        for (Point point : points) {
            HintMesh hintMesh = new HintMesh.HintMeshBuilder()
                    .visible(true)
                    .hints(List.of(hint))
                    .savedHints(savedHints)
                    .backgroundArea(screen.rectangle())
                    .hotspotByKeySequence(point == null ? Map.of() : Map.of(hint.keySequence(), point))
                    .styleByFilter(configuration.modeMap().get("hint-mode").hintMesh()
                                                .styleByFilter())
                    .build();
            show(hintMeshRenderer, hintMesh, Set.of(screen));
        }
        QImage image = new QImage(screen.rectangle().width(), screen.rectangle().height(),
                QImage.Format.Format_ARGB32_Premultiplied);
        image.fill(0);
        QPainter painter = new QPainter(image);
        for (BufferLayeredWindow layeredWindow : layeredWindows) {
            QImage layeredWindowImage = new QImage(layeredWindow.pixels,
                    screen.rectangle().width(), screen.rectangle().height(),
                    QImage.Format.Format_ARGB32_Premultiplied);
            painter.drawImage(0, 0, layeredWindowImage);
            layeredWindowImage.dispose();
        }
        for (TransparentWindow window : hintMeshRenderer.windows()) {
            if (!window.isVisible())
                continue;
            QPixmap pixmap = window.grab();
            painter.drawPixmap(window.xInPixels(), window.yInPixels(), pixmap);
            pixmap.dispose();
        }
        painter.end();
        List<Integer> colors = probes.stream()
                                     .map(probe -> image.pixel((int) probe.x(), (int) probe.y()))
                                     .toList();
        painter.dispose();
        image.dispose();
        hintMeshRenderer.hideHintMesh();
        for (TransparentWindow window : hintMeshRenderer.windows())
            window.disposeLater();
        return colors;
    }

    /** Without layered windows when layeredWindows is null. */
    private static HintMeshRenderer hintMeshRenderer(List<BufferLayeredWindow> layeredWindows,
                                                     Runnable beforePaint) {
        return new HintMeshRenderer(() -> {
            TransparentWindow window = new TransparentWindow();
            window.setAttribute(Qt.WidgetAttribute.WA_DontShowOnScreen);
            window.setBeforePaint(beforePaint);
            return window;
        }, layeredWindows == null ? null : () -> {
            BufferLayeredWindow layeredWindow = new BufferLayeredWindow();
            layeredWindows.add(layeredWindow);
            return layeredWindow;
        }, () -> {
        });
    }

    private static void show(HintMeshRenderer hintMeshRenderer, HintMesh hintMesh,
                             Set<Screen> screens) {
        hintMeshRenderer.setHintMesh(hintMesh,
                new Zoom(1, screen.rectangle().center(), screen.rectangle()), false, screens);
        for (int frame = 0; frame < 10 || hintMeshRenderer.buildPending(); frame++) {
            QApplication.processEvents();
            hintMeshRenderer.update(Mousemaster.FRAME_INTERVAL);
            hintMeshRenderer.runPendingWork();
        }
    }

    @Test
    void aMovedHotspotShowsItsBoxAndALineFromTheHintBoxBehindIt() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        assertEquals(List.of(0xFFFF0000, 0xFF00FF00, 0xFF0000FF), colors("""
                hint-mode.overlay.hotspot-box-width=10
                hint-mode.overlay.hotspot-box-height=10
                hint-mode.overlay.hotspot-box-color=#FF0000
                hint-mode.overlay.hotspot-box-opacity=1
                hint-mode.overlay.hotspot-box-border-thickness=0
                hint-mode.overlay.hotspot-line-thickness=4
                hint-mode.overlay.hotspot-line-color=#00FF00
                hint-mode.overlay.hotspot-line-opacity=1
                """, List.of(), List.of(new Point(380, 300)), probes));
    }

    @Test
    void aHotspotThatMovesAgainLeavesNothingWhereItWas() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        assertEquals(List.of(0, 0, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF), colors("""
                hint-mode.overlay.hotspot-box-width=10
                hint-mode.overlay.hotspot-box-height=10
                hint-mode.overlay.hotspot-box-color=#FF0000
                hint-mode.overlay.hotspot-box-opacity=1
                hint-mode.overlay.hotspot-box-border-thickness=0
                hint-mode.overlay.hotspot-line-thickness=4
                hint-mode.overlay.hotspot-line-color=#00FF00
                hint-mode.overlay.hotspot-line-opacity=1
                """, List.of(), List.of(new Point(380, 300), new Point(500, 220)),
                List.of(new Point(380, 300), new Point(360, 280), new Point(500, 220),
                        new Point(450, 220), new Point(300, 220))));
    }

    @Test
    void anAcrossHintSweepMovesWithItsHotspotBox() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        List<Integer> colors = colors("""
                hint-mode.overlay.hotspot-box-width=10
                hint-mode.overlay.hotspot-box-height=10
                hint-mode.overlay.hotspot-box-color=across-box per-pixel top-to-bottom #FF0000 #0000FF
                hint-mode.overlay.hotspot-box-opacity=1
                hint-mode.overlay.hotspot-box-border-thickness=0
                """, List.of(), List.of(new Point(380, 300), new Point(500, 220)),
                List.of(new Point(500, 216), new Point(500, 224)));
        assertTrue((colors.getFirst() >> 16 & 0xFF) > (colors.getFirst() & 0xFF));
        assertTrue((colors.getLast() & 0xFF) > (colors.getLast() >> 16 & 0xFF));
    }

    @Test
    void anAcrossHintLineTakesTheColorAtTheCenterOfItsBox() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        List<Integer> colors = colors("""
                hint-mode.overlay.hotspot-box-width=11
                hint-mode.overlay.hotspot-box-height=11
                hint-mode.overlay.hotspot-box-color=across-box per-pixel top-to-bottom #FF0000 #0000FF
                hint-mode.overlay.hotspot-box-opacity=1
                hint-mode.overlay.hotspot-box-border-thickness=0
                hint-mode.overlay.hotspot-line-thickness=4
                hint-mode.overlay.hotspot-line-color=across-box per-pixel top-to-bottom #FF0000 #0000FF
                hint-mode.overlay.hotspot-line-opacity=1
                """, List.of(), List.of(new Point(380, 300)), probes);
        for (int shift : List.of(16, 8, 0))
            assertTrue(Math.abs((colors.get(0) >> shift & 0xFF) - (colors.get(1) >> shift & 0xFF))
                       <= 1);
    }

    @Test
    void aHintThatLosesItsHotspotShowsNoHotspotBox() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        assertEquals(List.of(0, 0xFF0000FF), colors("""
                hint-mode.overlay.hotspot-box-width=10
                hint-mode.overlay.hotspot-box-height=10
                hint-mode.overlay.hotspot-box-color=#FF0000
                hint-mode.overlay.hotspot-box-opacity=1
                """, List.of(), Arrays.asList(new Point(380, 300), new Point(390, 300), null),
                List.of(new Point(390, 300), new Point(300, 220))));
    }

    @Test
    void aScreenThatLosesItsHintsLosesItsHotspots() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        Configuration configuration = ConfigurationParser.parse(
                PropertiesReader.readPropertiesFile(new BufferedReader(new StringReader("""
                        idle-mode.to.hint-mode=+u
                        hint-mode.overlay.selection-keys=a b
                        hint-mode.overlay.hotspot-box-width=10
                        hint-mode.overlay.hotspot-box-height=10
                        hint-mode.overlay.hotspot-box-color=#FF0000
                        hint-mode.overlay.hotspot-box-opacity=1
                        """))), KeyboardLayout.keyboardLayoutByShortName.get("us-qwerty"));
        Screen otherScreen = new Screen(new Rectangle(1920, 0, 1920, 1080), 96, 1);
        Hint otherHint = new Hint(2220, 220, -1, -1, List.of(Key.ofName("b")),
                new Rectangle(2120, 200, 200, 40));
        List<BufferLayeredWindow> layeredWindows = new ArrayList<>();
        HintMeshRenderer hintMeshRenderer = hintMeshRenderer(layeredWindows, () -> {
        });
        HintMesh.HintMeshBuilder hintMesh = new HintMesh.HintMeshBuilder()
                .visible(true)
                .styleByFilter(configuration.modeMap().get("hint-mode").hintMesh()
                                            .styleByFilter());
        show(hintMeshRenderer, hintMesh.hints(List.of(hint, otherHint))
                                       .hotspotByKeySequence(Map.of(hint.keySequence(),
                                               new Point(380, 300), otherHint.keySequence(),
                                               new Point(2500, 220)))
                                       .build(), Set.of(screen, otherScreen));
        show(hintMeshRenderer, hintMesh.hints(List.of(otherHint))
                                       .hotspotByKeySequence(Map.of(otherHint.keySequence(),
                                               new Point(2500, 220)))
                                       .build(), Set.of(screen, otherScreen));
        show(hintMeshRenderer, hintMesh.hotspotByKeySequence(Map.of(otherHint.keySequence(),
                new Point(2510, 220))).build(), Set.of(screen, otherScreen));
        for (BufferLayeredWindow layeredWindow : layeredWindows)
            assertEquals(0, layeredWindow.pixels.getInt(4 * (300 * 1920 + 380)));
        hintMeshRenderer.hideHintMesh();
        for (TransparentWindow window : hintMeshRenderer.windows())
            window.disposeLater();
    }

    @Test
    void aHintMeshWindowThatShowsNothingIsNotPainted() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        Configuration configuration = ConfigurationParser.parse(
                PropertiesReader.readPropertiesFile(new BufferedReader(new StringReader("""
                        idle-mode.to.hint-mode=+u
                        hint-mode.overlay.selection-keys=a b
                        hint-mode.overlay.hotspot-box-width=10
                        hint-mode.overlay.hotspot-box-height=10
                        hint-mode.overlay.hotspot-box-color=#FF0000
                        hint-mode.overlay.hotspot-box-opacity=1
                        """))), KeyboardLayout.keyboardLayoutByShortName.get("us-qwerty"));
        Hint otherHint = new Hint(800, 220, -1, -1, List.of(Key.ofName("b")),
                new Rectangle(700, 200, 200, 40));
        int[] paintCount = {0};
        HintMeshRenderer hintMeshRenderer =
                hintMeshRenderer(new ArrayList<>(), () -> paintCount[0]++);
        HintMesh.HintMeshBuilder hintMesh = new HintMesh.HintMeshBuilder()
                .visible(true)
                .hotspotByKeySequence(Map.of(hint.keySequence(), new Point(380, 300)))
                .styleByFilter(configuration.modeMap().get("hint-mode").hintMesh()
                                            .styleByFilter());
        show(hintMeshRenderer, hintMesh.hints(List.of(hint, otherHint)).build(), Set.of(screen));
        paintCount[0] = 0;
        show(hintMeshRenderer, hintMesh.hints(List.of(hint)).build(), Set.of(screen));
        hintMeshRenderer.hideHintMesh();
        QApplication.processEvents();
        assertEquals(0, paintCount[0]);
        for (TransparentWindow window : hintMeshRenderer.windows())
            window.disposeLater();
    }

    @Test
    void aMeshThatIsNotAGridIsDrawnInTheLayeredWindowFromItsShow() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        Configuration configuration = ConfigurationParser.parse(
                PropertiesReader.readPropertiesFile(new BufferedReader(new StringReader("""
                        idle-mode.to.hint-mode=+u
                        hint-mode.overlay.selection-keys=a b
                        hint-mode.overlay.box-color=#0000FF
                        hint-mode.overlay.box-opacity=1
                        hint-mode.overlay.box-border-opacity=0
                        hint-mode.overlay.font-opacity=0
                        """))), KeyboardLayout.keyboardLayoutByShortName.get("us-qwerty"));
        List<BufferLayeredWindow> layeredWindows = new ArrayList<>();
        HintMeshRenderer hintMeshRenderer = hintMeshRenderer(layeredWindows, () -> {
        });
        show(hintMeshRenderer, new HintMesh.HintMeshBuilder()
                .visible(true)
                .hints(List.of(hint))
                .styleByFilter(configuration.modeMap().get("hint-mode").hintMesh()
                                            .styleByFilter())
                .build(), Set.of(screen));
        assertEquals(0xFF0000FF, layeredWindows.getFirst().pixels.getInt(4 * (220 * 1920 + 300)));
        assertTrue(hintMeshRenderer.windows().iterator().next().showsNothing());
        hintMeshRenderer.hideHintMesh();
        for (TransparentWindow window : hintMeshRenderer.windows())
            window.disposeLater();
    }

    @Test
    void aSavedHintTakesTheSavedColorsOfItsHotspotBox() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        assertEquals(0xFFFFFF00, colors("""
                hint-mode.overlay.hotspot-box-color=#FF0000
                hint-mode.overlay.hotspot-box-opacity=1
                hint-mode.overlay.saved-hotspot-box-color=#FFFF00
                """, List.of(hint), List.of(new Point(380, 300)), probes).getFirst());
    }

    @Test
    void aHotspotBoxCastsTheShadowOfAnElementBoxOfItsShape() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        List<Point> ring = List.of(new Point(198, 220), new Point(199, 205),
                new Point(300, 198), new Point(300, 241), new Point(401, 230),
                new Point(199, 199), new Point(250, 220));
        List<Integer> elementBoxShadow = colors("""
                hint-mode.overlay.ui-element-box-horizontal-padding=0
                hint-mode.overlay.ui-element-box-vertical-padding=0
                hint-mode.overlay.ui-element-box-border-thickness=0
                hint-mode.overlay.ui-element-box-shadow-color=#FF0000
                hint-mode.overlay.ui-element-box-shadow-opacity=0.8
                hint-mode.overlay.ui-element-box-shadow-blur-radius=4
                """, List.of(), List.of(new Point(300, 220)), ring);
        assertEquals(elementBoxShadow, colors("""
                hint-mode.overlay.hotspot-box-width=200
                hint-mode.overlay.hotspot-box-height=40
                hint-mode.overlay.hotspot-box-border-thickness=0
                hint-mode.overlay.hotspot-box-shadow-color=#FF0000
                hint-mode.overlay.hotspot-box-shadow-opacity=0.8
                hint-mode.overlay.hotspot-box-shadow-blur-radius=4
                """, List.of(), List.of(new Point(300, 220)), ring));
        assertTrue(elementBoxShadow.getFirst() >>> 24 > 0);
        assertEquals(0, elementBoxShadow.getLast() >>> 24);
    }

    @Test
    void aHotspotLineCastsItsShadowBesideIt() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        List<Integer> colors = colors("""
                hint-mode.overlay.hotspot-box-width=10
                hint-mode.overlay.hotspot-box-height=10
                hint-mode.overlay.hotspot-line-thickness=4
                hint-mode.overlay.hotspot-line-color=#00FF00
                hint-mode.overlay.hotspot-line-opacity=1
                hint-mode.overlay.hotspot-line-shadow-color=#FF0000
                hint-mode.overlay.hotspot-line-shadow-opacity=1
                hint-mode.overlay.hotspot-line-shadow-blur-radius=10
                """, List.of(), List.of(new Point(380, 300)),
                List.of(new Point(360, 280), new Point(364, 276), new Point(340, 290)));
        assertEquals(0xFF00FF00, colors.getFirst());
        assertTrue(colors.get(1) >>> 24 > 0);
        assertEquals(0, colors.get(1) >> 8 & 0xFF);
        assertEquals(0, colors.getLast());
    }

    @Test
    void aMovedHotspotBoxShadowTakesTheColorWhereTheBoxIs() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        String properties = """
                hint-mode.overlay.hotspot-box-width=10
                hint-mode.overlay.hotspot-box-height=10
                hint-mode.overlay.hotspot-box-shadow-color=across-screen per-pixel top-to-bottom #FF0000 #0000FF
                hint-mode.overlay.hotspot-box-shadow-opacity=1
                hint-mode.overlay.hotspot-box-shadow-blur-radius=10
                """;
        int top = colors(properties, List.of(), List.of(new Point(380, 100)),
                List.of(new Point(388, 100))).getFirst();
        int bottom = colors(properties, List.of(),
                List.of(new Point(380, 100), new Point(380, 1000)),
                List.of(new Point(388, 1000))).getFirst();
        assertTrue((top >> 16 & 0xFF) > (top & 0xFF));
        assertTrue((bottom & 0xFF) > (bottom >> 16 & 0xFF));
    }

    @Test
    void aHotspotLineShadowTakesItsColorsAsTheLineDoes() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        List<Integer> colors = colors("""
                hint-mode.overlay.hotspot-box-width=10
                hint-mode.overlay.hotspot-box-height=10
                hint-mode.overlay.hotspot-line-thickness=4
                hint-mode.overlay.hotspot-line-shadow-color=across-screen per-pixel top-to-bottom #FF0000 #0000FF
                hint-mode.overlay.hotspot-line-shadow-opacity=1
                hint-mode.overlay.hotspot-line-shadow-blur-radius=10
                """, List.of(), List.of(new Point(300, 900)),
                List.of(new Point(304, 300), new Point(304, 850)));
        assertTrue((colors.getFirst() >> 16 & 0xFF) > (colors.getFirst() & 0xFF));
        assertTrue((colors.getLast() & 0xFF) > (colors.getLast() >> 16 & 0xFF));
    }

    @Test
    void aHotspotThatMovesLeavesNoShadowWhereItWas() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        List<Integer> colors = colors("""
                hint-mode.overlay.hotspot-box-width=10
                hint-mode.overlay.hotspot-box-height=10
                hint-mode.overlay.hotspot-box-shadow-opacity=1
                hint-mode.overlay.hotspot-box-shadow-blur-radius=4
                hint-mode.overlay.hotspot-line-shadow-opacity=1
                hint-mode.overlay.hotspot-line-shadow-blur-radius=4
                """, List.of(), List.of(new Point(380, 300), new Point(500, 220)),
                List.of(new Point(386, 300), new Point(360, 280), new Point(506, 220)));
        assertEquals(0, colors.get(0));
        assertEquals(0, colors.get(1));
        assertTrue(colors.get(2) >>> 24 > 0);
    }

    @Test
    void aHotspotShowsNothingByDefault() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        assertEquals(List.of(0, 0, 0xFF0000FF),
                colors("", List.of(), List.of(new Point(380, 300)), probes));
    }

    @Test
    void theBackgroundCoversItsAreaAroundTheHints() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        assertEquals(List.of(0xFFFF0000, 0xFFFFFF00), colors("""
                hint-mode.overlay.hotspot-box-width=10
                hint-mode.overlay.hotspot-box-height=10
                hint-mode.overlay.hotspot-box-color=#FF0000
                hint-mode.overlay.hotspot-box-opacity=1
                hint-mode.overlay.background-color=#FFFF00
                hint-mode.overlay.background-opacity=1
                """, List.of(), List.of(new Point(380, 300)),
                List.of(new Point(380, 300), new Point(1500, 900))));
    }
}
