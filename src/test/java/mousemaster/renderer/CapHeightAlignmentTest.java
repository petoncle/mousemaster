package mousemaster.renderer;

import io.qt.core.QPointF;
import io.qt.gui.QColor;
import io.qt.gui.QImage;
import io.qt.gui.QPainter;
import io.qt.widgets.QApplication;
import mousemaster.Color;
import mousemaster.FontStyle;
import mousemaster.FontStyle.FontStyleBuilder;
import mousemaster.FontVerticalAlignment;
import mousemaster.FontWeight;
import mousemaster.MousemasterApplication;
import mousemaster.Os;
import mousemaster.QtManager;
import mousemaster.qt.QtFontStyle;
import mousemaster.qt.QtHintFont;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CapHeightAlignmentTest {

    private static boolean qtAvailable;

    @BeforeAll
    static void initializeQt() {
        MousemasterApplication.tempDirectory =
                System.getProperty("java.io.tmpdir") + "/mousemaster-cap-height-test";
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
                QtFontStyle style =
                        QtHintFont.qtFontStyle(fontStyle(size, weight), screenScale, "#FFFFFF");
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

    private static FontStyle fontStyle(double size, FontWeight weight) {
        FontStyleBuilder builder = new FontStyleBuilder()
                .name(FontStyle.defaultName)
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
