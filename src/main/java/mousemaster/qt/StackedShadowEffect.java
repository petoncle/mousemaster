package mousemaster.qt;

import io.qt.core.QPoint;
import io.qt.core.QRect;
import io.qt.core.QRectF;
import io.qt.core.Qt;
import io.qt.gui.QColor;
import io.qt.gui.QImage;
import io.qt.gui.QPainter;
import io.qt.gui.QPixmap;
import io.qt.gui.QTransform;
import io.qt.widgets.QGraphicsDropShadowEffect;
import io.qt.widgets.QGraphicsPixmapItem;
import io.qt.widgets.QGraphicsScene;

import java.nio.ByteBuffer;

/**
 * A drop-shadow effect that supports stacking (compositing the shadow on itself for a
 * stronger effect) and lets subclasses redraw the source over the shadow. The shadow
 * baking helpers are shared by the indicator and the hint labels.
 */
public class StackedShadowEffect extends QGraphicsDropShadowEffect {

    private int stackCount;
    private boolean transparencyOnly;

    public void setStackCount(int stackCount) {
        this.stackCount = stackCount;
    }

    public void setTransparencyOnly(boolean transparencyOnly) {
        this.transparencyOnly = transparencyOnly;
    }

    @Override
    protected void draw(QPainter painter) {
        if (transparencyOnly) {
            redrawSourceOverShadow(painter);
            return;
        }
        if (stackCount <= 1) {
            super.draw(painter);
            redrawSourceOverShadow(painter);
            return;
        }
        // Pre-render the shadow separately, bake stacking, then draw
        // the stacked shadow and source independently.
        QPoint sourceOffset = new QPoint();
        QPixmap sourcePixmap = sourcePixmap(
                Qt.CoordinateSystem.DeviceCoordinates, sourceOffset,
                PixmapPadMode.PadToEffectiveBoundingRect);
        QImage sourceImage = sourcePixmap.toImage();
        int w = sourceImage.width();
        int h = sourceImage.height();
        QColor shadowColor = color();
        ShadowImage shadow = renderShadowOnly(sourceImage, shadowColor,
                blurRadius(), xOffset(), yOffset(), w, h);
        shadowColor.dispose();
        QImage stackedShadow = bakeStacking(shadow.image(), stackCount);
        QTransform savedTransform = painter.worldTransform();
        QTransform identity = new QTransform();
        painter.setWorldTransform(identity);
        painter.drawImage(sourceOffset.x() + shadow.x(),
                sourceOffset.y() + shadow.y(), stackedShadow);
        stackedShadow.dispose();
        painter.setWorldTransform(savedTransform);
        savedTransform.dispose();
        identity.dispose();
        sourceImage.dispose();
        sourcePixmap.dispose();
        sourceOffset.dispose();
        drawSource(painter);
        redrawSourceOverShadow(painter);
    }

    protected void redrawSourceOverShadow(QPainter painter) {
        // No-op by default. Subclasses override to clear and redraw
        // source content, preventing shadow from showing through
        // transparent parts.
    }

    /**
     * Intensifies an image by computing the closed-form result of compositing
     * it on top of itself stackCount times (premultiplied alpha geometric series).
     * Returns a new QImage (caller must dispose the original if different).
     */
    public static QImage bakeStacking(QImage image, int stackCount) {
        if (stackCount <= 1)
            return image;
        int w = image.width();
        int h = image.height();
        int totalBytes = w * h * 4;
        ByteBuffer buf = image.bits();
        byte[] pixels = new byte[totalBytes];
        buf.position(0);
        buf.get(pixels);
        // Precompute multiplier for each possible alpha value.
        // For premultiplied alpha, stacking N times multiplies all channels by
        // (1 - t^N) / (1 - t) where t = 1 - a/255.
        double[] multiplier = new double[256];
        for (int a = 1; a <= 255; a++) {
            double t = 1.0 - a / 255.0;
            multiplier[a] = (1.0 - Math.pow(t, stackCount)) / (a / 255.0);
        }
        // ARGB32_Premultiplied, little-endian: B, G, R, A.
        for (int i = 0; i < totalBytes; i += 4) {
            int a = pixels[i + 3] & 0xFF;
            if (a == 0) continue;
            double m = multiplier[a];
            pixels[i]     = (byte) Math.min(255, (int) (((pixels[i]     & 0xFF) * m) + 0.5));
            pixels[i + 1] = (byte) Math.min(255, (int) (((pixels[i + 1] & 0xFF) * m) + 0.5));
            pixels[i + 2] = (byte) Math.min(255, (int) (((pixels[i + 2] & 0xFF) * m) + 0.5));
            pixels[i + 3] = (byte) Math.min(255, (int) ((a * m) + 0.5));
        }
        image.dispose();
        return new QImage(pixels, w, h, QImage.Format.Format_ARGB32_Premultiplied);
    }

