package mousemaster.renderer;

import io.qt.core.Qt;
import io.qt.gui.QBrush;
import io.qt.core.QPointF;
import io.qt.core.QRect;
import io.qt.gui.QColor;
import io.qt.gui.QFont;
import io.qt.gui.QFontMetrics;
import io.qt.gui.QImage;
import io.qt.gui.QPaintEvent;
import io.qt.gui.QPainter;
import io.qt.gui.QPainterPath;
import io.qt.gui.QPen;
import io.qt.gui.QTransform;
import io.qt.widgets.QWidget;
import mousemaster.EffectFrame;
import mousemaster.EffectShape;
import mousemaster.EffectText;
import mousemaster.Os;
import mousemaster.Point;
import mousemaster.Rectangle;
import mousemaster.Screen;
import mousemaster.qt.QtColorUtil;
import mousemaster.qt.QtHintFont;
import mousemaster.qt.TransparentWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
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

    /**
     * A platform window the renderer paints into itself rather than through a Qt
     * widget: it receives each frame as a premultiplied BGRA image and places it on
     * the screen. On Windows the overlay uses one so it can update the layered window
     * in place (UpdateLayeredWindow without a position): a Qt widget flush passes the
     * window's position along, which Windows treats as a move and re-picks the cursor
     * for, and an animation over a window edge flickered between the resize cursor
     * and the arrow.
     */
    public interface NativeSink {
        /** Shows the image at the given screen position (pixels); called every frame drawn.
         *  Rows are strideBytes apart in the buffer (Qt pads scanlines to 4 bytes). */
        void show(int leftPixels, int topPixels, int width, int height,
                  ByteBuffer bgraPremultiplied, int strideBytes);
        /** Moves the window without redrawing it (a following effect after a mouse move). */
        void move(int leftPixels, int topPixels);
        void hide();
    }

    private final NativeSink sink;
    private TransparentWindow window;
    private EffectWidget widget;
    private QImage image;
    private boolean showing;

    /** A renderer drawing through a Qt window (macOS). */
    public EffectRenderer() {
        this(null);
    }

    /** A renderer drawing into the given platform window (Windows). */
    public EffectRenderer(NativeSink sink) {
        this.sink = sink;
    }
    // The window only grows while showing, so it is not resized (and cleared)
    // frame after frame; it is reset when hidden.
    private int windowWidth, windowHeight;
    // Where the native window was last placed, to skip a move that changes nothing.
    private int placedLeft = Integer.MIN_VALUE, placedTop, placedWidth, placedHeight;
    private Screen placedScreen;
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
        if (sink != null) {
            layout(frames, mouseXPixels, mouseYPixels, screen);
            showing = true;
            present();
            setEffectsCalls++;
            if (setEffectsCalls <= 3)
                logger.debug("Effects frame " + setEffectsCalls + ": " + frames.size() +
                             " effect(s), mouse (" + mouseXPixels + "," + mouseYPixels +
                             "), scale " + screen.scale() + ", window " + placedLeft +
                             "," + placedTop + " " + windowWidth + "x" + windowHeight +
                             ", paints=" + paintCount);
            return;
        }
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

    /** Paints the frames into the image and hands it to the sink. */
    private void present() {
        if (image == null || image.width() != windowWidth || image.height() != windowHeight) {
            if (image != null)
                image.dispose();
            image = new QImage(Math.max(1, windowWidth), Math.max(1, windowHeight),
                    QImage.Format.Format_ARGB32_Premultiplied);
        }
        QPainter painter = new QPainter(image);
        paint(painter, new QRect(0, 0, image.width(), image.height()));
        painter.end();
        painter.dispose();
        sink.show(placedLeft, placedTop, windowWidth, windowHeight, image.bits(),
                (int) image.bytesPerLine());
    }

    // Effects are centered on the cursor's visual center, the point the indicator marks,
    // not on the hotspot (the arrow's tip, at its top left): this is the offset between
    // them, in screen pixels, for the cursor currently shown.
    private double originOffsetX, originOffsetY;

    /** The offset from the mouse position to the cursor's visual center, in screen pixels. */
    public void setOriginOffset(double offsetX, double offsetY) {
        originOffsetX = offsetX;
        originOffsetY = offsetY;
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
        int before = placedLeft, beforeTop = placedTop;
        layout(frames, mouseXPixels, mouseYPixels, screen);
        if (sink != null) {
            if (anyAnchored)
                present();
            else if (placedLeft != before || placedTop != beforeTop)
                sink.move(placedLeft, placedTop);
            return;
        }
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
        this.screenScale = scale;
        this.drawScale = Os.windows ? scale : 1;
        // Each frame's center in screen pixels, and the union of their areas.
        List<int[]> centers = new ArrayList<>();
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        Map<EffectFrame.ResolvedEffectLayer, double[]> shifts = new HashMap<>();
        Rectangle screenRectangle = screen.rectangle();
        for (EffectFrame frame : frames) {
            int centerX = (int) Math.round((frame.anchor() == null ? mouseXPixels : frame.anchor().x()) + originOffsetX);
            int centerY = (int) Math.round((frame.anchor() == null ? mouseYPixels : frame.anchor().y()) + originOffsetY);
            centers.add(new int[]{centerX, centerY});
            int halfWidth = (int) Math.ceil(frame.areaWidth() * scale / 2);
            int halfHeight = (int) Math.ceil(frame.areaHeight() * scale / 2);
            left = Math.min(left, centerX - halfWidth);
            top = Math.min(top, centerY - halfHeight);
            right = Math.max(right, centerX + halfWidth);
            bottom = Math.max(bottom, centerY + halfHeight);
            // A text layer kept on screen: its box (in screen pixels, rotation aside)
            // is pushed inwards by what would stick out, and the window grows to it.
            for (EffectFrame.ResolvedEffectLayer layer : frame.layers()) {
                if (layer.shape() != EffectShape.TEXT || !layer.text().keepOnScreen())
                    continue;
                TextBlock block = textBlock(layer);
                double pixelsPerDrawUnit = scale / drawScale;
                double padding = layer.backgroundHexColor() == null ? 0 : layer.padding() * scale;
                double boxLeft = centerX + layer.x() * scale + block.x0 * pixelsPerDrawUnit - padding;
                double boxTop = centerY + layer.y() * scale + block.y0 * pixelsPerDrawUnit - padding;
                double boxRight = boxLeft + block.width * pixelsPerDrawUnit + 2 * padding;
                double boxBottom = boxTop + block.height * pixelsPerDrawUnit + 2 * padding;
                double shiftX = 0, shiftY = 0;
                if (boxLeft < screenRectangle.x())
                    shiftX = screenRectangle.x() - boxLeft;
                else if (boxRight > screenRectangle.x() + screenRectangle.width())
                    shiftX = screenRectangle.x() + screenRectangle.width() - boxRight;
                if (boxTop < screenRectangle.y())
                    shiftY = screenRectangle.y() - boxTop;
                else if (boxBottom > screenRectangle.y() + screenRectangle.height())
                    shiftY = screenRectangle.y() + screenRectangle.height() - boxBottom;
                if (shiftX != 0 || shiftY != 0) {
                    shifts.put(layer, new double[]{shiftX, shiftY});
                    left = Math.min(left, (int) Math.floor(boxLeft + shiftX) - 1);
                    top = Math.min(top, (int) Math.floor(boxTop + shiftY) - 1);
                    right = Math.max(right, (int) Math.ceil(boxRight + shiftX) + 1);
                    bottom = Math.max(bottom, (int) Math.ceil(boxBottom + shiftY) + 1);
                }
            }
        }
        this.textShifts = shifts;
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
        // Only touch the native window when its place or size changed: a SetWindowPos
        // under the mouse makes Windows re-pick the cursor, and doing that every frame
        // of an animation over a window edge flickered between the resize cursor and
        // the arrow.
        if (windowLeft != placedLeft || windowTop != placedTop ||
            windowWidth != placedWidth || windowHeight != placedHeight ||
            !screen.equals(placedScreen)) {
            if (sink == null) {
                window.moveAndResizeInPixels(screen, windowLeft, windowTop, windowWidth,
                        windowHeight);
                widget.setGeometry(0, 0, window.width(), window.height());
            }
            placedLeft = windowLeft;
            placedTop = windowTop;
            placedWidth = windowWidth;
            placedHeight = windowHeight;
            placedScreen = screen;
        }
        // Frame centers in the window's own pixel coordinates.
        List<Point> windowCenters = new ArrayList<>();
        for (int[] center : centers)
            windowCenters.add(new Point(center[0] - windowLeft, center[1] - windowTop));
        // Qt units are pixels on Windows and points on macOS: draw scaled on Windows.
        showFrames(frames, windowCenters, drawScale);
    }

    public void hide() {
        if (!showing)
            return;
        showing = false;
        clearFrames();
        placedLeft = Integer.MIN_VALUE;
        placedScreen = null;
        if (sink != null)
            sink.hide();
        else
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
    // The active screen's scale: screen pixels per logical pixel.
    private double screenScale = 1;
    private int paintCount;
    // Fonts are looked up by family in the font database: cache them per
    // (family, size, weight, italic), cleared when it grows past a bound, since an
    // animated font-size makes many sizes.
    private final Map<String, QFont> fontCache = new HashMap<>();
    // Where a text layer kept on screen is drawn instead of at its position, in
    // screen pixels (see layout).
    private Map<EffectFrame.ResolvedEffectLayer, double[]> textShifts = Map.of();
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
        QPainter painter = new QPainter(widget);
        paint(painter, event.rect());
        painter.end();
        painter.dispose();
    }

    /** Clears the rectangle to transparent and draws the frames over it. */
    private void paint(QPainter painter, QRect rect) {
        paintCount++;
        QColor transparent = new QColor(0, 0, 0, 0);
        painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Clear);
        painter.fillRect(rect, transparent);
        transparent.dispose();
        if (frames != null) {
            painter.setCompositionMode(
                    QPainter.CompositionMode.CompositionMode_SourceOver);
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            for (int i = 0; i < frames.size(); i++)
                drawFrame(painter, frames.get(i), centers.get(i).x(), centers.get(i).y());
        }
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
        double[] shift = textShifts.get(layer);
        if (shift != null) {
            // A text kept on screen may sit outside its effect's area: the area clip
            // (set per frame) would cut it, so it is lifted for this layer.
            painter.setClipping(false);
            double drawUnitsPerPixel = drawScale / Math.max(1e-9, screenScale);
            painter.translate(shift[0] * drawUnitsPerPixel, shift[1] * drawUnitsPerPixel);
        }
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
        TextBlock block = textBlock(layer);
        QFont font = font(layer);
        if (layer.backgroundHexColor() != null) {
            double padding = layer.padding() * drawScale;
            double radius = layer.cornerRadius() * drawScale;
            QPainterPath box = new QPainterPath();
            box.addRoundedRect(block.x0 - padding, block.y0 - padding,
                    block.width + 2 * padding, block.height + 2 * padding, radius, radius);
            QColor backgroundColor =
                    QtColorUtil.qColor(layer.backgroundHexColor(), layer.opacity());
            QBrush backgroundBrush = new QBrush(backgroundColor);
            painter.fillPath(box, backgroundBrush);
            backgroundBrush.dispose();
            backgroundColor.dispose();
            box.dispose();
        }
        if (layer.outlineHexColor() != null && layer.outlineThickness() > 0) {
            QColor outlineColor = QtColorUtil.qColor(layer.outlineHexColor(), layer.opacity());
            QPen outlinePen = new QPen(outlineColor);
            outlinePen.setWidthF(layer.outlineThickness() * drawScale);
            outlinePen.setJoinStyle(Qt.PenJoinStyle.RoundJoin);
            painter.setPen(outlinePen);
            painter.setBrush(QtColorUtil.noBrush());
            QPainterPath glyphs = new QPainterPath();
            for (int i = 0; i < block.lines.size(); i++)
                glyphs.addText(block.lineX(i), block.baselineY(i), font, block.lines.get(i));
            painter.drawPath(glyphs);
            glyphs.dispose();
            outlinePen.dispose();
            outlineColor.dispose();
        }
        QColor color = QtColorUtil.qColor(layer.hexColor(), layer.opacity());
        painter.setPen(color);
        painter.setFont(font);
        for (int i = 0; i < block.lines.size(); i++)
            painter.drawText(new QPointF(block.lineX(i), block.baselineY(i)), block.lines.get(i));
        color.dispose();
    }

    /**
     * A text layer's lines and their box, in draw units, relative to the layer's
     * position: one line, or several when max-width wraps the text at its spaces.
     * The box is aligned on x by text-align and centered on y like the indicator
     * label; each line is aligned the same way inside the box.
     */
    private record TextBlock(List<String> lines, List<Double> advances, double width,
                             double height, double lineHeight, double ascent, double x0,
                             double y0, EffectText.Align align) {

        double lineX(int i) {
            double advance = advances.get(i);
            return switch (align) {
                case LEFT -> x0;
                case CENTER -> x0 + (width - advance) / 2;
                case RIGHT -> x0 + width - advance;
            };
        }

        double baselineY(int i) {
            return y0 + ascent + i * lineHeight;
        }
    }

    private TextBlock textBlock(EffectFrame.ResolvedEffectLayer layer) {
        QFont font = font(layer);
        QFontMetrics metrics = new QFontMetrics(font);
        List<String> lines = new ArrayList<>();
        String text = layer.text().text();
        double maxWidth = layer.text().maxWidth() * drawScale;
        if (!layer.text().wraps() || text.indexOf(' ') == -1)
            lines.add(text);
        else {
            // Greedy wrap at spaces: a word longer than the width stays on its own line.
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && metrics.horizontalAdvance(candidate) > maxWidth) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                }
                else
                    line = new StringBuilder(candidate);
            }
            lines.add(line.toString());
        }
        List<Double> advances = new ArrayList<>(lines.size());
        double width = 0;
        for (String line : lines) {
            double advance = metrics.horizontalAdvance(line);
            advances.add(advance);
            width = Math.max(width, advance);
        }
        double lineHeight = metrics.height();
        double ascent = metrics.ascent();
        double height = lineHeight * lines.size();
        metrics.dispose();
        double x0 = switch (layer.text().align()) {
            case LEFT -> 0;
            case CENTER -> -width / 2;
            case RIGHT -> -width;
        };
        return new TextBlock(lines, advances, width, height, lineHeight, ascent, x0,
                -height / 2, layer.text().align());
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
            case STAR -> {
                // edge-count points, a point at the top (like an odd polygon), the inner
                // vertices at 0.4 of the radius: the classic five-point star is 0.38.
                int points = Math.max(3, layer.edgeCount());
                double outer = Math.min(width, height) / 2;
                double inner = outer * 0.4;
                for (int i = 0; i < 2 * points; i++) {
                    double radius = i % 2 == 0 ? outer : inner;
                    double angle = -Math.PI / 2 + Math.PI * i / points;
                    double x = radius * Math.cos(angle), y = radius * Math.sin(angle);
                    if (i == 0)
                        path.moveTo(x, y);
                    else
                        path.lineTo(x, y);
                }
                path.closeSubpath();
            }
            case PATH -> {
                // The layer's own corners, in pixels from its center; the size is not
                // used, but scale (and rotation, like any layer) applies.
                double factor = layer.scale() * drawScale;
                List<Point> points = layer.points();
                for (int i = 0; i < points.size(); i++) {
                    double x = points.get(i).x() * factor, y = points.get(i).y() * factor;
                    if (i == 0)
                        path.moveTo(x, y);
                    else
                        path.lineTo(x, y);
                }
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
