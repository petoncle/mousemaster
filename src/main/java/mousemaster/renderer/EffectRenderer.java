package mousemaster.renderer;

import io.qt.core.Qt;
import io.qt.gui.QBrush;
import io.qt.core.QPointF;
import io.qt.core.QRect;
import io.qt.gui.QColor;
import io.qt.gui.QFont;
import io.qt.gui.QFontMetrics;
import io.qt.gui.QPaintEvent;
import io.qt.gui.QPainter;
import io.qt.gui.QPainterPath;
import io.qt.gui.QPen;
import io.qt.gui.QTransform;
import io.qt.widgets.QWidget;
import mousemaster.EffectFrame;
import mousemaster.EffectShape;
import mousemaster.Os;
import mousemaster.Point;
import mousemaster.Screen;
import mousemaster.qt.QtColorUtil;
import mousemaster.qt.QtHintFont;
import mousemaster.qt.TransparentWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private int windowWidth, windowHeight;
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
        boolean firstFrame = setEffectsCalls == 0;
        if (firstFrame)
            logger.debug("Effects first frame: moving the window");
        layout(frames, mouseXPixels, mouseYPixels, screen);
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
                         "), scale " + screen.scale() + ", window " +
                         window.x() + "," + window.y() + " " + window.width() + "x" +
                         window.height() + " visible=" + window.isVisible() +
                         ", widget " + widget.width() + "x" + widget.height() +
                         " visible=" + widget.isVisible() + ", paints=" +
                         paintCount);
    }

    /** Whether a shown frame is centered on the mouse (rather than on its anchor). */
    public boolean followingMouse() {
        if (!showing || frames == null)
            return false;
        for (EffectFrame frame : frames)
            if (frame.anchor() == null)
                return true;
        return false;
    }

    /**
     * Moves the frames that follow the mouse to a new mouse position, without
     * waiting for the next tick's frames: the platform calls this as soon as it
     * learns of a mouse move, like it repositions the indicator, so a following
     * effect trails the cursor by as little as the indicator does.
     */
    public void mouseMoved(int mouseXPixels, int mouseYPixels, Screen screen) {
        if (!followingMouse())
            return;
        // A following frame keeps its place in the window, which moves as a whole; only
        // an anchored frame sharing the window has to be redrawn at its new place in it.
        boolean anyAnchored = false;
        for (EffectFrame frame : frames)
            anyAnchored |= frame.anchor() != null;
        layout(frames, mouseXPixels, mouseYPixels, screen);
        if (anyAnchored)
            widget.repaint();
    }

    /**
     * Places the window over the union of the frames' areas and records where each
     * frame is drawn in it. The window keeps the largest size it has had while
     * showing rather than being resized frame after frame (a resize shows the old
     * surface at the new size for a frame, and a following effect would otherwise
     * leave a growing window behind it); it is reset when hidden.
     */
    private void layout(List<EffectFrame> frames, int mouseXPixels, int mouseYPixels,
                        Screen screen) {
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
            windowWidth = right - left;
            windowHeight = bottom - top;
        }
        else {
            windowWidth = Math.max(windowWidth, right - left);
            windowHeight = Math.max(windowHeight, bottom - top);
        }
        // The union sits in the middle of the (possibly larger) window.
        int windowLeft = (left + right) / 2 - windowWidth / 2;
        int windowTop = (top + bottom) / 2 - windowHeight / 2;
        window.moveAndResizeInPixels(screen, windowLeft, windowTop, windowWidth,
                windowHeight);
        widget.setGeometry(0, 0, window.width(), window.height());
        // Frame centers in the window's own pixel coordinates.
        List<Point> windowCenters = new ArrayList<>();
        for (int[] center : centers)
            windowCenters.add(new Point(center[0] - windowLeft, center[1] - windowTop));
        // Qt units are pixels on Windows and points on macOS: draw scaled on Windows.
        showFrames(frames, windowCenters, Os.windows ? scale : 1);
    }

    public void hide() {
        if (!showing)
            return;
        showing = false;
        clearFrames();
        window.hide();
        logger.debug("Effects hidden after " + setEffectsCalls + " frames, " +
                     paintCount + " paints");
        setEffectsCalls = 0;
    }

    /**
     * The Qt side is kept to this: a widget whose paintEvent hands the painter to
     * the renderer. QtJambi analyzes a QWidget subclass reflectively when it is
     * first instantiated (fields and methods, to build its meta-object), and in the
     * GraalVM native image that analysis hung on a widget carrying the frame state
     * (a List of double[] field and an anonymous map subclass). So the widget
     * declares no fields or methods of its own besides paintEvent, like the
     * indicator's widget, and the state lives in the enclosing renderer.
     */
    private final class EffectWidget extends QWidget {

        EffectWidget(QWidget parent) {
            super(parent);
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            paint(this, event);
        }

    }

    // ---- drawing state (kept out of the QWidget subclass, see EffectWidget) ----
    private List<EffectFrame> frames;
    private List<Point> centers;
    private double drawScale = 1;
    private int paintCount;
    // Fonts are looked up by family in the font database: cache them per
    // (family, size, weight, italic), cleared when it grows past a bound, since an
    // animated font-size makes many sizes.
    private final Map<String, QFont> fontCache = new HashMap<>();
    private boolean drawFailureLogged;

    private void showFrames(List<EffectFrame> frames, List<Point> centers, double drawScale) {
        this.frames = frames;
        this.centers = centers;
        this.drawScale = drawScale;
    }

    private void clearFrames() {
        frames = null;
        centers = null;
    }

    private void paint(QWidget widget, QPaintEvent event) {
        paintCount++;
        QPainter painter = new QPainter(widget);
        QColor transparent = new QColor(0, 0, 0, 0);
        painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Clear);
        painter.fillRect(event.rect(), transparent);
        transparent.dispose();
        if (frames != null) {
            painter.setCompositionMode(
                    QPainter.CompositionMode.CompositionMode_SourceOver);
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            for (int i = 0; i < frames.size(); i++)
                drawFrame(painter, frames.get(i), centers.get(i).x(), centers.get(i).y());
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
        for (EffectFrame.ResolvedEffectLayer layer : frame.layers()) {
            try {
                drawLayer(painter, layer, centerX, centerY);
            } catch (RuntimeException e) {
                // A layer that cannot be drawn is skipped, once loudly, then quietly:
                // a paint callback is no place to take the process down.
                if (!drawFailureLogged) {
                    drawFailureLogged = true;
                    logger.error("Effect layer could not be drawn (skipped from now on): " +
                                 layer, e);
                }
            }
        }
        painter.restore();
    }

    private void drawLayer(QPainter painter,
                           EffectFrame.ResolvedEffectLayer layer, double centerX,
                           double centerY) {
        double width = layer.width() * drawScale;
        double height = layer.height() * drawScale;
        painter.save();
        try {
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
        if (layer.shape() == EffectShape.TEXT) {
            drawText(painter, layer);
            return;
        }
        QColor color = QtColorUtil.qColor(layer.hexColor(), layer.opacity());
        QPainterPath path = layerPath(layer, width, height);
        boolean stroke = layer.shape() == EffectShape.LINE ||
                         layer.shape() == EffectShape.CROSS || !layer.filled();
        if (stroke) {
            QPen pen = new QPen(color);
            double penWidth = Math.max(1, layer.thickness() * drawScale);
            pen.setWidthF(penWidth);
            pen.setCapStyle(Qt.PenCapStyle.FlatCap);
            if (layer.dashLength() > 0) {
                // Qt measures dash patterns in pen widths; the config is in pixels.
                List<Double> pattern = new ArrayList<>();
                pattern.add(layer.dashLength() * drawScale / penWidth);
                pattern.add(Math.max(0.01, layer.dashGap() * drawScale / penWidth));
                pen.setDashPattern(pattern);
                pen.setDashOffset(layer.dashOffset() * drawScale / penWidth);
            }
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
        } finally {
            painter.restore();
        }
    }

    /**
     * Text at the layer's position: the font from the hint font machinery (same
     * family lookup and antialiasing as hints and the indicator label), aligned
     * on x by text-align and centered on y like the indicator label, over an
     * optional background box and with an optional glyph outline.
     */
    private void drawText(QPainter painter, EffectFrame.ResolvedEffectLayer layer) {
        String text = layer.text().text();
        QFont font = font(layer);
        QFontMetrics metrics = new QFontMetrics(font);
        double advance = metrics.horizontalAdvance(text);
        QRect tight = metrics.tightBoundingRect(text);
        double textX = switch (layer.text().align()) {
            case LEFT -> 0;
            case CENTER -> -advance / 2;
            case RIGHT -> -advance;
        };
        double textY = -tight.y() - tight.height() / 2.0;
        if (layer.backgroundHexColor() != null) {
            double padding = layer.padding() * drawScale;
            double radius = layer.cornerRadius() * drawScale;
            QPainterPath box = new QPainterPath();
            box.addRoundedRect(textX + tight.x() - padding, textY + tight.y() - padding,
                    tight.width() + 2 * padding, tight.height() + 2 * padding,
                    radius, radius);
            QColor backgroundColor =
                    QtColorUtil.qColor(layer.backgroundHexColor(), layer.opacity());
            QBrush backgroundBrush = new QBrush(backgroundColor);
            painter.fillPath(box, backgroundBrush);
            backgroundBrush.dispose();
            backgroundColor.dispose();
            box.dispose();
        }
        tight.dispose();
        metrics.dispose();
        if (layer.outlineHexColor() != null && layer.outlineThickness() > 0) {
            QColor outlineColor = QtColorUtil.qColor(layer.outlineHexColor(), layer.opacity());
            QPen outlinePen = new QPen(outlineColor);
            outlinePen.setWidthF(layer.outlineThickness() * drawScale);
            outlinePen.setJoinStyle(Qt.PenJoinStyle.RoundJoin);
            painter.setPen(outlinePen);
            painter.setBrush(QtColorUtil.noBrush());
            QPainterPath glyphs = new QPainterPath();
            glyphs.addText(textX, textY, font, text);
            painter.drawPath(glyphs);
            glyphs.dispose();
            outlinePen.dispose();
            outlineColor.dispose();
        }
        QColor color = QtColorUtil.qColor(layer.hexColor(), layer.opacity());
        painter.setPen(color);
        painter.setFont(font);
        painter.drawText(new QPointF(textX, textY), text);
        color.dispose();
    }

    private QFont font(EffectFrame.ResolvedEffectLayer layer) {
        double size = layer.fontSize() * layer.scale() * drawScale;
        String key = layer.text().fontName() + "|" + size + "|" + layer.text().weight() +
                     "|" + layer.text().italic();
        QFont font = fontCache.get(key);
        if (font == null) {
            font = QtHintFont.qFont(layer.text().fontName(), size, layer.text().weight());
            font.setItalic(layer.text().italic());
            fontCache.put(key, font);
        }
        return font;
    }

    private QPainterPath layerPath(EffectFrame.ResolvedEffectLayer layer,
                                   double width, double height) {
        QPainterPath path = new QPainterPath();
        switch (layer.shape()) {
            case DOT, CIRCLE ->
                    path.addEllipse(-width / 2, -height / 2, width, height);
            case RECT -> {
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
                double qtSweep = -layer.arcLength();
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
