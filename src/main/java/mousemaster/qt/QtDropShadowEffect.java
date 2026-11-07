// Copyright (C) 2016 The Qt Company Ltd.
// SPDX-License-Identifier: LGPL-3.0-only
//
// A Java translation, made in 2026, of QGraphicsDropShadowEffect::draw, from
// qtbase/src/widgets/effects/qgraphicseffect.cpp, and QPixmapDropShadowFilter::draw, from
// qtbase/src/widgets/effects/qpixmapfilter.cpp, both at Qt 6.8.2, with the call to qt_blurImage
// replaced by ExpBlur. Upstream is available under LicenseRef-Qt-Commercial OR LGPL-3.0-only OR
// GPL-2.0-only OR GPL-3.0-only; this translation elects LGPL-3.0-only. See THIRD-PARTY-NOTICES.md.
package mousemaster.qt;

import io.qt.core.QPoint;
import io.qt.core.QRect;
import io.qt.core.Qt;
import io.qt.gui.QBrush;
import io.qt.gui.QColor;
import io.qt.gui.QImage;
import io.qt.gui.QPainter;
import io.qt.gui.QPixmap;
import io.qt.gui.QTransform;
import io.qt.widgets.QGraphicsDropShadowEffect;

import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Qt's drop shadow with its blur swapped for {@link ExpBlur}. Qt still pads the source, upscales
 * the halved blur, colors the shadow and composites it, so only the blur itself has to be kept
 * bit-exact.
 */
public class QtDropShadowEffect extends QGraphicsDropShadowEffect {

    private QBrush shadowBrush;
    private boolean alphaOnly = true;

    /** Tints the shadow with a brush instead of {@link #color()}, so it can be a gradient. */
    public void setShadowBrush(QBrush shadowBrush) {
        this.shadowBrush = shadowBrush;
    }

    protected QBrush shadowBrush() {
        return shadowBrush;
    }

    /** false blurs the source's colors too: {@link #color()} then gives only the opacity. */
    public void setAlphaOnly(boolean alphaOnly) {
        this.alphaOnly = alphaOnly;
    }

    protected boolean alphaOnly() {
        return alphaOnly;
    }

    protected void drawBlurredShadow(QPainter painter) {
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
        QImage sourceImage = sourcePixmap.toImage();
        QColor shadowColor = color();
        QImage shadow = shadows(List.of(sourceImage), sourceImage.height(), xOffset(), yOffset(),
                blurRadius(), shadowColor, shadowBrush, alphaOnly, false).getFirst();
        shadowColor.dispose();

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

    /**
     * The shadow of the source, colored with the brush if there is one, or else with the color, or
     * in the source's own colors at the color's opacity when not alphaOnly, and with the source cut
     * out of it when cutOut. The source comes in chunks of chunkHeight rows, the last one shorter,
     * and so does the shadow, each chunk scaled back up from the blur and colored on a thread of its
     * own. Consumes the sources. Brushes are cached and shared, so this fills with one rather than
     * disposing it.
     */
    protected static List<QImage> shadows(List<QImage> sources, int chunkHeight, double xOffset,
                                          double yOffset, double blurRadius, QColor color,
                                          QBrush brush, boolean alphaOnly, boolean cutOut) {
        int width = sources.getFirst().width();
        int height = (sources.size() - 1) * chunkHeight + sources.getLast().height();
        // qt_blurImage halves the image once the radius reaches 4, and scales it back afterwards.
        int scale = blurRadius >= 4 && width >= 2 && height >= 2 ? 2 : 1;
        List<IntBuffer> chunks = new ArrayList<>();
        for (QImage source : sources)
            chunks.add(source.bits().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer());
        // Qt moves the source with a blit, which rounds the offset to whole pixels.
        QImage blurredImage = new QImage(ExpBlur.blur(chunks, chunkHeight, width, height,
                (int) Math.round(xOffset), (int) Math.round(yOffset), scale, blurRadius / scale,
                alphaOnly), width / scale, height / scale, alphaOnly ? QImage.Format.Format_Alpha8 :
                QImage.Format.Format_ARGB32_Premultiplied);
        QImage[] shadows = new QImage[sources.size()];
        ImageChunks.overRange(shadows.length, width * chunkHeight, (from, to) -> {
            for (int chunk = from; chunk < to; chunk++) {
                QImage source = sources.get(chunk);
                shadows[chunk] = shadow(source, chunk * chunkHeight, blurredImage, scale, width,
                        height, color, brush, alphaOnly, cutOut);
                source.dispose();
            }
        });
        blurredImage.dispose();
        return List.of(shadows);
    }

    /** The chunk of the shadow under the chunk of the source that starts at row top. */
    private static QImage shadow(QImage source, int top, QImage blurredImage, int scale,
                                 int width, int height, QColor color, QBrush brush,
                                 boolean alphaOnly, boolean cutOut) {
        int scaledWidth = scale * blurredImage.width();
        int scaledHeight = scale * blurredImage.height();
        QImage shadow = new QImage(width, source.height(),
                QImage.Format.Format_ARGB32_Premultiplied);
        QPainter painter = new QPainter(shadow);
        painter.translate(0, -top);
        painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source);
        // The halved image covers all but an odd last column or row.
        QColor transparent = new QColor(0, 0, 0, 0);
        painter.fillRect(scaledWidth, 0, width - scaledWidth, height, transparent);
        painter.fillRect(0, scaledHeight, width, height - scaledHeight, transparent);
        transparent.dispose();
        painter.save();
        painter.scale(scale, scale);
        painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform);
        painter.drawImage(0, 0, blurredImage);
        painter.restore();
        painter.setCompositionMode(alphaOnly ? QPainter.CompositionMode.CompositionMode_SourceIn :
                QPainter.CompositionMode.CompositionMode_DestinationIn);
        QRect rect = new QRect(0, top, width, source.height());
        if (brush != null)
            painter.fillRect(rect, brush);
        else
            painter.fillRect(rect, color);
        rect.dispose();
        if (cutOut) {
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_DestinationOut);
            painter.drawImage(0, top, source);
        }
        painter.end();
        painter.dispose();
        return shadow;
    }
}
