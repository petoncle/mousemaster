package mousemaster.renderer;

import io.qt.core.Qt;
import io.qt.gui.QBrush;
import io.qt.gui.QColor;
import io.qt.gui.QPaintEvent;
import io.qt.gui.QPainter;
import io.qt.gui.QPainterPath;
import io.qt.gui.QPen;
import io.qt.gui.QTransform;
import io.qt.widgets.QWidget;
import mousemaster.EffectFrame;
import mousemaster.EffectShape;
import mousemaster.qt.QtColorUtil;

import java.util.List;

/**
 * Cross-platform Qt rendering of the effects: one transparent window centered on
 * the mouse position, redrawn every tick with the fully-resolved frames the
 * {@link mousemaster.EffectManager} hands over. The platform overlay owns the
 * native window (styles its handle) and supplies the mouse position and screen
 * scale.
 */
public final class EffectRenderer {

    private final EffectWidget widget = new EffectWidget();
    private boolean showing;
    // The window never shrinks, so it is not resized (and cleared) frame after frame.
    private int windowSize;

    public QWidget widget() {
        return widget;
    }

    public boolean showing() {
        return showing;
    }

    /** Shows the frames in a window centered on the mouse position. Coordinates and
     *  sizes in the frames are logical: they are multiplied by the screen scale. */
    public void setEffects(List<EffectFrame> frames, int mouseX, int mouseY,
                           double screenScale) {
        int maxScaledArea = 0;
        for (EffectFrame frame : frames)
            maxScaledArea = Math.max(maxScaledArea,
                    (int) Math.ceil(Math.max(frame.areaWidth(), frame.areaHeight()) *
                                    screenScale));
        // An odd size puts the center half a pixel off (like the indicator).
        maxScaledArea += maxScaledArea % 2;
        windowSize = Math.max(windowSize, maxScaledArea);
        widget.showFrames(frames, screenScale, windowSize);
        widget.move(mouseX - windowSize / 2, mouseY - windowSize / 2);
        if (!showing) {
            showing = true;
            widget.show();
        }
    }

    public void hide() {
        if (!showing)
            return;
        showing = false;
        widget.hideFrames();
    }

    private static final class EffectWidget extends QWidget {

        private List<EffectFrame> frames;
        private double screenScale = 1;

        EffectWidget() {
            setWindowFlags(Qt.WindowType.FramelessWindowHint);
            setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground);
        }

        void showFrames(List<EffectFrame> frames, double screenScale, int windowSize) {
            this.frames = frames;
            this.screenScale = screenScale;
            if (width() != windowSize || height() != windowSize)
                resize(windowSize, windowSize);
            repaint();
        }

        void hideFrames() {
            frames = null;
            repaint();
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            QPainter painter = new QPainter(this);
            QColor transparent = new QColor(0, 0, 0, 0);
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Clear);
            painter.fillRect(event.rect(), transparent);
            transparent.dispose();
            if (frames != null) {
                painter.setCompositionMode(
                        QPainter.CompositionMode.CompositionMode_SourceOver);
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
                double center = width() / 2d;
                for (EffectFrame frame : frames)
                    drawFrame(painter, frame, center);
            }
            painter.end();
            painter.dispose();
        }

        private void drawFrame(QPainter painter, EffectFrame frame, double center) {
            double areaWidth = frame.areaWidth() * screenScale;
            double areaHeight = frame.areaHeight() * screenScale;
            painter.save();
            // Each effect clips to its own area, so an area-sized background layer
            // cannot bleed into another effect drawn in the same window.
            painter.setClipRect((int) Math.round(center - areaWidth / 2),
                    (int) Math.round(center - areaHeight / 2),
                    (int) Math.round(areaWidth), (int) Math.round(areaHeight));
            for (EffectFrame.ResolvedEffectLayer layer : frame.layers())
                drawLayer(painter, layer, center);
            painter.restore();
        }

        private void drawLayer(QPainter painter,
                               EffectFrame.ResolvedEffectLayer layer, double center) {
            double width = layer.width() * screenScale;
            double height = layer.height() * screenScale;
            painter.save();
            painter.translate(center + layer.x() * screenScale,
                    center + layer.y() * screenScale);
            painter.rotate(layer.rotation());
            if (layer.rotationX() != 0 || layer.rotationY() != 0) {
                // 3D-projected tilt around the layer's own center (Qt applies a
                // perspective projection for the X and Y axes).
                QTransform tilt = new QTransform();
                tilt.rotate(layer.rotationX(), Qt.Axis.XAxis);
                tilt.rotate(layer.rotationY(), Qt.Axis.YAxis);
                painter.setWorldTransform(tilt, true);
                tilt.dispose();
            }
            QColor color = QtColorUtil.qColor(layer.hexColor(), layer.opacity());
            QPainterPath path = layerPath(layer.shape(), width, height);
            boolean stroke = layer.shape() == EffectShape.LINE ||
                             layer.shape() == EffectShape.CROSS || !layer.filled();
            if (stroke) {
                QPen pen = new QPen(color);
                pen.setWidthF(Math.max(1, layer.thickness() * screenScale));
                pen.setCapStyle(Qt.PenCapStyle.FlatCap);
                painter.setPen(pen);
                painter.setBrush(QtColorUtil.noBrush());
                painter.drawPath(path);
                pen.dispose();
            }
            else {
                // Not QtColorUtil.qBrush: an animated opacity would grow its rgba-keyed
                // cache by one brush per frame.
                QBrush brush = new QBrush(color);
                painter.fillPath(path, brush);
                brush.dispose();
            }
            path.dispose();
            color.dispose();
            painter.restore();
        }

        private static QPainterPath layerPath(EffectShape shape, double width,
                                              double height) {
            QPainterPath path = new QPainterPath();
            switch (shape) {
                case DOT, CIRCLE ->
                        path.addEllipse(-width / 2, -height / 2, width, height);
                case SQUARE -> path.addRect(-width / 2, -height / 2, width, height);
                case TRIANGLE -> {
                    path.moveTo(0, -height / 2);
                    path.lineTo(width / 2, height / 2);
                    path.lineTo(-width / 2, height / 2);
                    path.closeSubpath();
                }
                case LINE -> {
                    path.moveTo(-width / 2, 0);
                    path.lineTo(width / 2, 0);
                }
                case CROSS -> {
                    path.moveTo(-width / 2, -height / 2);
                    path.lineTo(width / 2, height / 2);
                    path.moveTo(-width / 2, height / 2);
                    path.lineTo(width / 2, -height / 2);
                }
            }
            return path;
        }

    }

}
