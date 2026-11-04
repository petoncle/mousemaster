package mousemaster.platform.macos;

import io.qt.core.QRect;
import io.qt.gui.QColor;
import io.qt.gui.QPaintEvent;
import io.qt.gui.QPainter;
import mousemaster.Rectangle;
import mousemaster.Screen;
import mousemaster.qt.TransparentWindow;
import mousemaster.renderer.IndicatorRenderer;
import mousemaster.renderer.IndicatorRenderer.WindowImage;

import java.util.List;

/**
 * The indicator window on macOS: it covers the active screen and draws images over each other.
 * It is shown once and never hidden: a window that shows reaches the screen a frame after the
 * others are updated.
 */
final class MacosIndicatorWindow extends TransparentWindow {

    private Screen screen;
    private List<WindowImage> windowImages = List.of();

    /** The images, in z order. */
    void show(List<WindowImage> windowImages, Screen activeScreen) {
        Rectangle dirty;
        if (activeScreen.equals(screen))
            dirty = IndicatorRenderer.changedRectangle(windowImages, this.windowImages)
                                     .intersection(screen.rectangle());
        else {
            screen = activeScreen;
            coverInPixels(screen);
            dirty = screen.rectangle();
        }
        this.windowImages = windowImages;
        if (!isVisible())
            show();
        if (dirty.isEmpty())
            return;
        Rectangle screenRectangle = screen.rectangle();
        double scale = screen.scale();
        int left = (int) Math.floor((dirty.x() - screenRectangle.x()) / scale);
        int top = (int) Math.floor((dirty.y() - screenRectangle.y()) / scale);
        int right = (int) Math.ceil((dirty.x() + dirty.width() - screenRectangle.x()) / scale);
        int bottom = (int) Math.ceil((dirty.y() + dirty.height() - screenRectangle.y()) / scale);
        update(left, top, right - left, bottom - top);
    }

    @Override
    protected void paintEvent(QPaintEvent event) {
        QPainter painter = new QPainter(this);
        QRect area = event.rect();
        QColor transparent = new QColor(0, 0, 0, 0);
        painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source);
        painter.fillRect(area, transparent);
        area.dispose();
        transparent.dispose();
        painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
        Rectangle screenRectangle = screen.rectangle();
        painter.scale(1 / screen.scale(), 1 / screen.scale());
        for (WindowImage windowImage : windowImages)
            painter.drawImage(windowImage.x() - screenRectangle.x(),
                    windowImage.y() - screenRectangle.y(), windowImage.image());
        painter.end();
        painter.dispose();
    }

}
