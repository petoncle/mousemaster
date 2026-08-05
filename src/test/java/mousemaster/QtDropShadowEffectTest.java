package mousemaster;

import io.qt.core.QRect;
import io.qt.core.QRectF;
import io.qt.gui.QBrush;
import io.qt.gui.QColor;
import io.qt.gui.QImage;
import io.qt.gui.QPainter;
import io.qt.gui.QPixmap;
import io.qt.widgets.QGraphicsDropShadowEffect;
import io.qt.widgets.QGraphicsEffect;
import io.qt.widgets.QGraphicsPixmapItem;
import io.qt.widgets.QGraphicsScene;
import mousemaster.qt.StackedShadowEffect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The ported blur has to stay bit-exact with the one it replaces, or shadows change appearance.
 * Renders the same source through Qt's own drop shadow and through ours, and compares every byte.
 */
class QtDropShadowEffectTest {

    private static final int WIDTH = 220, HEIGHT = 160;

    private static boolean qtAvailable;

    @BeforeAll
    static void initializeQt() {
        try {
            MousemasterApplication.tempDirectory =
                    System.getProperty("java.io.tmpdir") + "/mousemaster-shadow-test";
            QtManager.initialize();
            new QImage(1, 1, QImage.Format.Format_ARGB32_Premultiplied).dispose();
            qtAvailable = true;
        } catch (Throwable e) {
            qtAvailable = false;
        }
    }

    @Test
    void portedShadowMatchesQtsOwn() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        // Radii either side of 4, where Qt halves the image before blurring; offsets whole,
        // fractional and negative; shadow colors opaque and translucent.
        double[] radii = {0.5, 2, 3.9, 4, 6, 12};
        double[][] offsets = {{0, 0}, {1, 1}, {0.5, 0.5}, {-2, 3}};
        QColor[] colors = {new QColor(0, 0, 0, 255), new QColor(200, 30, 30, 128)};
        List<String> mismatches = new ArrayList<>();
        for (double radius : radii)
            for (double[] offset : offsets)
                for (QColor color : colors) {
                    byte[] qt = render(() -> {
                        QGraphicsDropShadowEffect effect = new QGraphicsDropShadowEffect();
                        effect.setBlurRadius(radius);
                        effect.setOffset(offset[0], offset[1]);
                        effect.setColor(color);
                        return effect;
                    });
                    byte[] ported = render(() -> {
                        StackedShadowEffect effect = new StackedShadowEffect();
                        effect.setBlurRadius(radius);
                        effect.setOffset(offset[0], offset[1]);
                        effect.setColor(color);
                        effect.setStackCount(1);
                        return effect;
                    });
                    int differing = 0, maxDelta = 0;
                    for (int i = 0; i < qt.length; i++) {
                        int delta = Math.abs((qt[i] & 0xFF) - (ported[i] & 0xFF));
                        if (delta > 0)
                            differing++;
                        maxDelta = Math.max(maxDelta, delta);
                    }
                    if (differing != 0)
                        mismatches.add(String.format(
                                "radius=%s offset=(%s,%s) alpha=%d: %d bytes differ, max %d",
                                radius, offset[0], offset[1], color.alpha(), differing, maxDelta));
                }
        assertTrue(mismatches.isEmpty(),
                "the ported blur no longer matches Qt's:\n" + String.join("\n", mismatches));
    }

    /** The source through a scene carrying {@code effect}, as the hint mesh renders its shadows. */
    private static byte[] render(Supplier<QGraphicsEffect> effect) {
        QGraphicsScene scene = new QGraphicsScene();
        QPixmap sourcePixmap = QPixmap.fromImage(source());
        QGraphicsPixmapItem item = scene.addPixmap(sourcePixmap);
        item.setGraphicsEffect(effect.get());
        QImage result = new QImage(WIDTH, HEIGHT, QImage.Format.Format_ARGB32_Premultiplied);
        QColor transparent = new QColor(0, 0, 0, 0);
        result.fill(transparent);
        transparent.dispose();
        QPainter painter = new QPainter(result);
        QRect resultRect = result.rect();
        QRectF target = new QRectF(resultRect);
        QRectF sourceRect = new QRectF(0, 0, WIDTH, HEIGHT);
        scene.render(painter, target, sourceRect);
        resultRect.dispose();
        target.dispose();
        sourceRect.dispose();
        painter.end();
        painter.dispose();
        scene.dispose();
        sourcePixmap.dispose();
        byte[] bytes = new byte[WIDTH * HEIGHT * 4];
        result.bits().get(0, bytes, 0, bytes.length);
        result.dispose();
        return bytes;
    }

    /** Antialiased edges and partial alpha, which is where the two paths could disagree. Shapes
     *  rather than text, so the test does not depend on an installed font. */
    private static QImage source() {
        QImage image = new QImage(WIDTH, HEIGHT, QImage.Format.Format_ARGB32_Premultiplied);
        QColor transparent = new QColor(0, 0, 0, 0);
        image.fill(transparent);
        transparent.dispose();
        QPainter painter = new QPainter(image);
        painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
        painter.setPen(new QColor(255, 255, 255, 255));
        for (int i = 0; i < 6; i++) {
            painter.setBrush(new QBrush(new QColor(255, 255, 255, 40 + i * 43)));
            painter.drawEllipse(12 + i * 32, 20 + (i % 3) * 34, 26, 22);
        }
        painter.fillRect(new QRect(0, HEIGHT - 12, WIDTH, 6),
                new QBrush(new QColor(255, 255, 255, 255)));
        painter.end();
        painter.dispose();
        return image;
    }
}
