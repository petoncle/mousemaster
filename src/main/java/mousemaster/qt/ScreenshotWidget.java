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
        QRectF sourceRect = new QRectF(sourceX, sourceY, sourceWidth, sourceHeight);
        QRectF targetRect = new QRectF(0, 0, width(), height());
        painter.drawPixmap(targetRect, pixmap, sourceRect);
        sourceRect.dispose();
        targetRect.dispose();
        painter.end();
        painter.dispose();
    }
}
