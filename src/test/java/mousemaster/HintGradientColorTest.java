package mousemaster;

import io.qt.core.Qt;
import io.qt.gui.QImage;
import io.qt.gui.QPainter;
import mousemaster.HintGradientColor.HintGradientArea;
import mousemaster.HintGradientColor.HintGradientDirection;
import mousemaster.HintGradientColor.HintGradientStep;
import mousemaster.qt.QtColorUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HintGradientColorTest {

    private static final Rectangle area = new Rectangle(100, 200, 400, 800);

    @Test
    void oneColorIsSolid() {
        HintGradientColor color = HintGradientColor.parse("#FF00AA");
        assertFalse(color.gradient());
        assertEquals("#FF00AA", color.hexColor());
        assertEquals(0xFF00AA, color.rgbAt(area, 300, 1000));
    }

    @Test
    void keywordsAreOrderFree() {
        HintGradientColor color =
                HintGradientColor.parse("#FF0000 per-group #0000FF across-screen right-to-left");
        assertEquals(List.of("#FF0000", "#0000FF"), color.hexColors());
        assertEquals(HintGradientDirection.RIGHT_TO_LEFT, color.direction());
        assertEquals(HintGradientArea.SCREEN, color.area());
        assertEquals(HintGradientStep.GROUP, color.step());
    }

    @Test
    void defaultsAreContentTopToBottomPerElement() {
        HintGradientColor color = HintGradientColor.parse("#FF0000 #0000FF");
        assertEquals(HintGradientDirection.TOP_TO_BOTTOM, color.direction());
        assertEquals(HintGradientArea.CONTENT, color.area());
        assertEquals(HintGradientStep.ELEMENT, color.step());
    }

    @Test
    void stopsSitAtTheAreaEdgesAndAreEvenlySpaced() {
        HintGradientColor color = HintGradientColor.parse("#FF0000 #00FF00 #0000FF");
        assertEquals(0xFF0000, color.rgbAt(area, 300, 200));
        assertEquals(0x00FF00, color.rgbAt(area, 300, 600));
        assertEquals(0x0000FF, color.rgbAt(area, 300, 1000));
    }

    /** Through OkLab, so red to green passes through gold rather than through olive mud. */
    @Test
    void betweenTwoColorsTheSweepKeepsItsLightness() {
        assertEquals(0xD0A800, HintGradientColor.parse("#FF0000 #00FF00").rgbAt(0.5));
        assertEquals(0x6CABC7, HintGradientColor.parse("#FFFF00 #0000FF").rgbAt(0.5));
        assertEquals(0x636363, HintGradientColor.parse("#000000 #FFFFFF").rgbAt(0.5));
    }

    @Test
    void aPointOutsideTheAreaClampsToAStop() {
        HintGradientColor color = HintGradientColor.parse("#FF0000 #0000FF");
        assertEquals(0xFF0000, color.rgbAt(area, 300, -5000));
        assertEquals(0x0000FF, color.rgbAt(area, 300, 5000));
    }

    @Test
    void aDirectionPicksItsAxis() {
        HintGradientColor color = HintGradientColor.parse("left-to-right #FF0000 #0000FF");
        assertEquals(0xFF0000, color.rgbAt(area, 100, 600));
        assertEquals(0x0000FF, color.rgbAt(area, 500, 600));
        assertEquals(0xFF0000, color.rgbAt(area, 100, 1000));
    }

    @Test
    void aRoundSweepRunsOutFromTheAreaCenter() {
        HintGradientColor color = HintGradientColor.parse("center-to-corner #FF0000 #0000FF");
        assertEquals(HintGradientColor.HintGradientShape.CIRCLE, color.direction().shape());
        assertEquals(0xFF0000, color.rgbAt(area, 300, 600));
        assertEquals(0x0000FF, color.rgbAt(area, 500, 1000));
        assertEquals(0x0000FF, color.rgbAt(area, 100, 200));
        // An edge is nearer than a corner, so it stops short of the last color.
        assertNotEquals(0x0000FF, color.rgbAt(area, 300, 1000));
    }

    /** The reason it exists: a circle reaches its last color only at the corners, so the area's
     *  short axis never traverses the whole sweep. This area is 400x800, so that axis is x. */
    @Test
    void anEllipticalSweepReachesEveryEdge() {
        HintGradientColor circle = HintGradientColor.parse("center-to-corner #FF0000 #0000FF");
        HintGradientColor ellipse = HintGradientColor.parse("center-to-edge #FF0000 #0000FF");
        assertEquals(0.45, circle.sweepPosition(area, 100, 600), 0.01);
        assertEquals(0.89, circle.sweepPosition(area, 300, 200), 0.01);
        assertEquals(1, ellipse.sweepPosition(area, 300, 200), 0.001);
        assertEquals(1, ellipse.sweepPosition(area, 100, 600), 0.001);
        assertEquals(0, ellipse.sweepPosition(area, 300, 600), 0.001);
        assertEquals(0x0000FF, ellipse.rgbAt(area, 300, 200));
        assertEquals(0x0000FF, ellipse.rgbAt(area, 100, 600));
        assertEquals(0xFF0000, ellipse.rgbAt(area, 300, 600));
    }

    /** A group is one column wide under the default layout, so a flat area is the common case. */
    @Test
    void anAreaFlatOnOneAxisLeavesThatAxisNothingToSay() {
        HintGradientColor ellipse = HintGradientColor.parse("center-to-edge #FF0000 #0000FF");
        Rectangle column = new Rectangle(100, 200, 0, 800);
        assertEquals(0xFF0000, ellipse.rgbAt(column, 100, 600));
        assertEquals(0x0000FF, ellipse.rgbAt(column, 100, 1000));
        Rectangle row = new Rectangle(100, 200, 400, 0);
        assertEquals(0xFF0000, ellipse.rgbAt(row, 300, 200));
        assertEquals(0x0000FF, ellipse.rgbAt(row, 500, 200));
    }

    /** Qt has to ramp over a flat area too, not collapse to a zero-radius sweep. */
    @Test
    void aFlatAreaStillRampsWhenQtPaintsIt() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        HintGradientColor ellipse = HintGradientColor.parse("center-to-edge #FF0000 #0000FF");
        for (Rectangle flat : new Rectangle[] {new Rectangle(0, 0, 0, 32),
                new Rectangle(0, 0, 64, 0)}) {
            QImage image = new QImage(64, 32, QImage.Format.Format_ARGB32_Premultiplied);
            QPainter painter = new QPainter(image);
            painter.setPen(Qt.PenStyle.NoPen);
            painter.setBrush(QtColorUtil.qBrush(ellipse, 1,
                    ellipse.direction().start(flat), ellipse.direction().end(flat)));
            painter.drawRect(0, 0, 64, 32);
            painter.end();
            assertNotEquals(image.pixel(0, 0) & 0xFFFFFF, image.pixel(32, 16) & 0xFFFFFF,
                    "flat " + flat + " painted one color");
        }
    }

    @Test
    void aRoundSweepReverses() {
        HintGradientColor circle = HintGradientColor.parse("corner-to-center #FF0000 #0000FF");
        assertEquals(0x0000FF, circle.rgbAt(area, 300, 600));
        assertEquals(0xFF0000, circle.rgbAt(area, 500, 1000));
        HintGradientColor ellipse = HintGradientColor.parse("edge-to-center #FF0000 #0000FF");
        assertEquals(0x0000FF, ellipse.rgbAt(area, 300, 600));
        assertEquals(0xFF0000, ellipse.rgbAt(area, 300, 200));
    }

    @Test
    void perPixelParses() {
        HintGradientColor color = HintGradientColor.parse("per-pixel #FF0000 #0000FF");
        assertEquals(HintGradientStep.PIXEL, color.step());
    }

    @Test
    void anInvalidTokenIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HintGradientColor.parse("#FF0000 sideways #0000FF"));
        assertThrows(IllegalArgumentException.class,
                () -> HintGradientColor.parse("across-screen"));
        assertThrows(IllegalArgumentException.class,
                () -> HintGradientColor.parse("across-element per-group #FF0000 #0000FF"));
        assertThrows(IllegalArgumentException.class,
                () -> HintGradientColor.parse("across-group per-group #FF0000 #0000FF"));
    }

    @Test
    void oneKeywordPerAxis() {
        assertThrows(IllegalArgumentException.class, () -> HintGradientColor.parse(
                "right-to-left corner-to-center #FF0000 #0000FF"));
        assertThrows(IllegalArgumentException.class, () -> HintGradientColor.parse(
                "across-screen across-content #FF0000 #0000FF"));
        assertThrows(IllegalArgumentException.class, () -> HintGradientColor.parse(
                "per-pixel per-element #FF0000 #0000FF"));
        assertThrows(IllegalArgumentException.class, () -> HintGradientColor.parse(
                "top-to-bottom top-to-bottom #FF0000 #0000FF"));
    }

    private static boolean qtAvailable;

    @BeforeAll
    static void initializeQt() {
        MousemasterApplication.tempDirectory =
                System.getProperty("java.io.tmpdir") + "/mousemaster-hint-gradient-test";
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

    /**
     * A sampled color and one Qt paints have to match, or a box would change color just from
     * switching between an area Qt paints and one that is sampled.
     */
    @Test
    void aSampledColorMatchesTheOneQtPaints() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        for (String direction : new String[] {"top-to-bottom", "right-to-left",
                "topleft-to-bottomright", "center-to-corner", "corner-to-center", "center-to-edge",
                "edge-to-center"})
            assertSampledMatchesPainted(direction);
    }

    private static void assertSampledMatchesPainted(String direction) {
        HintGradientColor color =
                HintGradientColor.parse(direction + " #FF0000 #00FF00 #0000FF");
        Rectangle imageArea = new Rectangle(0, 0, 64, 32);
        QImage image = new QImage(imageArea.width(), imageArea.height(),
                QImage.Format.Format_ARGB32_Premultiplied);
        QPainter painter = new QPainter(image);
        painter.setPen(Qt.PenStyle.NoPen);
        painter.setBrush(QtColorUtil.qBrush(color, 1, color.direction().start(imageArea),
                color.direction().end(imageArea)));
        painter.drawRect(0, 0, imageArea.width(), imageArea.height());
        painter.end();
        for (int y = 0; y < imageArea.height(); y += 8) {
            for (int x = 0; x < imageArea.width(); x += 8) {
                int painted = image.pixel(x, y) & 0xFFFFFF;
                int sampled = color.rgbAt(imageArea, x + 0.5, y + 0.5);
                for (int shift = 16; shift >= 0; shift -= 8)
                    assertTrue(Math.abs(((painted >> shift) & 0xFF) -
                                        ((sampled >> shift) & 0xFF)) <= 2,
                            direction + " at (" + x + ", " + y + "): painted " +
                            Integer.toHexString(painted) + ", sampled " +
                            Integer.toHexString(sampled));
            }
        }
    }

}
