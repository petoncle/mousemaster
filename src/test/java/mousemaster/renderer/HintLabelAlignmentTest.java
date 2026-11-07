package mousemaster.renderer;

import io.qt.core.QPointF;
import io.qt.core.Qt;
import io.qt.gui.QColor;
import io.qt.gui.QFontMetrics;
import io.qt.gui.QImage;
import io.qt.gui.QPainter;
import io.qt.gui.QPixmap;
import io.qt.widgets.QApplication;
import mousemaster.Color;
import mousemaster.Configuration;
import mousemaster.ConfigurationParser;
import mousemaster.FontStyle;
import mousemaster.FontStyle.FontStyleBuilder;
import mousemaster.FontVerticalAlignment;
import mousemaster.FontWeight;
import mousemaster.GradientColor;
import mousemaster.Hint;
import mousemaster.HintFontStyle;
import mousemaster.HintMesh;
import mousemaster.Key;
import mousemaster.KeyboardLayout;
import mousemaster.Mousemaster;
import mousemaster.MousemasterApplication;
import mousemaster.Os;
import mousemaster.PropertiesReader;
import mousemaster.QtManager;
import mousemaster.Rectangle;
import mousemaster.Screen;
import mousemaster.Zoom;
import mousemaster.qt.QtFontStyle;
import mousemaster.qt.QtHintFont;
import mousemaster.qt.QtHintFontStyle;
import mousemaster.qt.TransparentWindow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HintLabelAlignmentTest {

    private static final Color.LastSelectedHintColors white = new Color.LastSelectedHintColors(
            GradientColor.parse("#FFFFFF"), GradientColor.parse("#FFFFFF"));

    private static boolean qtAvailable;

    @BeforeAll
    static void initializeQt() {
        MousemasterApplication.tempDirectory =
                System.getProperty("java.io.tmpdir") + "/mousemaster-label-alignment-test";
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

    /** The space above the capitals and below them differ by the pixel the baseline is rounded to. */
    @Test
    void capitalsAreCenteredInTheirBox() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        double screenScale = Os.macos ? 2 :
                QApplication.primaryScreen().logicalDotsPerInchX() / 96;
        double qtScaleFactor = Os.windows ? 1 : screenScale;
        List<String> offCenter = new ArrayList<>();
        for (double size : new double[] {8, 10, 13, 18}) {
            for (FontWeight weight : new FontWeight[] {FontWeight.NORMAL, FontWeight.BOLD}) {
                QtFontStyle style = QtHintFont.qtFontStyle(
                        fontStyle(FontStyle.defaultName, size, weight), screenScale, white);
                int lineHeight = style.metrics().height();
                for (int boxHeight = lineHeight; boxHeight < lineHeight + 4; boxHeight++) {
                    double baseline = HintMeshRenderer.baselineY(
                            FontVerticalAlignment.CAP_HEIGHT, boxHeight, style.metrics(), 0, 0,
                            qtScaleFactor);
                    double[] gaps = gaps(style, boxHeight, baseline, qtScaleFactor);
                    if (Math.abs(gaps[0] - gaps[1]) > 1)
                        offCenter.add(String.format(
                                "size %s %s, box %d: %.2f pixels above, %.2f below", size,
                                weight, boxHeight, gaps[0], gaps[1]));
                }
            }
        }
        assertTrue(offCenter.isEmpty(), String.join("\n", offCenter));
    }

    /** A label is where Qt draws its text centered in the box, on the step a glyph is placed on. */
    @Test
    void labelsAreCenteredInTheirBox() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        double screenScale = Os.macos ? 2 :
                QApplication.primaryScreen().logicalDotsPerInchX() / 96;
        double qtScaleFactor = Os.windows ? 1 : screenScale;
        List<String> offCenter = new ArrayList<>();
        for (String name : new String[] {FontStyle.defaultName,
                Os.macos ? "Helvetica Neue" : "Segoe UI"}) {
            for (double size : new double[] {8, 10, 13, 18}) {
                for (FontWeight weight : new FontWeight[] {FontWeight.NORMAL, FontWeight.BOLD}) {
                    FontStyle fontStyle = fontStyle(name, size, weight);
                    QtHintFontStyle style = QtHintFont.qtHintFontStyle(
                            new HintFontStyle(fontStyle, 0, fontStyle, fontStyle, fontStyle), null,
                            screenScale, white);
                    QFontMetrics metrics = style.defaultStyle().metrics();
                    double glyphPositionsPerUnit = qtScaleFactor *
                            QtHintFont.glyphPositionsPerPixel(metrics, qtScaleFactor);
                    double baseline = HintMeshRenderer.baselineY(FontVerticalAlignment.CAP_HEIGHT,
                            metrics.height(), metrics, 0, 0, qtScaleFactor);
                    for (String label : new String[] {"uu", "uw", "vd", "vv", "nk", "aj", "wl",
                            "ft", "lv", "av", "ta", "ly"}) {
                        List<Key> keys = label.chars()
                                              .mapToObj(c -> Key.ofName(Character.toString(c)))
                                              .toList();
                        Map<String, Integer> xAdvancesByString = new HashMap<>();
                        for (Key key : keys)
                            xAdvancesByString.computeIfAbsent(key.hintLabel(),
                                    metrics::horizontalAdvance);
                        String text =
                                keys.stream().map(Key::hintLabel).collect(Collectors.joining());
                        int totalXAdvance = metrics.horizontalAdvance(text);
                        int hintKeyMaxXAdvance = Collections.max(xAdvancesByString.values());
                        for (int boxWidth = totalXAdvance + 4; boxWidth < totalXAdvance + 8;
                             boxWidth++) {
                            HintMeshRenderer.HintLabel hintLabel = HintMeshRenderer.HintLabel.of(
                                    keys, xAdvancesByString, boxWidth, metrics.height(),
                                    totalXAdvance, -1, style, hintKeyMaxXAdvance, -1,
                                    FontVerticalAlignment.CAP_HEIGHT, false, qtScaleFactor);
                            double[] gaps = horizontalGaps(hintLabel::paint, boxWidth,
                                    metrics.height(), qtScaleFactor);
                            double textX = Math.round((boxWidth - totalXAdvance) / 2d *
                                                      glyphPositionsPerUnit) / glyphPositionsPerUnit;
                            double[] qtGaps = horizontalGaps(painter -> {
                                painter.setFont(style.defaultStyle().font());
                                painter.setPen(new QColor(255, 255, 255));
                                painter.drawText(new QPointF(textX, baseline), text);
                            }, boxWidth, metrics.height(), qtScaleFactor);
                            if (Math.abs(gaps[0] - qtGaps[0]) > 1e-9 ||
                                Math.abs(gaps[1] - qtGaps[1]) > 1e-9)
                                offCenter.add(String.format(
                                        "%s %s %s, %s in a box %d wide: %.2f pixels left, %.2f right, Qt %.2f, %.2f",
                                        name, size, weight, label, boxWidth, gaps[0], gaps[1],
                                        qtGaps[0], qtGaps[1]));
                        }
                    }
                }
            }
        }
        assertTrue(offCenter.isEmpty(), String.join("\n", offCenter));
    }

    private static final List<String> boxLabels = List.of("ic", "ci", "ii", "mw");

    /** A grid's box narrower than its cell holds its keys, in columns as wide as the widest key. */
    @Test
    void aGridBoxHoldsTheColumnsOfItsKeys() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        double screenScale = Os.macos ? 2 :
                QApplication.primaryScreen().logicalDotsPerInchX() / 96;
        int cellWidth = (int) (200 * screenScale);
        int cellHeight = (int) (40 * screenScale);
        List<Hint> hints = new ArrayList<>();
        for (int labelIndex = 0; labelIndex < boxLabels.size(); labelIndex++)
            hints.add(new Hint(cellWidth * (labelIndex % 2 + 0.5),
                    cellHeight * (labelIndex / 2 + 0.5), cellWidth, cellHeight,
                    keys(boxLabels.get(labelIndex)), null));
        List<DrawnHint> drawnHints = drawnHints(
                boxProperties(0.5) + "hint-mode.overlay.box-width-percent=0\n", hints,
                new Rectangle(0, 0, 2 * cellWidth, 2 * cellHeight), screenScale, cellWidth,
                cellHeight);
        List<String> wrongBoxes = inkOutsideBoxes(drawnHints, "");
        assertTrue(wrongBoxes.isEmpty(), String.join("\n", wrongBoxes));
        assertEquals(1, drawnHints.stream()
                                  .map(drawnHint -> drawnHint.box().width())
                                  .distinct()
                                  .count());
    }

    /** Outside a grid, a box holds its text, or from a spacing of 0.5 its columns as wide as the
     *  widest key, and the padding: a spacing above 0.5 acts as 0.5. */
    @Test
    void aBoxOutsideAGridHoldsItsTextOrItsColumns() throws IOException {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        double screenScale = Os.macos ? 2 :
                QApplication.primaryScreen().logicalDotsPerInchX() / 96;
        double qtScaleFactor = Os.windows ? 1 : screenScale;
        int areaWidth = (int) (300 * screenScale);
        List<Hint> hints = new ArrayList<>();
        for (int labelIndex = 0; labelIndex < boxLabels.size(); labelIndex++)
            hints.add(new Hint(areaWidth * (labelIndex + 0.5), areaWidth / 2d, -1, -1,
                    keys(boxLabels.get(labelIndex)), null));
        FontStyle fontStyle = fontStyle(boxFontName(), 10, FontWeight.BOLD);
        QFontMetrics metrics = QtHintFont.qtHintFontStyle(
                new HintFontStyle(fontStyle, 0, fontStyle, fontStyle, fontStyle), null,
                screenScale, white).defaultStyle().metrics();
        int columnsWidth = 2 * boxLabels.stream()
                                        .flatMap(label -> keys(label).stream())
                                        .mapToInt(key -> metrics.horizontalAdvance(
                                                key.hintLabel()))
                                        .max()
                                        .orElseThrow();
        int padding = (int) Math.round(3 * screenScale);
        List<String> wrongBoxes = new ArrayList<>();
        for (double fontSpacingPercent : new double[] {0, 0.5, 0.7, 1}) {
            List<DrawnHint> drawnHints = drawnHints(boxProperties(fontSpacingPercent), hints,
                    new Rectangle(0, 0, boxLabels.size() * areaWidth, areaWidth), screenScale,
                    areaWidth, areaWidth);
            wrongBoxes.addAll(inkOutsideBoxes(drawnHints, " at spacing " + fontSpacingPercent));
            for (DrawnHint drawnHint : drawnHints) {
                String text = drawnHint.hint().keySequence().stream().map(Key::hintLabel)
                                       .collect(Collectors.joining());
                int width = fontSpacingPercent < 0.5 ? metrics.horizontalAdvance(text) :
                        columnsWidth;
                int expectedBoxWidth = (int) Math.round(width * qtScaleFactor) + 2 * padding;
                if (drawnHint.box().width() != expectedBoxWidth)
                    wrongBoxes.add(String.format("%s at spacing %s: box %d wide, %d expected",
                            text, fontSpacingPercent, drawnHint.box().width(),
                            expectedBoxWidth));
            }
        }
        assertTrue(wrongBoxes.isEmpty(), String.join("\n", wrongBoxes));
    }

    private static String boxFontName() {
        return Os.macos ? "Helvetica Neue" : "Segoe UI";
    }

    /** Only the keys ink: no box fill, border or shadow. */
    private static String boxProperties(double fontSpacingPercent) {
        return """
                idle-mode.to.hint-mode=+u
                hint-mode.to.idle-mode=+esc
                hint-mode.overlay.selection-keys=c i m w
                hint-mode.overlay.font-name=%s
                hint-mode.overlay.font-size=10
                hint-mode.overlay.font-weight=bold
                hint-mode.overlay.font-shadow-opacity=0
                hint-mode.overlay.font-spacing-percent=%s
                hint-mode.overlay.box-horizontal-padding=3
                hint-mode.overlay.box-opacity=0
                hint-mode.overlay.box-border-opacity=0
                hint-mode.overlay.box-shadow-opacity=0
                hint-mode.overlay.fade-enabled=false
                hint-mode.overlay.transition-enabled=false
                """.formatted(boxFontName(), fontSpacingPercent);
    }

    private static List<Key> keys(String label) {
        return label.chars().mapToObj(c -> Key.ofName(Character.toString(c))).toList();
    }

    private record DrawnHint(Hint hint, Rectangle box, int inkLeft, int inkRight) {
    }

    private static List<String> inkOutsideBoxes(List<DrawnHint> drawnHints, String context) {
        List<String> inkOutsideBoxes = new ArrayList<>();
        for (DrawnHint drawnHint : drawnHints) {
            Rectangle box = drawnHint.box();
            if (drawnHint.inkLeft() > drawnHint.inkRight() || drawnHint.inkLeft() < box.x() ||
                drawnHint.inkRight() > box.x() + box.width())
                inkOutsideBoxes.add(String.format("%s%s: ink %d to %d, box %d to %d",
                        drawnHint.hint().keySequence(), context, drawnHint.inkLeft(),
                        drawnHint.inkRight(), box.x(), box.x() + box.width()));
        }
        return inkOutsideBoxes;
    }

    /** Each hint's box, and where its keys ink in the area around its center, from the mesh of
     *  the config's hint-mode drawn off the screen. */
    private static List<DrawnHint> drawnHints(String properties, List<Hint> hints,
                                              Rectangle screenRectangle, double screenScale,
                                              int areaWidth, int areaHeight)
            throws IOException {
        return drawn(properties, new HintMesh.HintMeshBuilder().hints(hints),
                new Screen(screenRectangle, 96, screenScale), (image, hintMeshRenderer) -> {
                    List<DrawnHint> drawnHints = new ArrayList<>();
                    for (Hint hint : hints) {
                        int inkLeft = Integer.MAX_VALUE;
                        int inkRight = Integer.MIN_VALUE;
                        for (int x = (int) (hint.centerX() - areaWidth / 2d);
                             x < hint.centerX() + areaWidth / 2d; x++) {
                            for (int y = (int) (hint.centerY() - areaHeight / 2d);
                                 y < hint.centerY() + areaHeight / 2d; y++) {
                                if ((image.pixel(x, y) >>> 24) == 0)
                                    continue;
                                inkLeft = Math.min(inkLeft, x);
                                inkRight = Math.max(inkRight, x + 1);
                            }
                        }
                        drawnHints.add(new DrawnHint(hint,
                                hintMeshRenderer.hintBoxRectangle(hint), inkLeft, inkRight));
                    }
                    return drawnHints;
                });
    }

    static <T> T drawn(String properties, HintMesh.HintMeshBuilder hintMeshBuilder,
                       Screen screen, BiFunction<QImage, HintMeshRenderer, T> read)
            throws IOException {
        List<String> lines = PropertiesReader.readPropertiesFile(
                new BufferedReader(new StringReader(properties)));
        Configuration configuration = ConfigurationParser.parse(lines,
                KeyboardLayout.keyboardLayoutByShortName.get("us-qwerty"));
        HintMesh hintMesh = hintMeshBuilder
                .visible(true)
                .styleByFilter(configuration.modeMap().get("hint-mode").hintMesh()
                                            .styleByFilter())
                .build();
        HintMeshRenderer hintMeshRenderer = new HintMeshRenderer(() -> {
            TransparentWindow window = new TransparentWindow();
            window.setAttribute(Qt.WidgetAttribute.WA_DontShowOnScreen);
            return window;
        }, () -> {
        });
        hintMeshRenderer.setHintMesh(hintMesh,
                new Zoom(1, screen.rectangle().center(), screen.rectangle()), false,
                Set.of(screen));
        for (int frame = 0; frame < 10 || hintMeshRenderer.buildPending(); frame++) {
            QApplication.processEvents();
            hintMeshRenderer.update(Mousemaster.FRAME_INTERVAL);
            hintMeshRenderer.runPendingWork();
        }
        QImage image = new QImage(screen.rectangle().width(), screen.rectangle().height(),
                QImage.Format.Format_ARGB32_Premultiplied);
        image.fill(0);
        QPainter painter = new QPainter(image);
        for (TransparentWindow window : hintMeshRenderer.windows()) {
            if (!window.isVisible())
                continue;
            QPixmap pixmap = window.grab();
            painter.drawPixmap(window.xInPixels(), window.yInPixels(), pixmap);
            pixmap.dispose();
        }
        painter.end();
        T found = read.apply(image, hintMeshRenderer);
        painter.dispose();
        image.dispose();
        hintMeshRenderer.hideHintMesh();
        for (TransparentWindow window : hintMeshRenderer.windows())
            window.disposeLater();
        return found;
    }

    /** The box of a grid without selection keys is its cell, however small. */
    @Test
    void aLabelWithoutKeysHasNoHeight() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        FontStyle fontStyle = fontStyle(FontStyle.defaultName, 10, FontWeight.NORMAL);
        QtHintFontStyle style = QtHintFont.qtHintFontStyle(
                new HintFontStyle(fontStyle, 0, fontStyle, fontStyle, fontStyle), null, 1, white);
        HintMeshRenderer.HintLabel hintLabel = HintMeshRenderer.HintLabel.of(List.of(),
                new HashMap<>(), 7, 7, 0, -1, style, 0, -1, FontVerticalAlignment.CAP_HEIGHT,
                false, 1);
        assertEquals(0, hintLabel.tightHintBoxHeight);
    }

    /** In pixels, from the box's left to the ink and from the ink to the box's right. */
    private static double[] horizontalGaps(Consumer<QPainter> paint, int boxWidth,
                                           int boxHeight, double qtScaleFactor) {
        int width = (int) Math.round(boxWidth * qtScaleFactor);
        int height = (int) Math.round(boxHeight * qtScaleFactor);
        QImage image = new QImage(width, height, QImage.Format.Format_ARGB32_Premultiplied);
        image.setDevicePixelRatio(qtScaleFactor);
        image.fill(0);
        QPainter painter = new QPainter(image);
        paint.accept(painter);
        painter.end();
        double left = -1, right = -1;
        for (int x = 0; x < width; x++) {
            int alpha = 0;
            for (int y = 0; y < height; y++)
                alpha = Math.max(alpha, image.pixel(x, y) >>> 24);
            if (alpha == 0)
                continue;
            if (left == -1)
                left = x + 1 - alpha / 255d;
            right = x + alpha / 255d;
        }
        painter.dispose();
        image.dispose();
        return new double[] {left, width - right};
    }

    /** In pixels, from the box's top to the ink and from the ink to the box's bottom. */
    private static double[] gaps(QtFontStyle style, int boxHeight, double baseline,
                                 double qtScaleFactor) {
        int width = (int) Math.ceil(style.metrics().maxWidth() * 2 * qtScaleFactor);
        int height = (int) Math.round(boxHeight * qtScaleFactor);
        QImage image = new QImage(width, height, QImage.Format.Format_ARGB32_Premultiplied);
        image.setDevicePixelRatio(qtScaleFactor);
        image.fill(0);
        QPainter painter = new QPainter(image);
        painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
        painter.setRenderHint(QPainter.RenderHint.TextAntialiasing, true);
        painter.setFont(style.font());
        painter.setPen(new QColor(255, 255, 255));
        painter.drawText(new QPointF(0, baseline), "EF");
        painter.end();
        double top = -1, bottom = -1;
        for (int y = 0; y < height; y++) {
            int alpha = 0;
            for (int x = 0; x < width; x++)
                alpha = Math.max(alpha, image.pixel(x, y) >>> 24);
            if (alpha == 0)
                continue;
            if (top == -1)
                top = y + 1 - alpha / 255d;
            bottom = y + alpha / 255d;
        }
        painter.dispose();
        image.dispose();
        return new double[] {top, height - bottom};
    }

    private static FontStyle fontStyle(String name, double size, FontWeight weight) {
        FontStyleBuilder builder = new FontStyleBuilder()
                .name(name)
                .weight(weight)
                .size(size)
                .color(Color.parse("#FFFFFF"))
                .opacity(1d)
                .outlineThickness(0d)
                .outlineColor(Color.parse("#000000"))
                .outlineOpacity(0d)
                .verticalAlignment(FontVerticalAlignment.CAP_HEIGHT);
        builder.shadow()
               .blurRadius(0d)
               .color(Color.parse("#000000"))
               .opacity(0d)
               .horizontalOffset(0d)
               .verticalOffset(0d)
               .stackCount(1);
        return builder.build();
    }

}
