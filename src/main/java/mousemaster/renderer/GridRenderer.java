package mousemaster.renderer;

import mousemaster.qt.*;

import io.qt.core.QEasingCurve;
import io.qt.core.QMetaObject;
import io.qt.core.QRect;
import io.qt.core.QVariantAnimation;
import io.qt.core.Qt;
import io.qt.gui.QColor;
import io.qt.gui.QPaintEvent;
import io.qt.gui.QPainter;
import io.qt.gui.QPen;
import io.qt.widgets.QWidget;
import mousemaster.Grid;
import mousemaster.Rectangle;

import java.time.Duration;

public final class GridRenderer {

    private final GridWidget widget = new GridWidget();
    private FadeAnimator fadeAnimator;
    private boolean showing;
    private Grid currentGrid;

    public QWidget widget() {
        return widget;
    }

    public boolean showing() {
        return showing;
    }

    public void setGrid(Grid grid, Rectangle desktopBounds, int lineThicknessPixels) {
        boolean fadingOut = fadeAnimator != null && fadeAnimator.isFadingOut();
        if (!fadingOut && showing && currentGrid != null && currentGrid.equals(grid))
            return;
        // While the old grid is fading out, treat this as a fresh appearance: the new
        // grid fades in rather than morphing from the one that was disappearing.
        boolean wasShowing = showing && !fadingOut;
        if (fadingOut)
            fadeAnimator.cancel();
        currentGrid = grid;
        if (!widget.covers(grid))
            widget.coverVirtualDesktop(desktopBounds);
        widget.show();
        showing = true;
        QColor background = grid.backgroundOpacity() > 0 ?
                QtColorUtil.qColor(grid.backgroundHexColor(), grid.backgroundOpacity()) : null;
        widget.showGrid(grid, lineThicknessPixels,
                QtColorUtil.qColor(grid.lineHexColor(), grid.lineOpacity()), background,
                grid.transitionAnimationEnabled() && wasShowing,
                grid.transitionAnimationDuration());
        if (!wasShowing) {
            fadeAnimator = new FadeAnimator(
                    widget::setWindowOpacity,
                    this::doHide,
                    grid.fadeAnimationEnabled(),
                    grid.fadeAnimationDuration());
            if (fadeAnimator.isEnabled()) {
                widget.setWindowOpacity(0.0);
                fadeAnimator.startFadeIn();
            }
        }
    }

    public void hide() {
        if (!showing)
            return;
        if (fadeAnimator != null && fadeAnimator.shouldDeferHide())
            return;
        doHide();
    }

    private void doHide() {
        showing = false;
        if (fadeAnimator != null)
            fadeAnimator.cancel();
        widget.hideGrid();
        widget.setWindowOpacity(1.0);
    }

    private static final class GridWidget extends QWidget {
        private int originX, originY, coveredWidth, coveredHeight;
        private Grid grid;
        private int lineThickness;
        private QColor lineColor;
        private QColor backgroundColor; // Grid fill, or null when the background is off.
        private boolean visible;
        private int drawX, drawY, drawW, drawH; // Current (possibly animated) grid rectangle.
        private QRect paintedRect = new QRect(); // Last drawn grid bounds; cleared on the next paint.
        private QVariantAnimation animation;
        private QMetaObject.Slot1<Object> animationChanged; // Kept referenced so Qt does not GC it.

        GridWidget() {
            setWindowFlags(Qt.WindowType.FramelessWindowHint);
            setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground);
        }

        void coverVirtualDesktop(Rectangle bounds) {
            if (bounds.x() == originX && bounds.y() == originY &&
                bounds.width() == coveredWidth && bounds.height() == coveredHeight)
                return;
            originX = bounds.x();
            originY = bounds.y();
            coveredWidth = bounds.width();
            coveredHeight = bounds.height();
            move(bounds.x(), bounds.y());
            resize(bounds.width(), bounds.height());
            // The resize reallocates (clears) the surface, so nothing is painted anymore.
            paintedRect.dispose();
            paintedRect = new QRect();
        }

        boolean covers(Grid grid) {
            return grid.x() >= originX && grid.y() >= originY &&
                   grid.x() + grid.width() <= originX + coveredWidth &&
                   grid.y() + grid.height() <= originY + coveredHeight;
        }

