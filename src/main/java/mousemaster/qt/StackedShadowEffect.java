package mousemaster.qt;

import io.qt.core.QPoint;
import io.qt.core.QPointF;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * A drop-shadow effect that supports stacking (compositing the shadow on itself for a
 * stronger effect) and lets subclasses redraw the source over the shadow. The shadow
 * baking helpers are shared by the indicator and the hint labels.
 */
public class StackedShadowEffect extends QGraphicsDropShadowEffect {

    private static final Logger logger = LoggerFactory.getLogger(StackedShadowEffect.class);

    private static final long SLOW_DRAW_MS = 5;

    private int stackCount;
    private boolean transparencyOnly;
    private boolean preWarming;

    public void setPreWarming(boolean preWarming) {
        this.preWarming = preWarming;
    }

    public void setStackCount(int stackCount) {
        this.stackCount = stackCount;
    }

    public void setTransparencyOnly(boolean transparencyOnly) {
        this.transparencyOnly = transparencyOnly;
    }

    @Override
    protected void draw(QPainter painter) {
        long before = System.nanoTime();
        drawShadow(painter);
        long durationMillis = (System.nanoTime() - before) / 1000000;
        if (durationMillis >= SLOW_DRAW_MS && !preWarming)
            logger.debug("Blurred a shadow in " + durationMillis + "ms");
    }

    private void drawShadow(QPainter painter) {
        if (transparencyOnly) {
            redrawSourceOverShadow(painter);
            return;
        }
        if (stackCount <= 1) {
            drawBlurredShadow(painter);
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
        sourcePixmap.dispose();
        sourceOffset.dispose();
        drawSource(painter);
        redrawSourceOverShadow(painter);
    }

    /**
     * QGraphicsDropShadowEffect::draw and QPixmapDropShadowFilter::draw, with the blur replaced by
     * the ported one. Qt still pads the source, upscales the halved blur, colours the shadow and
     * composites it, so only the blur itself has to be kept bit-exact.
     */
    private void drawBlurredShadow(QPainter painter) {
        if (blurRadius() <= 0 && xOffset() == 0 && yOffset() == 0) {
            drawSource(painter);
            return;
        }
        QPoint sourceOffset = new QPoint();
        QPixmap sourcePixmap = sourcePixmap(Qt.CoordinateSystem.DeviceCoordinates, sourceOffset,
                PixmapPadMode.PadToEffectiveBoundingRect);
        if (sourcePixmap.isNull()) {
            sourcePixmap.dispose();
            sourceOffset.dispose();
            return;
        }
        int width = sourcePixmap.width(), height = sourcePixmap.height();
        QImage shifted = transparentImage(width, height);
        QPainter shiftPainter = new QPainter(shifted);
        shiftPainter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source);
        QPointF shadowOffset = new QPointF(xOffset(), yOffset());
        shiftPainter.drawPixmap(shadowOffset, sourcePixmap);
        shadowOffset.dispose();
        shiftPainter.end();
        shiftPainter.dispose();

        byte[] pixels = new byte[width * height * 4];
        shifted.bits().get(0, pixels, 0, pixels.length);
        shifted.dispose();
        byte[] plane = ExpBlur.alphaPlane(pixels, width, height);
        double radius = blurRadius();
        double scale = 1;
        int blurWidth = width, blurHeight = height;
        // qt_blurImage halves the image once the radius reaches 4, and scales it back afterwards.
        if (radius >= 4 && width >= 2 && height >= 2) {
            plane = ExpBlur.halfScaledPlane(plane, width, height);
            blurWidth = width / 2;
            blurHeight = height / 2;
            scale = 2;
            radius *= 0.5;
        }
        ExpBlur.blurPlane(plane, blurWidth, blurHeight, radius);

        QImage blurredImage = new QImage(ExpBlur.planeAsImage(plane, blurWidth, blurHeight),
                blurWidth, blurHeight, QImage.Format.Format_ARGB32_Premultiplied);
        QImage shadow = transparentImage(width, height);
        QPainter blurPainter = new QPainter(shadow);
        blurPainter.scale(scale, scale);
        blurPainter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform);
        QRect blurredRect = new QRect(0, 0, blurWidth, blurHeight);
        blurPainter.drawImage(blurredRect, blurredImage);
        blurredRect.dispose();
        blurPainter.end();
        blurPainter.dispose();
        blurredImage.dispose();

        QPainter colorPainter = new QPainter(shadow);
        colorPainter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceIn);
        QColor shadowColor = color();
        QRect shadowRect = shadow.rect();
        colorPainter.fillRect(shadowRect, shadowColor);
        shadowRect.dispose();
        shadowColor.dispose();
        colorPainter.end();
        colorPainter.dispose();

        QTransform savedTransform = painter.worldTransform();
        QTransform identity = new QTransform();
        painter.setWorldTransform(identity);
        painter.drawImage(sourceOffset, shadow);
        painter.drawPixmap(sourceOffset, sourcePixmap);
        painter.setWorldTransform(savedTransform);
        savedTransform.dispose();
        identity.dispose();
        shadow.dispose();
        sourcePixmap.dispose();
        sourceOffset.dispose();
    }

    private static QImage transparentImage(int width, int height) {
        QImage image = new QImage(width, height, QImage.Format.Format_ARGB32_Premultiplied);
        QColor transparent = new QColor(0, 0, 0, 0);
        image.fill(transparent);
        transparent.dispose();
        return image;
    }

    protected void redrawSourceOverShadow(QPainter painter) {
        // No-op by default. Subclasses override to clear and redraw
        // source content, preventing shadow from showing through
        // transparent parts.
    }

    /**
     * Intensifies an image by computing the closed-form result of compositing
     * it on top of itself stackCount times (premultiplied alpha geometric series).
     * Consumes {@code image}: it is returned as is, or disposed and replaced by the
     * intensified one. Dispose the returned image, never the argument.
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

    /**
     * Renders the shadow {@code sourceImage} casts, with the source itself subtracted out.
     * Consumes {@code sourceImage}. Dispose the returned image, never the argument.
     */
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
        // A row of the source at a time: taking it byte by byte out of its buffer costs more than
        // the subtraction itself, there being tens of millions of them.
        byte[] sourceRow = new byte[overlapW * 4];
        for (int py = 0; py < overlapH; py++) {
            int resultRowStart = (py + srcOffY) * resultBytesPerLine + srcOffX * 4;
            sourceBuf.get(py * sourceBytesPerLine, sourceRow, 0, sourceRow.length);
            for (int i = 0; i < sourceRow.length; i++) {
                int c = shadowBytes[resultRowStart + i] & 0xFF;
                int s = sourceRow[i] & 0xFF;
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
