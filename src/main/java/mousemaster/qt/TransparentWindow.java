package mousemaster.qt;

import io.qt.core.QObject;
import io.qt.core.QRect;
import io.qt.core.Qt;
import io.qt.gui.QColor;
import io.qt.gui.QPaintEvent;
import io.qt.gui.QPainter;
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
        pointsPerPixel = Os.macos ? screen.scale() : 1;
        Rectangle rectangle = screen.rectangle();
        move(toPoints(rectangle.x()), toPoints(rectangle.y()));
        resize(toPoints(rectangle.width()), toPoints(rectangle.height()));
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
