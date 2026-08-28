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
import mousemaster.Os;
import mousemaster.Screen;
import mousemaster.qt.QtColorUtil;
import mousemaster.qt.TransparentWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Cross-platform Qt rendering of the effects: one transparent window centered on
 * the mouse position (the same {@link TransparentWindow} + child widget pattern as
 * the indicator), redrawn every tick with the fully-resolved frames the
 * {@link mousemaster.EffectManager} hands over. The platform overlay owns the
 * native window (styles its handle) and supplies the mouse position and screen.
 */
public final class EffectRenderer {

    private static final Logger logger = LoggerFactory.getLogger(EffectRenderer.class);

    private TransparentWindow window;
    private EffectWidget widget;
    private boolean showing;
    // The window never shrinks, so it is not resized (and cleared) frame after frame.
    private int windowSizePixels;
    private int setEffectsCalls;

    /** Lazily creates the window and its widget; the host styles winId() afterwards. */
    public TransparentWindow window() {
        if (window == null) {
            window = new TransparentWindow();
            widget = new EffectWidget(window);
        }
        return window;
    }

    public boolean showing() {
        return showing;
    }

    /** Shows the frames in a window centered on the given mouse position (in screen
     *  pixels). Frame coordinates and sizes are logical: they scale by the screen's
     *  scale on Windows, and are Qt points as-is on macOS. */
    public void setEffects(List<EffectFrame> frames, int mouseXPixels,
                           int mouseYPixels, Screen screen) {
        window();
        int maxScaledArea = 0;
        for (EffectFrame frame : frames)
            maxScaledArea = Math.max(maxScaledArea,
                    (int) Math.ceil(Math.max(frame.areaWidth(), frame.areaHeight()) *
                                    screen.scale()));
        // An odd size puts the center half a pixel off (like the indicator).
        maxScaledArea += maxScaledArea % 2;
        windowSizePixels = Math.max(windowSizePixels, maxScaledArea);
        window.moveAndResizeInPixels(screen,
                mouseXPixels - windowSizePixels / 2,
                mouseYPixels - windowSizePixels / 2,
                windowSizePixels, windowSizePixels);
        widget.setGeometry(0, 0, window.width(), window.height());
        // Qt units are pixels on Windows and points on macOS: draw scaled on Windows.
        widget.showFrames(frames, Os.windows ? screen.scale() : 1);
        if (!showing) {
            showing = true;
            window.show();
            widget.show();
        }
        widget.repaint();
        setEffectsCalls++;
        if (setEffectsCalls <= 3)
            logger.debug("Effects frame " + setEffectsCalls + ": " + frames.size() +
                         " effect(s), mouse (" + mouseXPixels + "," + mouseYPixels +
                         "), scale " + screen.scale() + ", window " +
                         window.x() + "," + window.y() + " " + window.width() + "x" +
                         window.height() + " visible=" + window.isVisible() +
                         ", widget " + widget.width() + "x" + widget.height() +
                         " visible=" + widget.isVisible() + ", paints=" +
                         widget.paintCount);
    }

    public void hide() {
        if (!showing)
            return;
        showing = false;
        widget.clearFrames();
        window.hide();
        logger.debug("Effects hidden after " + setEffectsCalls + " frames, " +
                     widget.paintCount + " paints");
        setEffectsCalls = 0;
    }

    private static final class EffectWidget extends QWidget {

        private List<EffectFrame> frames;
        private double drawScale = 1;
        private int paintCount;

        EffectWidget(QWidget parent) {
            super(parent);
        }

        void showFrames(List<EffectFrame> frames, double drawScale) {
            this.frames = frames;
            this.drawScale = drawScale;
        }

        void clearFrames() {
            frames = null;
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            paintCount++;
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
            double areaWidth = frame.areaWidth() * drawScale;
            double areaHeight = frame.areaHeight() * drawScale;
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
            double width = layer.width() * drawScale;
            double height = layer.height() * drawScale;
            painter.save();
            painter.translate(center + layer.x() * drawScale,
                    center + layer.y() * drawScale);
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
                pen.setWidthF(Math.max(1, layer.thickness() * drawScale));
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
