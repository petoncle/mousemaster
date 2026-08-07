package mousemaster.qt;

import io.qt.core.QObject;
import io.qt.core.QRect;
import io.qt.core.Qt;
import io.qt.gui.QColor;
import io.qt.gui.QPaintEvent;
import io.qt.gui.QPainter;
import io.qt.gui.QGuiApplication;
import io.qt.gui.QScreen;
import io.qt.widgets.QWidget;
import mousemaster.Os;
import mousemaster.Rectangle;
import mousemaster.Screen;

public class TransparentWindow extends QWidget {

    private QColor backgroundColor;
    private QRect backgroundRect;
    private double pointsPerPixel = 1;

    public TransparentWindow() {
        // WindowDoesNotAcceptFocus is not implemented for Windows.
        setWindowFlags(Qt.WindowType.FramelessWindowHint);
        setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground);
    }

    public void setBackground(QColor color, QRect rect) {
        if (this.backgroundRect != null)
            this.backgroundRect.dispose();
        this.backgroundColor = color;
        this.backgroundRect = rect;
    }

    /**
     * Qt positions windows in points, which are pixels except on macOS. Each screen scales by
     * its own amount, so the window is told which screen it covers.
     */
    public void coverInPixels(Screen screen) {
        Rectangle rectangle = screen.rectangle();
        moveAndResizeInPixels(screen, rectangle.x(), rectangle.y(), rectangle.width(),
                rectangle.height());
    }

    /** Sizes scale, but a position is an offset into the screen: only that offset scales. */
    public void moveAndResizeInPixels(Screen screen, int x, int y, int width, int height) {
        pointsPerPixel = Os.macos ? screen.scale() : 1;
        Rectangle rectangle = screen.rectangle();
        QRect logical = logicalGeometry(rectangle);
        move(logical.x() + toPoints(x - rectangle.x()),
                logical.y() + toPoints(y - rectangle.y()));
        resize(toPoints(width), toPoints(height));
    }

    /** Where Qt itself puts the screen, rather than a second guess at its arrangement. */
    private static QRect logicalGeometry(Rectangle screenRectangle) {
        for (QScreen qScreen : QGuiApplication.screens()) {
            QRect geometry = qScreen.geometry();
            if (Math.round(geometry.x() * qScreen.devicePixelRatio()) == screenRectangle.x())
                return geometry;
        }
        return QGuiApplication.primaryScreen().geometry();
    }

    public int xInPixels() {
        return toPixels(x());
    }

    public int yInPixels() {
        return toPixels(y());
    }

    public int widthInPixels() {
        return toPixels(width());
    }

    public int heightInPixels() {
        return toPixels(height());
    }

    private int toPoints(int pixels) {
        return (int) Math.round(pixels / pointsPerPixel);
    }

    private int toPixels(int points) {
        return (int) Math.round(points * pointsPerPixel);
    }

    @Override
    protected void paintEvent(QPaintEvent event) {
        if (backgroundColor != null) {
            QPainter painter = new QPainter(this);
            painter.fillRect(backgroundRect, backgroundColor);
            painter.end();
            painter.dispose();
        }
    }

    public void hideChildren() {
        for (QObject child : children()) {
            if (child instanceof QWidget widget) {
                widget.hide();
            }
        }

    }

    public void clearWindow() {
        for (QObject child : children()) {
            if (child instanceof QWidget widget) {
                widget.setParent(null);
            }
        }
    }

}