        void showGrid(Grid grid, int lineThickness, QColor lineColor, QColor backgroundColor,
                      boolean animate, Duration animationDuration) {
            stopAnimation();
            if (this.lineColor != null)
                this.lineColor.dispose();
            if (this.backgroundColor != null)
                this.backgroundColor.dispose();
            boolean wasVisible = visible;
            int oldX = drawX, oldY = drawY, oldW = drawW, oldH = drawH;
            this.grid = grid;
            this.lineThickness = lineThickness;
            this.lineColor = lineColor;
            this.backgroundColor = backgroundColor;
            this.visible = true;
            int newX = grid.x(), newY = grid.y(), newW = grid.width(), newH = grid.height();
            boolean geometryChanged =
                    newX != oldX || newY != oldY || newW != oldW || newH != oldH;
            if (animate && wasVisible && geometryChanged &&
                animationDuration != null && !animationDuration.isZero())
                animateDrawRect(oldX, oldY, oldW, oldH, newX, newY, newW, newH,
                        animationDuration);
            else
                setDrawRect(newX, newY, newW, newH);
        }

        void hideGrid() {
            stopAnimation();
            visible = false;
            repaintGrid(new QRect());
        }

        // Eases the drawn rectangle from the old grid geometry to the new one.
        private void animateDrawRect(int oldX, int oldY, int oldW, int oldH,
                                     int newX, int newY, int newW, int newH,
                                     Duration duration) {
            QRect begin = new QRect(oldX, oldY, oldW, oldH);
            QRect end = new QRect(newX, newY, newW, newH);
            animation = new QVariantAnimation();
            animation.setDuration((int) duration.toMillis());
            animation.setStartValue(begin);
            animation.setEndValue(end);
            animation.setEasingCurve(QEasingCurve.Type.InOutQuad);
            animationChanged = value -> {
                QRect r = (QRect) value;
                setDrawRect(r.x(), r.y(), r.width(), r.height());
            };
            animation.valueChanged.connect(animationChanged);
            animation.start();
            begin.dispose();
            end.dispose();
        }

        private void stopAnimation() {
            if (animation == null)
                return;
            animation.stop();
            animation.dispose();
            animation = null;
            animationChanged = null;
        }

        private void setDrawRect(int x, int y, int w, int h) {
            drawX = x;
            drawY = y;
            drawW = w;
            drawH = h;
            repaintGrid(new QRect(x - originX, y - originY,
                    w + lineThickness, h + lineThickness));
        }

        // Repaints (clearing then redrawing) the union of the old and new grid bounds.
        private void repaintGrid(QRect newRect) {
            QRect dirty = newRect.united(paintedRect);
            paintedRect.dispose();
            paintedRect = newRect;
            repaint(dirty);
            dirty.dispose();
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            QPainter painter = new QPainter(this);
            // Clear only the repainted region (the grid always fits inside it), leaving
            // the rest of the surface untouched so the old grid persists until redrawn.
            QColor transparent = new QColor(0, 0, 0, 0);
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Clear);
            painter.fillRect(event.rect(), transparent);
            transparent.dispose();
            if (visible && grid != null)
                drawGrid(painter);
            painter.end();
            painter.dispose();
        }

        private void drawGrid(QPainter painter) {
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, false);
            int ox = drawX - originX;
            int oy = drawY - originY;
            int columnCount = grid.columnCount();
            int rowCount = grid.rowCount();
            int width = drawW;
            int height = drawH;
            if (backgroundColor != null)
                painter.fillRect(ox, oy, width, height, backgroundColor);
            int cellWidth = width / columnCount;
            int cellHeight = height / rowCount;
            // A pen of width t centered at c covers [c - t/2, c + (t+1)/2 - 1], so edge
            // lines are inset by half the thickness to stay fully inside the grid.
            int edgeInsetLow = lineThickness / 2;
            int edgeInsetHigh = lineThickness / 2 + lineThickness % 2;
            QPen pen = new QPen(lineColor);
            pen.setCapStyle(Qt.PenCapStyle.FlatCap);
            pen.setWidth(lineThickness);
            painter.setPen(pen);
            for (int i = 0; i <= columnCount; i++) {
                int x = i == columnCount ? width : i * cellWidth;
                if (i == 0)
                    x += edgeInsetLow;
                else if (i == columnCount)
                    x -= edgeInsetHigh;
                painter.drawLine(ox + x, oy, ox + x, oy + height);
            }
            for (int i = 0; i <= rowCount; i++) {
                int y = i == rowCount ? height : i * cellHeight;
                if (i == 0)
                    y += edgeInsetLow;
                else if (i == rowCount)
                    y -= edgeInsetHigh;
                painter.drawLine(ox, oy + y, ox + width, oy + y);
            }
            pen.dispose();
        }
    }
}