    public static ShadowImage renderShadowOnly(
            QImage sourceImage, QColor shadowColor, double blurRadius,
            double horizontalOffset, double verticalOffset,
            int containerWidth, int containerHeight) {
        QGraphicsScene scene = new QGraphicsScene();
        QPixmap sourcePixmap = QPixmap.fromImage(sourceImage);
        QGraphicsPixmapItem item = scene.addPixmap(sourcePixmap);
        StackedShadowEffect effect = new StackedShadowEffect();
        effect.setBlurRadius(blurRadius);
        effect.setOffset(horizontalOffset, verticalOffset);
        effect.setColor(shadowColor);
        effect.setStackCount(1);
        item.setGraphicsEffect(effect);
        QRectF bounds = scene.itemsBoundingRect();
        int boundsX = (int) Math.floor(bounds.x());
        int boundsY = (int) Math.floor(bounds.y());
        int boundsW = (int) Math.ceil(bounds.x() + bounds.width()) - boundsX;
        int boundsH = (int) Math.ceil(bounds.y() + bounds.height()) - boundsY;
        bounds.dispose();
        QRectF intBounds = new QRectF(boundsX, boundsY, boundsW, boundsH);
        QImage resultImage = new QImage(boundsW, boundsH,
                QImage.Format.Format_ARGB32_Premultiplied);
        QColor fillColor = new QColor(0, 0, 0, 0);
        resultImage.fill(fillColor);
        fillColor.dispose();
        QPainter resultPainter = new QPainter(resultImage);
        QRect resultRect = resultImage.rect();
        QRectF targetRect = new QRectF(resultRect);
        scene.render(resultPainter, targetRect, intBounds);
        resultRect.dispose();
        targetRect.dispose();
        intBounds.dispose();
        resultPainter.end();
        resultPainter.dispose();
        scene.dispose();
        sourcePixmap.dispose();
        ByteBuffer combinedBuf = resultImage.bits();
        ByteBuffer sourceBuf = sourceImage.bits();
        int resultBytesPerLine = boundsW * 4;
        int sourceBytesPerLine = containerWidth * 4;
        int totalBytes = resultBytesPerLine * boundsH;
        byte[] shadowBytes = new byte[totalBytes];
        combinedBuf.get(0, shadowBytes, 0, totalBytes);
        int srcOffX = -boundsX;
        int srcOffY = -boundsY;
        int overlapW = Math.min(containerWidth, boundsW - srcOffX);
        int overlapH = Math.min(containerHeight, boundsH - srcOffY);
        for (int py = 0; py < overlapH; py++) {
            int resultRowStart = (py + srcOffY) * resultBytesPerLine + srcOffX * 4;
            int sourceRowStart = py * sourceBytesPerLine;
            for (int i = 0; i < overlapW * 4; i++) {
                int c = shadowBytes[resultRowStart + i] & 0xFF;
                int s = sourceBuf.get(sourceRowStart + i) & 0xFF;
                shadowBytes[resultRowStart + i] = (byte) Math.max(0, c - s);
            }
        }
        resultImage.dispose();
        sourceImage.dispose();
        QImage shadowImage = new QImage(shadowBytes, boundsW, boundsH,
                QImage.Format.Format_ARGB32_Premultiplied);
        return new ShadowImage(shadowImage, boundsX, boundsY);
    }

    public record ShadowImage(QImage image, int x, int y) {
    }
}
