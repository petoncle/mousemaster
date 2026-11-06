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
import io.qt.core.QPointF;
import io.qt.core.QRect;
import io.qt.core.Qt;
import io.qt.gui.QBrush;
import io.qt.gui.QColor;
import io.qt.gui.QImage;
import io.qt.gui.QPainter;
import io.qt.gui.QPixmap;
import io.qt.gui.QTransform;
import io.qt.widgets.QGraphicsDropShadowEffect;

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

    /** Colors the image where it is opaque, with the brush if there is one, or keeps its own colors
     *  at the color's opacity. Brushes are cached and shared, so this fills with one rather than
     *  disposing it. */
    protected static void tint(QImage image, QColor color, QBrush brush, boolean alphaOnly) {
        QPainter painter = new QPainter(image);
        painter.setCompositionMode(alphaOnly ? QPainter.CompositionMode.CompositionMode_SourceIn :
                QPainter.CompositionMode.CompositionMode_DestinationIn);
        QRect rect = image.rect();
        if (brush != null)
            painter.fillRect(rect, brush);
        else
            painter.fillRect(rect, color);
        rect.dispose();
        painter.end();
        painter.dispose();
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
        QImage shadow = blurredShadow(sourcePixmap, xOffset(), yOffset(), blurRadius(), alphaOnly);
        QColor shadowColor = color();
        tint(shadow, shadowColor, shadowBrush, alphaOnly);
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

    /** The source shifted by the offset and blurred, in an image of its size, to be colored. */
    protected static QImage blurredShadow(QPixmap sourcePixmap, double xOffset, double yOffset,
                                          double blurRadius, boolean alphaOnly) {
        int width = sourcePixmap.width(), height = sourcePixmap.height();
        QImage shifted = transparentImage(width, height);
        QPainter shiftPainter = new QPainter(shifted);
        shiftPainter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source);
        QPointF shadowOffset = new QPointF(xOffset, yOffset);
        shiftPainter.drawPixmap(shadowOffset, sourcePixmap);
        shadowOffset.dispose();
        shiftPainter.end();
        shiftPainter.dispose();

        byte[] pixels = new byte[width * height * 4];
        shifted.bits().get(0, pixels, 0, pixels.length);
        shifted.dispose();
        double radius = blurRadius;
        double scale = 1;
        int blurWidth = width, blurHeight = height;
        // qt_blurImage halves the image once the radius reaches 4, and scales it back afterwards.
        boolean halfScaled = radius >= 4 && width >= 2 && height >= 2;
        if (halfScaled) {
            blurWidth = width / 2;
            blurHeight = height / 2;
            scale = 2;
            radius *= 0.5;
        }
        byte[] blurredPixels = new byte[blurWidth * blurHeight * 4];
        for (int channel = alphaOnly ? 3 : 0; channel < 4; channel++) {
            byte[] plane = ExpBlur.plane(pixels, width, height, channel);
            if (halfScaled)
                plane = ExpBlur.halfScaledPlane(plane, width, height);
            ExpBlur.blurPlane(plane, blurWidth, blurHeight, radius);
            ExpBlur.setPlane(blurredPixels, plane, blurWidth, blurHeight, channel);
        }

        QImage blurredImage = new QImage(blurredPixels, blurWidth, blurHeight,
                QImage.Format.Format_ARGB32_Premultiplied);
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
        return shadow;
    }

    private static QImage transparentImage(int width, int height) {
        QImage image = new QImage(width, height, QImage.Format.Format_ARGB32_Premultiplied);
        QColor transparent = new QColor(0, 0, 0, 0);
        image.fill(transparent);
        transparent.dispose();
        return image;
    }
}
