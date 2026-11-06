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
        MousemasterApplication.tempDirectory =
                System.getProperty("java.io.tmpdir") + "/mousemaster-shadow-test";
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
                    byte[] qt = render(source(0xFFFFFF), () -> {
                        QGraphicsDropShadowEffect effect = new QGraphicsDropShadowEffect();
                        effect.setBlurRadius(radius);
                        effect.setOffset(offset[0], offset[1]);
                        effect.setColor(color);
                        return effect;
                    });
                    byte[] ported = render(source(0xFFFFFF), () -> {
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

    /** A shadow that is not alpha only blurs each channel of the source the way the alpha is
     *  blurred, as qt_blurImage does: each of its channels is the shadow of a source whose alpha
     *  is that channel. Compared where the source is transparent, where only the shadow is. */
    @Test
    void shadowNotAlphaOnlyBlursEachChannelAsTheAlpha() {
        assumeTrue(qtAvailable, "Qt natives are unavailable here");
        QImage colored = source(0xE62814, 0x1EC85A, 0x283CFA);
        byte[] coloredBytes = new byte[WIDTH * HEIGHT * 4];
        colored.bits().get(0, coloredBytes, 0, coloredBytes.length);
        List<String> mismatches = new ArrayList<>();
        for (double radius : new double[]{2, 6, 12})
            for (int alpha : new int[]{255, 128}) {
                byte[] shadow = render(colored, () -> effect(radius, alpha, false));
                for (int channel = 0; channel < 4; channel++) {
                    byte[] channelBytes = new byte[coloredBytes.length];
                    for (int i = 0; i < channelBytes.length; i++)
                        channelBytes[i] = coloredBytes[i / 4 * 4 + channel];
                    byte[] channelShadow = render(new QImage(channelBytes, WIDTH, HEIGHT,
                            QImage.Format.Format_ARGB32_Premultiplied),
                            () -> effect(radius, alpha, true));
                    int differing = 0;
                    for (int i = 0; i < shadow.length; i += 4)
                        if (coloredBytes[i + 3] == 0 &&
                            shadow[i + channel] != channelShadow[i + 3])
                            differing++;
                    if (differing != 0)
                        mismatches.add(String.format("radius=%s alpha=%d channel=%d: %d differ",
                                radius, alpha, channel, differing));
                }
            }
        assertTrue(mismatches.isEmpty(), String.join("\n", mismatches));
    }

    private static StackedShadowEffect effect(double radius, int alpha, boolean alphaOnly) {
        StackedShadowEffect effect = new StackedShadowEffect();
        effect.setBlurRadius(radius);
        effect.setOffset(2, 3);
        effect.setColor(new QColor(255, 255, 255, alpha));
        effect.setStackCount(1);
        effect.setAlphaOnly(alphaOnly);
        return effect;
    }

    /** The source through a scene carrying {@code effect}, as the hint mesh renders its shadows. */
    private static byte[] render(QImage source, Supplier<QGraphicsEffect> effect) {
        QGraphicsScene scene = new QGraphicsScene();
        QPixmap sourcePixmap = QPixmap.fromImage(source);
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
    private static QImage source(int... rgbs) {
        QImage image = new QImage(WIDTH, HEIGHT, QImage.Format.Format_ARGB32_Premultiplied);
        QColor transparent = new QColor(0, 0, 0, 0);
        image.fill(transparent);
        transparent.dispose();
        QPainter painter = new QPainter(image);
        painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
        for (int i = 0; i < 6; i++) {
            int rgb = rgbs[i % rgbs.length];
            painter.setPen(new QColor(rgb >> 16, rgb >> 8 & 0xFF, rgb & 0xFF, 255));
            painter.setBrush(new QBrush(new QColor(rgb >> 16, rgb >> 8 & 0xFF, rgb & 0xFF,
                    40 + i * 43)));
            painter.drawEllipse(12 + i * 32, 20 + (i % 3) * 34, 26, 22);
        }
        painter.fillRect(new QRect(0, HEIGHT - 12, WIDTH, 6),
                new QBrush(new QColor(rgbs[0] >> 16, rgbs[0] >> 8 & 0xFF, rgbs[0] & 0xFF, 255)));
        painter.end();
        painter.dispose();
        return image;
    }
}
