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
        drawShadow(painter, sourcePixmap.toImage(), sourceOffset.x(), sourceOffset.y(),
                blurRadius(), xOffset(), yOffset(), shadowColor, shadowBrush(), stackCount,
                alphaOnly());
        shadowColor.dispose();
        painter.setWorldTransform(savedTransform);
        savedTransform.dispose();
        identity.dispose();
        sourcePixmap.dispose();
        sourceOffset.dispose();
        drawSource(painter);
        redrawSourceOverShadow(painter);
    }

    /** Draws the shadow of the source at x, y, stackCount times. Consumes the source. */
    public static void drawShadow(QPainter painter, QImage source, int x, int y,
                                  double blurRadius, double xOffset, double yOffset,
                                  QColor color, QBrush brush, int stackCount, boolean alphaOnly) {
        QImage shadow = shadows(List.of(source), source.height(), xOffset, yOffset, blurRadius,
                color, brush, alphaOnly, false).getFirst();
        for (int stack = 0; stack < stackCount; stack++)
            painter.drawImage(x, y, shadow);
        shadow.dispose();
    }

    protected void redrawSourceOverShadow(QPainter painter) {
        // No-op by default. Subclasses override to clear and redraw
        // source content, preventing shadow from showing through
        // transparent parts.
    }

    /**
     * Renders the shadow of what paintChunk paints in the area, as the effect draws it under the
     * source, in chunks of rows from the top, with the source cut out when cutOut. The source is
     * painted by ImageChunks.paintChunks.
     */
    public static List<QImage> renderShadow(Rectangle area, int dotsPerMeter,
                                            BiConsumer<QPainter, Rectangle> paintChunk,
                                            QColor shadowColor, double blurRadius,
                                            double horizontalOffset, double verticalOffset,
                                            boolean alphaOnly, boolean cutOut) {
        return shadows(ImageChunks.paintChunks(area, 1, dotsPerMeter, paintChunk),
                ImageChunks.chunkHeight(area.width(), area.height()), horizontalOffset,
                verticalOffset, blurRadius, shadowColor, null, alphaOnly, cutOut);
    }
}
