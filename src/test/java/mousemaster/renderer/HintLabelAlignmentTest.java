package mousemaster.renderer;

import io.qt.core.QPointF;
import io.qt.gui.QColor;
import io.qt.gui.QFontMetrics;
import io.qt.gui.QImage;
import io.qt.gui.QPainter;
import io.qt.widgets.QApplication;
import mousemaster.Color;
import mousemaster.FontStyle;
import mousemaster.FontStyle.FontStyleBuilder;
import mousemaster.FontVerticalAlignment;
import mousemaster.FontWeight;
import mousemaster.GradientColor;
import mousemaster.HintFontStyle;
import mousemaster.Key;
import mousemaster.MousemasterApplication;
import mousemaster.Os;
import mousemaster.QtManager;
import mousemaster.qt.QtFontStyle;
import mousemaster.qt.QtHintFont;
import mousemaster.qt.QtHintFontStyle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
