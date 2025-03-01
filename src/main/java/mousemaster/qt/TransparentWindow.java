package mousemaster.qt;

import io.qt.core.QObject;
import io.qt.core.Qt;
import io.qt.gui.QPaintEvent;
import io.qt.gui.QPainter;
import io.qt.widgets.QWidget;

public class TransparentWindow extends QWidget {

    public TransparentWindow() {
        // WindowDoesNotAcceptFocus is not implemented for Windows.
        setWindowFlags(Qt.WindowType.FramelessWindowHint);
        setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground);
    }

    @Override
    protected void paintEvent(QPaintEvent event) {
//        QPainter painter = new QPainter(this);

        // Draw a semi-transparent black rectangle
//        painter.setRenderHint(QPainter.RenderHint.Antialiasing);
//        backgroundColor = new QColor(0, 0, 0, 0);
//        painter.setBrush(backgroundColor); // Black with 30% opacity (76 = 255 * 0.3)
//        painter.setPen(Qt.PenStyle.NoPen);
//        painter.drawRect(0, 0, width(), height());
//        painter.end();
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
                widget.disposeLater();
            }
        }
    }

}
