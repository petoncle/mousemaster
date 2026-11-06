package mousemaster.qt;

import io.qt.core.QPoint;
import io.qt.core.Qt;
import io.qt.gui.QBrush;
import io.qt.gui.QColor;
import io.qt.gui.QImage;
import io.qt.gui.QPainter;
import io.qt.gui.QPixmap;
import io.qt.gui.QTransform;
import mousemaster.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * A drop-shadow effect that supports stacking (compositing the shadow on itself for a
 * stronger effect) and lets subclasses redraw the source over the shadow. The shadow
 * baking helpers are shared by the indicator and the hint labels.
 */
public class StackedShadowEffect extends QtDropShadowEffect {

    private static final Logger logger = LoggerFactory.getLogger(StackedShadowEffect.class);

    private static final long SLOW_DRAW_MS = 5;

    private int stackCount;
    private boolean transparencyOnly;

    public void setStackCount(int stackCount) {
        this.stackCount = stackCount;
    }

    public void setTransparencyOnly(boolean transparencyOnly) {
        this.transparencyOnly = transparencyOnly;
    }

    protected boolean transparencyOnly() {
        return transparencyOnly;
    }

    @Override
    protected void draw(QPainter painter) {
        long before = System.nanoTime();
        drawShadow(painter);
        long durationMillis = (System.nanoTime() - before) / 1000000;
        if (durationMillis >= SLOW_DRAW_MS)
            logger.debug("Blurred a shadow in " + durationMillis + "ms");
    }

    protected void drawShadow(QPainter painter) {
        if (transparencyOnly) {
            redrawSourceOverShadow(painter);
            return;
        }
        if (stackCount <= 1) {
            drawBlurredShadow(painter);
            redrawSourceOverShadow(painter);
            return;
        }
        QPoint sourceOffset = new QPoint();
        QPixmap sourcePixmap = sourcePixmap(
                Qt.CoordinateSystem.DeviceCoordinates, sourceOffset,
                PixmapPadMode.PadToEffectiveBoundingRect);
        QTransform savedTransform = painter.worldTransform();
        QTransform identity = new QTransform();
        painter.setWorldTransform(identity);
        QColor shadowColor = color();
        drawShadow(painter, sourcePixmap, sourceOffset, blurRadius(), xOffset(), yOffset(),
                shadowColor, shadowBrush(), stackCount, alphaOnly());
        shadowColor.dispose();
        painter.setWorldTransform(savedTransform);
        savedTransform.dispose();
        identity.dispose();
        sourcePixmap.dispose();
        sourceOffset.dispose();
        drawSource(painter);
        redrawSourceOverShadow(painter);
    }

    /** Draws the shadow of the source where the source goes, stackCount times, the painter's
     *  transform reset. */
    protected static void drawShadow(QPainter painter, QPixmap sourcePixmap, QPoint sourceOffset,
                                     double blurRadius, double xOffset, double yOffset,
                                     QColor color, QBrush brush, int stackCount, boolean alphaOnly) {
        QImage sourceImage = sourcePixmap.toImage();
        QImage shadow = shadows(List.of(sourceImage), sourceImage.height(), xOffset, yOffset,
                blurRadius, color, brush, alphaOnly, false).getFirst();
        for (int stack = 0; stack < stackCount; stack++)
            painter.drawImage(sourceOffset, shadow);
        shadow.dispose();
    }

    protected void redrawSourceOverShadow(QPainter painter) {
        // No-op by default. Subclasses override to clear and redraw
        // source content, preventing shadow from showing through
        // transparent parts.
    }

    /**
     * Renders the shadow of what chunkPainter paints in the area, as the effect draws it under the
     * source, in chunks of rows from the top, with the source cut out when cutOut. The chunks of
     * the source are made on the blur's threads, at dotsPerMeter, and chunkPainter paints each of
     * them, given the part of the area it covers.
     */
    public static List<QImage> renderShadow(Rectangle area, int dotsPerMeter,
                                            BiConsumer<QPainter, Rectangle> chunkPainter,
                                            QColor shadowColor, double blurRadius,
                                            double horizontalOffset, double verticalOffset,
                                            boolean alphaOnly, boolean cutOut) {
        int chunkHeight = ExpBlur.chunkHeight(area.width(), area.height());
        QImage[] sourceImages = new QImage[(area.height() + chunkHeight - 1) / chunkHeight];
        ExpBlur.overRange(sourceImages.length, area.width() * chunkHeight, (from, to) -> {
            for (int chunk = from; chunk < to; chunk++) {
                QImage sourceImage = new QImage(area.width(),
                        Math.min(chunkHeight, area.height() - chunk * chunkHeight),
                        QImage.Format.Format_ARGB32_Premultiplied);
                sourceImage.setDotsPerMeterX(dotsPerMeter);
                sourceImage.setDotsPerMeterY(dotsPerMeter);
                QColor fillColor = new QColor(0, 0, 0, 0);
                sourceImage.fill(fillColor);
                fillColor.dispose();
                sourceImages[chunk] = sourceImage;
            }
        });
        // Painting goes through caches that only this thread uses.
        for (int chunk = 0; chunk < sourceImages.length; chunk++) {
            Rectangle chunkArea = new Rectangle(area.x(), area.y() + chunk * chunkHeight,
                    area.width(), sourceImages[chunk].height());
            QPainter painter = new QPainter(sourceImages[chunk]);
            painter.translate(-chunkArea.x(), -chunkArea.y());
            chunkPainter.accept(painter, chunkArea);
            painter.end();
            painter.dispose();
        }
        return shadows(List.of(sourceImages), chunkHeight, horizontalOffset, verticalOffset,
                blurRadius, shadowColor, null, alphaOnly, cutOut);
    }
}
