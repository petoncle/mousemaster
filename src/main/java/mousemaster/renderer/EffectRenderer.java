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

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-platform Qt rendering of the effects: one transparent window covering the
 * running effects (the same {@link TransparentWindow} + child widget pattern as the
 * indicator), redrawn every tick with the fully-resolved frames the
 * {@link mousemaster.EffectManager} hands over. An effect that follows the mouse is
 * centered on the mouse position; one that does not is centered on its anchor. The
 * platform overlay owns the native window (styles its handle) and supplies the
 * mouse position and screen.
 */
public final class EffectRenderer {

    private static final Logger logger = LoggerFactory.getLogger(EffectRenderer.class);

    private TransparentWindow window;
    private EffectWidget widget;
    private boolean showing;
    // The window only grows while showing, so it is not resized (and cleared)
    // frame after frame; it is reset when hidden.
    private int windowLeft, windowTop, windowRight, windowBottom;
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

    /** Shows the frames around the given mouse position (in screen pixels). Frame
     *  coordinates and sizes are logical: they scale by the screen's scale on
     *  Windows, and are Qt points as-is on macOS. */
    public void setEffects(List<EffectFrame> frames, int mouseXPixels,
                           int mouseYPixels, Screen screen) {
        window();
        double scale = screen.scale();
        // Each frame's center in screen pixels, and the union of their areas.
        List<int[]> centers = new ArrayList<>();
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (EffectFrame frame : frames) {
            int centerX = frame.anchor() == null ? mouseXPixels : (int) Math.round(frame.anchor().x());
            int centerY = frame.anchor() == null ? mouseYPixels : (int) Math.round(frame.anchor().y());
            centers.add(new int[]{centerX, centerY});
            int halfWidth = (int) Math.ceil(frame.areaWidth() * scale / 2);
            int halfHeight = (int) Math.ceil(frame.areaHeight() * scale / 2);
            left = Math.min(left, centerX - halfWidth);
            top = Math.min(top, centerY - halfHeight);
            right = Math.max(right, centerX + halfWidth);
            bottom = Math.max(bottom, centerY + halfHeight);
        }
        if (!showing) {
            windowLeft = left;
            windowTop = top;
            windowRight = right;
            windowBottom = bottom;
        }
        else {
            windowLeft = Math.min(windowLeft, left);
            windowTop = Math.min(windowTop, top);
            windowRight = Math.max(windowRight, right);
            windowBottom = Math.max(windowBottom, bottom);
        }
        boolean firstFrame = setEffectsCalls == 0;
        if (firstFrame)
            logger.debug("Effects first frame: moving the window");
        window.moveAndResizeInPixels(screen, windowLeft, windowTop,
                windowRight - windowLeft, windowBottom - windowTop);
        widget.setGeometry(0, 0, window.width(), window.height());
        // Frame centers in the window's own pixel coordinates.
        List<double[]> windowCenters = new ArrayList<>();
        for (int[] center : centers)
            windowCenters.add(new double[]{center[0] - windowLeft, center[1] - windowTop});
        // Qt units are pixels on Windows and points on macOS: draw scaled on Windows.
        widget.showFrames(frames, windowCenters, Os.windows ? scale : 1);
        if (firstFrame)
            logger.debug("Effects first frame: window moved, showing it");
        if (!showing) {
            showing = true;
            window.show();
            widget.show();
        }
        if (firstFrame)
            logger.debug("Effects first frame: shown, repainting");
        widget.repaint();
        setEffectsCalls++;
        if (setEffectsCalls <= 3)
            logger.debug("Effects frame " + setEffectsCalls + ": " + frames.size() +
                         " effect(s), mouse (" + mouseXPixels + "," + mouseYPixels +
                         "), scale " + scale + ", window " +
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
        private List<double[]> centers;
        private double drawScale = 1;
        private int paintCount;

        EffectWidget(QWidget parent) {
            super(parent);
        }

        void showFrames(List<EffectFrame> frames, List<double[]> centers,
                        double drawScale) {
            this.frames = frames;
            this.centers = centers;
            this.drawScale = drawScale;
        }

        void clearFrames() {
            frames = null;
            centers = null;
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
                for (int i = 0; i < frames.size(); i++)
                    drawFrame(painter, frames.get(i), centers.get(i)[0], centers.get(i)[1]);
            }
            painter.end();
            painter.dispose();
        }

        private void drawFrame(QPainter painter, EffectFrame frame, double centerX,
                               double centerY) {
            double areaWidth = frame.areaWidth() * drawScale;
            double areaHeight = frame.areaHeight() * drawScale;
            painter.save();
            // Each effect clips to its own area, so an area-sized background layer
            // cannot bleed into another effect drawn in the same window.
            painter.setClipRect((int) Math.round(centerX - areaWidth / 2),
                    (int) Math.round(centerY - areaHeight / 2),
                    (int) Math.round(areaWidth), (int) Math.round(areaHeight));
            for (EffectFrame.ResolvedEffectLayer layer : frame.layers())
                drawLayer(painter, layer, centerX, centerY);
            painter.restore();
        }

        private void drawLayer(QPainter painter,
                               EffectFrame.ResolvedEffectLayer layer, double centerX,
                               double centerY) {
            double width = layer.width() * drawScale;
            double height = layer.height() * drawScale;
            painter.save();
            // Rotate about the pivot, then place the layer relative to it: with the
            // pivot at the layer's own center (the default) this is a spin in place,
            // with the pivot elsewhere it is an orbit.
            painter.translate(centerX + layer.pivotX() * drawScale,
                    centerY + layer.pivotY() * drawScale);
            painter.rotate(layer.rotation());
            painter.translate((layer.x() - layer.pivotX()) * drawScale,
                    (layer.y() - layer.pivotY()) * drawScale);
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
            QPainterPath path = layerPath(layer, width, height);
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

        private QPainterPath layerPath(EffectFrame.ResolvedEffectLayer layer,
                                       double width, double height) {
            QPainterPath path = new QPainterPath();
            switch (layer.shape()) {
                case DOT, CIRCLE ->
                        path.addEllipse(-width / 2, -height / 2, width, height);
                case SQUARE -> {
                    double radius = layer.cornerRadius() * drawScale;
                    if (radius > 0)
                        path.addRoundedRect(-width / 2, -height / 2, width, height,
                                radius, radius);
                    else
                        path.addRect(-width / 2, -height / 2, width, height);
                }
                case TRIANGLE -> {
                    path.moveTo(0, -height / 2);
                    path.lineTo(width / 2, height / 2);
                    path.lineTo(-width / 2, height / 2);
                    path.closeSubpath();
                }
                case POLYGON -> {
                    // The indicator's polygon (same edge-count convention and
                    // orientation), regular, inscribed in the smaller side.
                    path.dispose();
                    return IndicatorRenderer.IndicatorWidget.polygonPath(0, 0,
                            Math.min(width, height) / 2, layer.edgeCount());
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
                case ARC -> {
                    // Config angles are clockwise from 12 o'clock (the indicator's fill
                    // angle convention); Qt's are counterclockwise from 3 o'clock.
                    double qtStart = 90 - layer.arcStart();
                    double qtSweep = -layer.arcSweep();
                    if (layer.filled()) {
                        path.moveTo(0, 0);
                        path.arcTo(-width / 2, -height / 2, width, height, qtStart, qtSweep);
                        path.closeSubpath();
                    }
                    else {
                        path.arcMoveTo(-width / 2, -height / 2, width, height, qtStart);
                        path.arcTo(-width / 2, -height / 2, width, height, qtStart, qtSweep);
                    }
                }
            }
            return path;
        }

    }

}
