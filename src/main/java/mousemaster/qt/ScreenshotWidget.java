package mousemaster.qt;

import io.qt.core.QRectF;
import io.qt.core.Qt;
import io.qt.gui.QColor;
import io.qt.gui.QPaintEvent;
import io.qt.gui.QPainter;
import io.qt.gui.QPixmap;
import io.qt.widgets.QWidget;
import mousemaster.Rectangle;
import mousemaster.Zoom;

/**
 * Displays a captured screenshot zoomed toward a centre point — the cross-platform
 * rendering used to animate a zoom transition. The platform overlay drives it; screen
 * capture, cursor drawing, magnification and window hosting are platform-specific.
 */
public class ScreenshotWidget extends QWidget {

    private QPixmap pixmap;
    private Zoom zoom;
    private Rectangle screenRect;

    public ScreenshotWidget() {
        setWindowFlags(Qt.WindowType.FramelessWindowHint);
        setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground);
    }

    public void setScreenshot(QPixmap pixmap, Rectangle screenRect) {
        this.pixmap = pixmap;
        this.screenRect = screenRect;
    }

    public void setZoom(Zoom zoom) {
        this.zoom = zoom;
    }

    @Override
    protected void paintEvent(QPaintEvent event) {
        if (pixmap == null || zoom == null)
            return;
        double zoomPercent = zoom.percent();
        double localCenterX = zoom.center().x() - screenRect.x();
        double localCenterY = zoom.center().y() - screenRect.y();
        double sourceWidth = screenRect.width() / zoomPercent;
        double sourceHeight = screenRect.height() / zoomPercent;
        double sourceX = localCenterX - sourceWidth / 2;
        double sourceY = localCenterY - sourceHeight / 2;
        QPainter painter = new QPainter(this);
        painter.fillRect(0, 0, width(), height(), new QColor(0, 0, 0));
        painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform, true);

        // Near a screen edge/corner, the ideal source rect can extend past the
        // captured pixmap's bounds. Clamp it and shrink the target rect by the same
        // scale so the valid pixels still land in the right place, and the region
        // with no captured pixels stays as the black fill above instead of leaving
        // a gap where drawPixmap would otherwise paint nothing (transparent, letting
        // whatever is behind this widget show through).
        double clampedSourceX = Math.max(sourceX, 0);
        double clampedSourceY = Math.max(sourceY, 0);
        double clampedSourceRight = Math.min(sourceX + sourceWidth, pixmap.width());
        double clampedSourceBottom = Math.min(sourceY + sourceHeight, pixmap.height());
        double clampedSourceWidth = Math.max(0, clampedSourceRight - clampedSourceX);
        double clampedSourceHeight = Math.max(0, clampedSourceBottom - clampedSourceY);

        if (clampedSourceWidth > 0 && clampedSourceHeight > 0) {
            double targetX = (clampedSourceX - sourceX) * zoomPercent;
            double targetY = (clampedSourceY - sourceY) * zoomPercent;
            QRectF sourceRect = new QRectF(clampedSourceX, clampedSourceY,
                    clampedSourceWidth, clampedSourceHeight);
            QRectF targetRect = new QRectF(targetX, targetY,
                    clampedSourceWidth * zoomPercent, clampedSourceHeight * zoomPercent);
            painter.drawPixmap(targetRect, pixmap, sourceRect);
            sourceRect.dispose();
            targetRect.dispose();
        }
        painter.end();
        painter.dispose();
    }
}
