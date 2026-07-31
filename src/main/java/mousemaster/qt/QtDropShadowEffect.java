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
import io.qt.gui.QColor;
import io.qt.gui.QImage;
import io.qt.gui.QPainter;
import io.qt.gui.QPixmap;
import io.qt.gui.QTransform;
import io.qt.widgets.QGraphicsDropShadowEffect;

/**
 * Qt's drop shadow with its blur swapped for {@link ExpBlur}. Qt still pads the source, upscales
 * the halved blur, colours the shadow and composites it, so only the blur itself has to be kept
 * bit-exact.
 */
public class QtDropShadowEffect extends QGraphicsDropShadowEffect {

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
}
