package mousemaster.qt;

import io.qt.core.QObject;
import io.qt.core.QRect;
import io.qt.core.Qt;
import io.qt.gui.QColor;
import io.qt.gui.QPaintEvent;
import io.qt.gui.QPainter;
import io.qt.widgets.QWidget;

public class TransparentWindow extends QWidget {

    private QColor backgroundColor;
    private QRect backgroundRect;

    public TransparentWindow() {
        // WindowDoesNotAcceptFocus is not implemented for Windows.
        setWindowFlags(Qt.WindowType.FramelessWindowHint);
        setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground);
    }

    public void setBackground(QColor color, QRect rect) {
        this.backgroundColor = color;
        this.backgroundRect = rect;
    }

    @Override
    protected void paintEvent(QPaintEvent event) {
        if (backgroundColor != null) {
            QPainter painter = new QPainter(this);
            painter.fillRect(backgroundRect, backgroundColor);
            painter.end();
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
