package mousemaster.qt;

import io.qt.core.*;
import io.qt.gui.*;
import mousemaster.Rectangle;
import mousemaster.Zoom;

/**
 * Renders a captured screenshot magnified around a zoom center, standing in for the
 * Win32 Magnification API used on Windows (no equivalent live/optical magnifier exists
 * on Linux). Used for both the animated zoom transition and the resulting static zoom.
 */
public class ZoomWindow extends TransparentWindow {

    private QPixmap screenshot;
    private Rectangle screenRect;
    private Zoom zoom;

    public ZoomWindow() {
        super();
    }

    public void setScreenshot(QPixmap screenshot, Rectangle screenRect) {
        if (this.screenshot != null)
            this.screenshot.dispose();
        this.screenshot = screenshot;
        this.screenRect = screenRect;
        setGeometry(screenRect.x(), screenRect.y(), screenRect.width(),
                screenRect.height());
    }

    public void setZoom(Zoom zoom) {
        this.zoom = zoom;
    }

    public void clear() {
        if (screenshot != null) {
            screenshot.dispose();
            screenshot = null;
        }
        zoom = null;
        hide();
    }

    @Override
    protected void paintEvent(QPaintEvent event) {
        if (screenshot == null || zoom == null)
            return;

        QPainter painter = new QPainter(this);
        painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform, true);
        painter.fillRect(0, 0, width(), height(), QColor.fromRgb(0, 0, 0));

        double localCenterX = zoom.center().x() - screenRect.x();
        double localCenterY = zoom.center().y() - screenRect.y();
        double sourceWidth = screenRect.width() / zoom.percent();
        double sourceHeight = screenRect.height() / zoom.percent();
        double sourceX = localCenterX - sourceWidth / 2;
        double sourceY = localCenterY - sourceHeight / 2;

        // Near a screen edge/corner, the ideal source rect can extend past the
        // captured pixmap's bounds. Clamp it and shrink the target rect by the same
        // scale so the valid pixels still land in the right place, and the region
        // with no captured pixels stays as the black fill above instead of leaving
        // a gap where drawPixmap would otherwise paint nothing (transparent, letting
        // the real desktop show through).
        double scale = zoom.percent();
        double clampedSourceX = Math.max(sourceX, 0);
        double clampedSourceY = Math.max(sourceY, 0);
        double clampedSourceRight = Math.min(sourceX + sourceWidth, screenshot.width());
        double clampedSourceBottom =
                Math.min(sourceY + sourceHeight, screenshot.height());
        double clampedSourceWidth = Math.max(0, clampedSourceRight - clampedSourceX);
        double clampedSourceHeight = Math.max(0, clampedSourceBottom - clampedSourceY);

        if (clampedSourceWidth > 0 && clampedSourceHeight > 0) {
            double targetX = (clampedSourceX - sourceX) * scale;
            double targetY = (clampedSourceY - sourceY) * scale;
            QRectF sourceRect = new QRectF(clampedSourceX, clampedSourceY,
                    clampedSourceWidth, clampedSourceHeight);
            QRectF targetRect = new QRectF(targetX, targetY,
                    clampedSourceWidth * scale, clampedSourceHeight * scale);
            painter.drawPixmap(targetRect, screenshot, sourceRect);
        }

        painter.end();
        painter.dispose();
    }

}
