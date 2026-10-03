package mousemaster.renderer;

import mousemaster.qt.*;

import io.qt.core.*;
import io.qt.gui.*;
import io.qt.widgets.*;
import mousemaster.*;
import mousemaster.GradientColor.GradientArea;
import mousemaster.GradientColor.GradientStep;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cross-platform Qt rendering of the mouse indicator: owns a widget and a label widget per
 * layer and the shadow effects, and computes where and how big to draw each layer.
 * The platform overlay owns the native window (styles its handle) and supplies the cursor,
 * screen and zoom.
 */
public final class IndicatorRenderer {

    private TransparentWindow window;
    private QWidget layersWidget;
    private final List<IndicatorLayerWidget> widgets = new ArrayList<>();
    private final List<IndicatorLabelWidget> labelWidgets = new ArrayList<>();
    private IndicatorShadowEffect shadowEffect;
    private IndicatorConfiguration currentIndicator;
    private List<IndicatorLayerConfiguration> currentEnabledLayers;
    private Rectangle gradientArea;
    private Point gradientPoint;
    private final List<Point> currentTopLefts = new ArrayList<>();
    private final Map<String, LayerAnchor> anchorByLayerName = new HashMap<>();
    private IndicatorImage windowImage;
    private IndicatorConfiguration windowImageIndicator;
    private List<Point> windowImageOffsets;
    private double windowImageScale;
    private int maxIndicatorWindowSize;
    private boolean showing;
    private boolean cleared;

    /** Lazily creates the window and its widgets; the host styles winId() afterwards. */
    public TransparentWindow window() {
        if (window == null) {
            window = new TransparentWindow();
            layersWidget = new QWidget(window);
        }
        return window;
    }

    private IndicatorLayerWidget widget(int index) {
        if (index == widgets.size()) {
            widgets.add(new IndicatorLayerWidget(layersWidget));
            // Label widget is a child of window (not widget) so it renders on top
            // of the shadow effect and can clear/redraw the fill area.
            labelWidgets.add(new IndicatorLabelWidget(window));
        }
        return widgets.get(index);
    }

    /** Installing a graphics effect for the first time initializes Qt machinery that costs
     *  around 15ms, which the first mode to show an indicator would otherwise pay. */
    public void preWarm() {
        window();
        layersWidget.setGraphicsEffect(new IndicatorShadowEffect(this));
        layersWidget.setGraphicsEffect(null);
    }

    public boolean showing() {
        return showing;
    }

    public IndicatorConfiguration currentIndicator() {
        return currentIndicator;
    }

    private record LayerAnchor(Rectangle mouseRectangle, Point cursorVisualCenter,
                               Screen activeScreen) {
    }

    private static List<String> enabledLayerNames(IndicatorConfiguration indicator) {
        List<String> layerNames = new ArrayList<>();
        for (Map.Entry<String, IndicatorLayerConfiguration> entry : indicator.layerByName()
                                                                             .entrySet())
            if (entry.getValue().enabled())
                layerNames.add(entry.getKey());
        layerNames.sort(Comparator.comparingInt(
                layerName -> indicator.layerByName().get(layerName).z()));
        return layerNames;
    }

    private static List<IndicatorLayerConfiguration> enabledLayers(
            IndicatorConfiguration indicator) {
        List<IndicatorLayerConfiguration> layers = new ArrayList<>();
        for (String layerName : enabledLayerNames(indicator))
            layers.add(indicator.layerByName().get(layerName));
        return layers;
    }

    private void setGradientSampling(Rectangle mouseRectangle, Point cursorVisualCenter,
                                     Screen activeScreen) {
        gradientArea = activeScreen.rectangle();
        gradientPoint = new Point(mouseRectangle.x() + cursorVisualCenter.x(),
                mouseRectangle.y() + cursorVisualCenter.y());
    }

    private String hex(Color color, String lastSelectedHintBoxHexColor) {
        return sweep(color) == null && color instanceof GradientColor gradientColor &&
               gradientColor.gradient() ?
                Color.hexColor(gradientColor.rgbAt(gradientArea, gradientPoint.x(),
                        gradientPoint.y())) :
                color.hexColor(lastSelectedHintBoxHexColor);
    }

    /** The sweep is cached per extent, and a screen wide ramp shifted by less than this is
     *  not a different one. */
    private static final int sweepGrid = 64;

    private static int snapped(double coordinate) {
        return (int) Math.round(coordinate / sweepGrid) * sweepGrid;
    }

    private static GradientColor sweep(Color color) {
        return color instanceof GradientColor gradientColor && gradientColor.gradient() &&
               gradientColor.step() == GradientStep.PIXEL ? gradientColor : null;
    }

    /** The screen a sweep runs over, relative to the widget the brush spans. */
    private Rectangle sweepArea(Point topLeft) {
        return new Rectangle(snapped(gradientArea.x() - topLeft.x()),
                snapped(gradientArea.y() - topLeft.y()),
                gradientArea.width(), gradientArea.height());
    }

    private static QBrush brush(QColor color, GradientColor sweep, Rectangle sweepArea) {
        if (sweep == null)
            return QtColorUtil.qBrush(color);
        if (sweep.area() == GradientArea.ELEMENT)
            return QtColorUtil.qBrush(sweep, color.alphaF());
        return QtColorUtil.qBrush(sweep, color.alphaF(),
                sweep.direction().start(sweepArea), sweep.direction().end(sweepArea));
    }

    private double layerSize(IndicatorLayerConfiguration layer, double screenScale) {
        return layer.size() * screenScale;
    }

    private int layerSizeWithStroke(IndicatorLayerConfiguration layer, double screenScale) {
        // An odd size puts the center of a centered layer half a pixel off, so it would
        // shift as the size changes parity.
        int size = (int) Math.ceil(layerSize(layer, screenScale) +
                2 * IndicatorLayerWidget.strokePadding(
                        layer.stroke().thickness() * screenScale, layer.edgeCount()));
        return size + size % 2;
    }

    private int indicatorShadowPadding(Shadow shadow, double scale) {
        if (shadow.blurRadius() == 0)
            return 0;
        return (int) Math.ceil((shadow.blurRadius() +
                Math.max(Math.abs(shadow.horizontalOffset()),
                         Math.abs(shadow.verticalOffset()))) * scale);
    }


    /** Shows/updates the indicator: repositions and renders unless nothing changed. The
     *  overlay supplies the cursor rectangle, its visual center, and the active screen and
     *  zoom. */
    public void setIndicator(IndicatorConfiguration indicator, Set<String> layerNamesToAnchor,
                             Rectangle mouseRectangle, Point cursorVisualCenter,
                             Screen activeScreen, Zoom zoom, String lastSelectedHintBoxHexColor) {
        setGradientSampling(mouseRectangle, cursorVisualCenter, activeScreen);
        anchor(layerNamesToAnchor, mouseRectangle, cursorVisualCenter, activeScreen);
        if (showing && indicator.equals(currentIndicator) && layerNamesToAnchor.isEmpty())
            return;
        // Position the (hidden) window before showIndicator shows it.
        reposition(indicator, mouseRectangle, cursorVisualCenter, activeScreen, zoom);
        showIndicator(indicator, activeScreen.scale(), lastSelectedHintBoxHexColor);
    }

    /** Repositions/resizes the current indicator for the cursor, screen and zoom. */
    public void reposition(Rectangle mouseRectangle, Point cursorVisualCenter,
                           Screen activeScreen, Zoom zoom) {
        reposition(currentIndicator, mouseRectangle, cursorVisualCenter, activeScreen, zoom);
    }

    private void anchor(Set<String> layerNamesToAnchor, Rectangle mouseRectangle,
                        Point cursorVisualCenter, Screen activeScreen) {
        for (String layerName : layerNamesToAnchor)
            anchorByLayerName.put(layerName,
                    new LayerAnchor(mouseRectangle, cursorVisualCenter, activeScreen));
    }

    private List<Point> layerTopLefts(IndicatorConfiguration indicator,
                                      Rectangle mouseRectangle, Point cursorVisualCenter,
                                      Screen activeScreen, Zoom zoom) {
        // Screen pixels: the configured size does not change with the zoom. Only the
        // position does, because the cursor it marks is a desktop point.
        List<Point> topLefts = new ArrayList<>();
        for (String layerName : enabledLayerNames(indicator)) {
            IndicatorLayerConfiguration layer = indicator.layerByName().get(layerName);
            LayerAnchor anchor = layer.followMouse() ?
                    new LayerAnchor(mouseRectangle, cursorVisualCenter, activeScreen) :
                    anchorByLayerName.get(layerName);
            Point topLeft = layerTopLeft(anchor.mouseRectangle(), anchor.cursorVisualCenter(),
                    anchor.activeScreen(), zoom, layer,
                    layerSizeWithStroke(layer, activeScreen.scale()));
            topLefts.add(new Point(Math.round(topLeft.x()), Math.round(topLeft.y())));
        }
        return topLefts;
    }

    private void reposition(IndicatorConfiguration indicator, Rectangle mouseRectangle,
                            Point cursorVisualCenter, Screen activeScreen, Zoom zoom) {
        double screenScale = activeScreen.scale();
        List<IndicatorLayerConfiguration> enabledLayers = enabledLayers(indicator);
        List<Point> topLefts = layerTopLefts(indicator, mouseRectangle, cursorVisualCenter,
                activeScreen, zoom);
        Rectangle layersRectangle = layersRectangle(enabledLayers, topLefts, screenScale);
        // Never resize the window: the DWM compositor would show the old surface at the new
        // size for one frame, mispositioning the indicator. It fits the largest indicator drawn
        // so far; the extra area is transparent and the visible layers stay at their
        // top-lefts regardless.
        int windowSize = Math.max(layersRectangle.width(), layersRectangle.height()) +
                         2 * indicatorShadowPadding(indicator.shadow(), screenScale);
        maxIndicatorWindowSize = Math.max(maxIndicatorWindowSize, windowSize);
        windowSize = maxIndicatorWindowSize;
        int windowX = layersRectangle.x() + layersRectangle.width() / 2 - windowSize / 2;
        int windowY = layersRectangle.y() + layersRectangle.height() / 2 - windowSize / 2;
        window.moveAndResizeInPixels(activeScreen, windowX, windowY, windowSize, windowSize);
        placeLayers(enabledLayers, topLefts,
                new Point(layersRectangle.x(), layersRectangle.y()),
                layersRectangle.width(), layersRectangle.height(),
                new Point(windowX, windowY), screenScale, Os.macos ? activeScreen.scale() : 1);
    }

    private Rectangle layersRectangle(List<IndicatorLayerConfiguration> layers,
                                      List<Point> topLefts, double screenScale) {
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (int i = 0; i < layers.size(); i++) {
            int layerSizeWithStroke = layerSizeWithStroke(layers.get(i), screenScale);
            int x = (int) topLefts.get(i).x();
            int y = (int) topLefts.get(i).y();
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x + layerSizeWithStroke);
            bottom = Math.max(bottom, y + layerSizeWithStroke);
        }
        return new Rectangle(left, top, right - left, bottom - top);
    }

    private void placeLayers(List<IndicatorLayerConfiguration> layers, List<Point> topLefts,
                             Point layersTopLeft, int layersWidth, int layersHeight,
                             Point windowTopLeft, double screenScale,
                             double pointsPerPixel) {
        layersWidget.move(points(layersTopLeft.x() - windowTopLeft.x(), pointsPerPixel),
                points(layersTopLeft.y() - windowTopLeft.y(), pointsPerPixel));
        layersWidget.resize(points(layersWidth, pointsPerPixel),
                points(layersHeight, pointsPerPixel));
        currentTopLefts.clear();
        currentTopLefts.addAll(topLefts);
        for (int i = 0; i < layers.size(); i++) {
            IndicatorLayerConfiguration layer = layers.get(i);
            Point topLeft = topLefts.get(i);
            int layerSizeWithStroke =
                    points(layerSizeWithStroke(layer, screenScale), pointsPerPixel);
            IndicatorLayerWidget widget = widget(i);
            widget.setStrokeScale(screenScale);
            widget.setLayerSize(layerSize(layer, screenScale) / pointsPerPixel);
            widget.move(points(topLeft.x() - layersTopLeft.x(), pointsPerPixel),
                    points(topLeft.y() - layersTopLeft.y(), pointsPerPixel));
            widget.resize(layerSizeWithStroke, layerSizeWithStroke);
            IndicatorLabelWidget labelWidget = labelWidgets.get(i);
            labelWidget.move(points(topLeft.x() - windowTopLeft.x(), pointsPerPixel),
                    points(topLeft.y() - windowTopLeft.y(), pointsPerPixel));
            labelWidget.resize(layerSizeWithStroke, layerSizeWithStroke);
            labelWidget.setLayerSize(layerSize(layer, screenScale) / pointsPerPixel);
        }
    }

    private static int points(double pixels, double pointsPerPixel) {
        return (int) Math.round(pixels / pointsPerPixel);
    }

    private static final int layerEdgeThreshold = 100;

    /**
     * Returns the layer top-left position for the given layer size.
     * For CENTER, the layer is centered on the cursor's visual center.
     * For corner positions, the layer is placed in that corner relative to the cursor,
     * flipping to the opposite side when near the corresponding screen edge.
     */
    private Point layerTopLeft(Rectangle mouseRectangle, Point cursorVisualCenter,
                               Screen activeScreen, Zoom zoom, IndicatorLayerConfiguration layer,
                               int layerSizeWithStroke) {
        Rectangle screen = activeScreen.rectangle();
        if (layer.position() == IndicatorPosition.CENTER) {
            double centerX = mouseRectangle.x() + cursorVisualCenter.x();
            double centerY = mouseRectangle.y() + cursorVisualCenter.y();
            centerX = Math.max(screen.x(), Math.min(centerX,
                    screen.x() + screen.width()));
            centerY = Math.max(screen.y(), Math.min(centerY,
                    screen.y() + screen.height()));
            return new Point(zoomedX(centerX, zoom) - layerSizeWithStroke / 2.0,
                    zoomedY(centerY, zoom) - layerSizeWithStroke / 2.0);
        }
        int mouseX = Math.max(screen.x(), Math.min(mouseRectangle.x(),
                screen.x() + screen.width()));
        int mouseY = Math.max(screen.y(), Math.min(mouseRectangle.y(),
                screen.y() + screen.height()));
        IndicatorPosition position = layer.position();
        boolean defaultRight = position == IndicatorPosition.BOTTOM_RIGHT ||
                               position == IndicatorPosition.TOP_RIGHT;
        boolean defaultBottom = position == IndicatorPosition.BOTTOM_RIGHT ||
                                position == IndicatorPosition.BOTTOM_LEFT;
        boolean nearRightEdge = mouseX >=
                screen.x() + screen.width() - layerEdgeThreshold;
        boolean nearLeftEdge = mouseX <=
                screen.x() + layerEdgeThreshold;
        boolean placeRight = defaultRight ? !nearRightEdge : nearLeftEdge;
        int layerX = placeRight ?
                mouseX + mouseRectangle.width() / 2 : mouseX - layerSizeWithStroke;
        boolean nearBottomEdge = mouseY >=
                screen.y() + screen.height() - layerEdgeThreshold;
        boolean nearTopEdge = mouseY <=
                screen.y() + layerEdgeThreshold;
        boolean placeBottom = defaultBottom ? !nearBottomEdge : nearTopEdge;
        int layerY = placeBottom ?
                mouseY + mouseRectangle.height() / 2 : mouseY - layerSizeWithStroke;
        return new Point(zoomedX(layerX, zoom), zoomedY(layerY, zoom));
    }

    private static double zoomedX(double x, Zoom zoom) {
        return zoom == null ? x : zoom.zoomedX(x);
    }

    private static double zoomedY(double y, Zoom zoom) {
        return zoom == null ? y : zoom.zoomedY(y);
    }

    /** An offscreen-rendered indicator: premultiplied ARGB (0xAARRGGBB), row-major. */
    public record IndicatorImage(int[] argb, int width, int height) {}

    /** An image of the indicator, and where its top-left goes on the screen. */
    public record WindowImage(IndicatorImage image, int x, int y) {}

    /** Lays the indicator out at the cursor and renders it into an image the size of what it
     *  draws. When the layers keep their places inside the image, which they do when they all
     *  follow the mouse, and no color depends on where the indicator is, the last image is
     *  returned with its new screen position instead. */
    public WindowImage renderWindowImage(IndicatorConfiguration indicator,
                                         Set<String> layerNamesToAnchor,
                                         Rectangle mouseRectangle, Point cursorVisualCenter,
                                         Screen activeScreen, Zoom zoom,
                                         String lastSelectedHintBoxHexColor) {
        setGradientSampling(mouseRectangle, cursorVisualCenter, activeScreen);
        anchor(layerNamesToAnchor, mouseRectangle, cursorVisualCenter, activeScreen);
        double screenScale = activeScreen.scale();
        List<IndicatorLayerConfiguration> enabledLayers = enabledLayers(indicator);
        List<Point> topLefts = layerTopLefts(indicator, mouseRectangle, cursorVisualCenter,
                activeScreen, zoom);
        Rectangle layersRectangle = layersRectangle(enabledLayers, topLefts, screenScale);
        int shadowPadding = indicatorShadowPadding(indicator.shadow(), screenScale);
        int x = layersRectangle.x() - shadowPadding;
        int y = layersRectangle.y() - shadowPadding;
        List<Point> offsets = new ArrayList<>();
        for (Point topLeft : topLefts)
            offsets.add(new Point(topLeft.x() - x, topLeft.y() - y));
        if (indicator.equals(windowImageIndicator) && offsets.equals(windowImageOffsets) &&
            screenScale == windowImageScale && looksTheSameAnywhere(indicator))
            return new WindowImage(windowImage, x, y);
        int width = layersRectangle.width() + 2 * shadowPadding;
        int height = layersRectangle.height() + 2 * shadowPadding;
        window();
        window.resize(width, height);
        placeLayers(enabledLayers, topLefts,
                new Point(layersRectangle.x(), layersRectangle.y()),
                layersRectangle.width(), layersRectangle.height(), new Point(x, y),
                screenScale, 1);
        applyIndicator(indicator, screenScale, lastSelectedHintBoxHexColor);
        windowImage = render(width, height, screenScale);
        windowImageIndicator = indicator;
        windowImageOffsets = offsets;
        windowImageScale = screenScale;
        return new WindowImage(windowImage, x, y);
    }

    private static boolean looksTheSameAnywhere(IndicatorConfiguration indicator) {
        if (!looksTheSameAnywhere(indicator.shadow().color()))
            return false;
        for (IndicatorLayerConfiguration layer : enabledLayers(indicator)) {
            FontStyle labelFontStyle = layer.labelFontStyle();
            if (!looksTheSameAnywhere(layer.fillColor()) ||
                !looksTheSameAnywhere(layer.stroke().color()) ||
                !looksTheSameAnywhere(labelFontStyle.color()) ||
                !looksTheSameAnywhere(labelFontStyle.outlineColor()) ||
                !looksTheSameAnywhere(labelFontStyle.shadow().color()))
                return false;
        }
        return true;
    }

    /** A gradient is sampled at the cursor, or swept across the screen, unless it is swept
     *  across the element itself. */
    private static boolean looksTheSameAnywhere(Color color) {
        return !(color instanceof GradientColor gradientColor) || !gradientColor.gradient() ||
               gradientColor.step() == GradientStep.PIXEL &&
               gradientColor.area() == GradientArea.ELEMENT;
    }

    /** Renders the indicator's widget tree into a premultiplied-ARGB image for use as the
     *  system cursor, centered on the indicator's visual center. */
    public IndicatorImage renderCursorImage(IndicatorConfiguration indicator, double scale,
                                         String lastSelectedHintBoxHexColor,
                                         Rectangle mouseRectangle, Point cursorVisualCenter,
                                         Screen activeScreen) {
        setGradientSampling(mouseRectangle, cursorVisualCenter, activeScreen);
        List<IndicatorLayerConfiguration> enabledLayers = enabledLayers(indicator);
        int maxLayerSizeWithStroke = 0;
        for (IndicatorLayerConfiguration layer : enabledLayers)
            maxLayerSizeWithStroke = Math.max(maxLayerSizeWithStroke,
                    layerSizeWithStroke(layer, scale));
        int shadowPadding = indicatorShadowPadding(indicator.shadow(), scale);
        int imageSize = maxLayerSizeWithStroke + 2 * shadowPadding;
        List<Point> topLefts = new ArrayList<>();
        for (IndicatorLayerConfiguration layer : enabledLayers) {
            int layerSizeWithStroke = layerSizeWithStroke(layer, scale);
            topLefts.add(new Point(gradientPoint.x() - layerSizeWithStroke / 2.0,
                    gradientPoint.y() - layerSizeWithStroke / 2.0));
        }
        Point layersTopLeft = new Point(gradientPoint.x() - maxLayerSizeWithStroke / 2.0,
                gradientPoint.y() - maxLayerSizeWithStroke / 2.0);
        window();
        window.resize(imageSize, imageSize);
        placeLayers(enabledLayers, topLefts, layersTopLeft, maxLayerSizeWithStroke,
                maxLayerSizeWithStroke,
                new Point(layersTopLeft.x() - shadowPadding, layersTopLeft.y() - shadowPadding),
                scale, 1);
        applyIndicator(indicator, scale, lastSelectedHintBoxHexColor);
        return render(imageSize, imageSize, scale);
    }

    private IndicatorImage render(int width, int height, double scale) {
        QImage image = new QImage(width, height, QImage.Format.Format_ARGB32_Premultiplied);
        image.fill(0);
        // The label's point-size font resolves against the image's DPI; match the target
        // screen so it renders at the right size on any screen.
        HintMeshRenderer.setQImageDpiForScreen(image, scale);
        window.render(image);
        int[] argb = new int[width * height];
        ByteBuffer buffer = image.bits();
        buffer.position(0);
        buffer.order(ByteOrder.nativeOrder()).asIntBuffer().get(argb);
        image.dispose();
        return new IndicatorImage(argb, width, height);
    }

    /** Draws label text centered at (centerX, centerY): outline (if any) then fill, using the
     *  caller's already-sized font. Shared by the on-screen label widget and the cursor. */
    static void drawLabelText(QPainter painter, String text, QFont font, double centerX,
                              double centerY, int outlineThickness, QColor outlineColor,
                              QColor labelColor) {
        QFontMetrics fontMetrics = new QFontMetrics(font);
        int textX = (int) Math.round(centerX - fontMetrics.horizontalAdvance(text) / 2.0);
        QRect tightRect = fontMetrics.tightBoundingRect(text);
        int textY = (int) Math.round(centerY - tightRect.y() - tightRect.height() / 2.0);
        tightRect.dispose();
        fontMetrics.dispose();
        if (outlineThickness != 0 && outlineColor != null && outlineColor.alpha() != 0) {
            QPen outlinePen = new QPen(outlineColor);
            outlinePen.setWidth(outlineThickness);
            outlinePen.setJoinStyle(Qt.PenJoinStyle.RoundJoin);
            painter.setPen(outlinePen);
            painter.setBrush(Qt.BrushStyle.NoBrush);
            QPainterPath textPath = new QPainterPath();
            textPath.addText(textX, textY, font, text);
            painter.drawPath(textPath);
            textPath.dispose();
            outlinePen.dispose();
        }
        if (labelColor != null && labelColor.alpha() != 0) {
            painter.setPen(labelColor);
            painter.drawText(textX, textY, text);
        }
    }

    /** Applies the indicator to the widgets (shape, outlines, shadow effect, label) without
     *  showing or positioning. Shared by the on-screen path and the offscreen cursor render. */
    private void applyIndicator(IndicatorConfiguration indicator, double shadowScale,
                                String lastSelectedHintBoxHexColor) {
        currentIndicator = indicator;
        currentEnabledLayers = enabledLayers(indicator);
        cleared = false;
        applyShadowEffect(shadowScale, lastSelectedHintBoxHexColor);
        for (int i = 0; i < widgets.size(); i++) {
            if (i < currentEnabledLayers.size())
                applyLayer(currentEnabledLayers.get(i), widgets.get(i), labelWidgets.get(i),
                        currentTopLefts.get(i), shadowScale, lastSelectedHintBoxHexColor);
            else {
                widgets.get(i).hide();
                labelWidgets.get(i).hide();
            }
        }
    }

    private void applyLayer(IndicatorLayerConfiguration layer, IndicatorLayerWidget widget,
                            IndicatorLabelWidget labelWidget, Point topLeft,
                            double shadowScale, String lastSelectedHintBoxHexColor) {
        widget.setSweepArea(sweepArea(topLeft));
        widget.setEdgeCount(layer.edgeCount());
        widget.setFill(QtColorUtil.qColor(hex(layer.fillColor(), lastSelectedHintBoxHexColor), layer.fillOpacity()),
                sweep(layer.fillColor()));
        IndicatorStroke stroke = layer.stroke();
        widget.setStroke(stroke,
                QtColorUtil.qColor(hex(stroke.color(), lastSelectedHintBoxHexColor), stroke.opacity()),
                sweep(stroke.color()));
        widget.show();
        if (layer.labelEnabled() && layer.labelText() != null &&
            layer.labelFontStyle() != null) {
            FontStyle labelFontStyle = layer.labelFontStyle();
            QFont labelFont = QtHintFont.qFont(labelFontStyle.name(), labelFontStyle.size(), labelFontStyle.weight());
            QColor labelColor = QtColorUtil.qColor(hex(labelFontStyle.color(), lastSelectedHintBoxHexColor), labelFontStyle.opacity());
            QColor labelOutlineColor = QtColorUtil.qColor(hex(labelFontStyle.outlineColor(), lastSelectedHintBoxHexColor), labelFontStyle.outlineOpacity());
            labelWidget.setLabel(layer.labelText(), labelFont, labelColor,
                    (int) Math.round(labelFontStyle.outlineThickness()), labelOutlineColor,
                    layer.edgeCount());
            Shadow labelShadow = labelFontStyle.shadow();
            QColor labelShadowColor = QtColorUtil.qColor(hex(labelShadow.color(), lastSelectedHintBoxHexColor), labelShadow.opacity());
            if (labelShadowColor.alpha() != 0) {
                StackedShadowEffect effect = new StackedShadowEffect();
                effect.setBlurRadius(labelShadow.blurRadius() * shadowScale);
                effect.setOffset(labelShadow.horizontalOffset() * shadowScale,
                        labelShadow.verticalOffset() * shadowScale);
                effect.setColor(labelShadowColor);
                effect.setStackCount(labelShadow.stackCount());
                labelWidget.setGraphicsEffect(effect);
            }
            else {
                labelWidget.setGraphicsEffect(null);
            }
            labelShadowColor.dispose();
            labelWidget.show();
        }
        else {
            labelWidget.setLabel(null, null, null, 0, null, 0);
            labelWidget.setGraphicsEffect(null);
            labelWidget.hide();
        }
    }

    /** Applies the indicator, then shows the window. */
    private void showIndicator(IndicatorConfiguration indicator, double shadowScale,
                               String lastSelectedHintBoxHexColor) {
        applyIndicator(indicator, shadowScale, lastSelectedHintBoxHexColor);
        window.show();
        layersWidget.repaint();
        showing = true;
    }

    public void hide() {
        if (!showing)
            return;
        showing = false;
        // Paint the surface fully transparent before hiding.
        cleared = true;
        layersWidget.repaint();
        window.hide();
    }

    boolean indicatorHasTransparency() {
        for (IndicatorLayerConfiguration layer : currentEnabledLayers)
            if (layer.fillOpacity() < 1.0 ||
                layer.stroke().thickness() > 0 && layer.stroke().opacity() < 1.0)
                return true;
        return false;
    }

    private void applyShadowEffect(double scale, String lastSelectedHintBoxHexColor) {
        Shadow shadow = currentIndicator.shadow();
        QColor baseColor = QtColorUtil.qColor(hex(shadow.color(), lastSelectedHintBoxHexColor), 1.0);
        boolean hasShadow = shadow.opacity() > 0 && shadow.blurRadius() > 0;
        if (hasShadow) {
            // Reused rather than replaced: installing a graphics effect sets up Qt machinery
            // that costs milliseconds, which an animating indicator would pay every frame.
            IndicatorShadowEffect effect = reusableShadowEffect();
            effect.setTransparencyOnly(false);
            effect.setBlurRadius(shadow.blurRadius() * scale);
            effect.setOffset(shadow.horizontalOffset() * scale,
                    shadow.verticalOffset() * scale);
            int alpha = (int) Math.round(shadow.opacity() * 255);
            QColor shadowColor = new QColor(baseColor.red(), baseColor.green(),
                    baseColor.blue(), alpha);
            effect.setColor(shadowColor);
            GradientColor shadowSweep = sweep(shadow.color());
            effect.setShadowBrush(shadowSweep == null ? null :
                    brush(shadowColor, shadowSweep, sweepArea(layersTopLeft())));
            shadowColor.dispose();
            effect.setStackCount(shadow.stackCount());
            install(effect);
        }
        else if (indicatorHasTransparency()) {
            IndicatorShadowEffect effect = reusableShadowEffect();
            effect.setTransparencyOnly(true);
            install(effect);
        }
        else {
            shadowEffect = null;
            layersWidget.setGraphicsEffect(null);
        }
        baseColor.dispose();
    }

    private Point layersTopLeft() {
        double x = Double.MAX_VALUE;
        double y = Double.MAX_VALUE;
        for (Point topLeft : currentTopLefts) {
            x = Math.min(x, topLeft.x());
            y = Math.min(y, topLeft.y());
        }
        return new Point(x, y);
    }

    private IndicatorShadowEffect reusableShadowEffect() {
        return shadowEffect != null ? shadowEffect : new IndicatorShadowEffect(this);
    }

    private void install(IndicatorShadowEffect effect) {
        if (shadowEffect == effect)
            return;
        shadowEffect = effect;
        layersWidget.setGraphicsEffect(effect);
    }

    private class IndicatorLayerWidget extends QWidget {

        private double layerSize;
        private int edgeCount;
        private QColor fillColor;
        private GradientColor fillSweep;
        private IndicatorStroke stroke;
        private QColor strokeColor;
        private GradientColor strokeSweep;
        private Rectangle sweepArea;
        private double strokeScale;

        IndicatorLayerWidget(QWidget parent) {
            super(parent);
        }

        void setLayerSize(double layerSize) {
            this.layerSize = layerSize;
        }

        void setStrokeScale(double strokeScale) {
            this.strokeScale = strokeScale;
        }

        void setEdgeCount(int edgeCount) {
            this.edgeCount = edgeCount;
        }

        void setFill(QColor fillColor, GradientColor fillSweep) {
            if (this.fillColor != null)
                this.fillColor.dispose();
            this.fillColor = fillColor;
            this.fillSweep = fillSweep;
        }

        void setStroke(IndicatorStroke stroke, QColor strokeColor, GradientColor strokeSweep) {
            if (this.strokeColor != null)
                this.strokeColor.dispose();
            this.stroke = stroke;
            this.strokeColor = strokeColor;
            this.strokeSweep = strokeSweep;
        }

        /**
         * Axis-aligned padding needed around the shape's bounding box to fit a stroke
         * centered on its edge, miter tips included.
         * Projects the radial miter extension onto the x/y axes for each vertex
         * and returns the maximum.
         */
        static double strokePadding(double strokeThickness, int edgeCount) {
            double radial = strokeThickness / 2 / Math.cos(Math.PI / edgeCount);
            double startAngle = polygonStartAngle(edgeCount);
            double maxProjection = 0;
            for (int i = 0; i < edgeCount; i++) {
                double angle = startAngle + 2.0 * Math.PI * i / edgeCount;
                maxProjection = Math.max(maxProjection,
                        Math.max(Math.abs(Math.cos(angle)), Math.abs(Math.sin(angle))));
            }
            return radial * maxProjection;
        }

        private QBrush brush(QColor color, GradientColor sweep) {
            return IndicatorRenderer.brush(color, sweep, sweepArea);
        }

        void setSweepArea(Rectangle sweepArea) {
            this.sweepArea = sweepArea;
        }

        private static double polygonStartAngle(int edgeCount) {
            // Odd edge count: vertex at top (pointy top, e.g. triangle ▲).
            // Even edge count: flat edge at top (e.g. square □, hexagon ⬡).
            double startAngle = -Math.PI / 2;
            if (edgeCount % 2 == 0)
                startAngle += Math.PI / edgeCount;
            return startAngle;
        }

        /** Past this many edges the polygon is a circle, which Qt draws in one call. */
        private static final int circleEdgeCount = 100;

        static QPainterPath polygonPath(double centerX, double centerY,
                                       double radius, int edgeCount) {
            QPainterPath path = new QPainterPath();
            if (edgeCount >= circleEdgeCount) {
                path.addEllipse(centerX - radius, centerY - radius, 2 * radius, 2 * radius);
                return path;
            }
            double startAngle = polygonStartAngle(edgeCount);
            for (int i = 0; i < edgeCount; i++) {
                double angle = startAngle + 2.0 * Math.PI * i / edgeCount;
                double x = centerX + radius * Math.cos(angle);
                double y = centerY + radius * Math.sin(angle);
                if (i == 0)
                    path.moveTo(x, y);
                else
                    path.lineTo(x, y);
            }
            path.closeSubpath();
            return path;
        }

        // Returns the circumradius such that the polygon's bounding box
        // largest dimension equals targetSize, and the offset to center
        // the bounding box (the polygon's BB may not be symmetric around
        // the circumcenter, e.g. triangle).
        record PolygonLayout(double radius, double offsetX, double offsetY) {}

        static PolygonLayout polygonLayout(double targetSize, int edgeCount) {
            double startAngle = polygonStartAngle(edgeCount);
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (int i = 0; i < edgeCount; i++) {
                double angle = startAngle + 2.0 * Math.PI * i / edgeCount;
                double cx = Math.cos(angle);
                double cy = Math.sin(angle);
                minX = Math.min(minX, cx);
                maxX = Math.max(maxX, cx);
                minY = Math.min(minY, cy);
                maxY = Math.max(maxY, cy);
            }
            double maxDimension = Math.max(maxX - minX, maxY - minY);
            double radius = targetSize / maxDimension;
            double offsetX = -(minX + maxX) / 2.0 * radius;
            double offsetY = -(minY + maxY) / 2.0 * radius;
            return new PolygonLayout(radius, offsetX, offsetY);
        }

        /**
         * Builds an open path tracing a portion of the polygon outline.
         * startAngle: 0 = top (12 o'clock), increases clockwise, in degrees.
         * lengthPercent: a fraction of the perimeter, negative going counterclockwise.
         * anchor: MIDDLE = expand symmetrically from startAngle.
         */
        private static QPainterPath partialPolygonPath(double centerX, double centerY,
                                                       double radius, int edgeCount,
                                                       double startAngle, double lengthPercent,
                                                       StrokeAnchor anchor) {
            double polyStartAngle = polygonStartAngle(edgeCount);
            double[] vx = new double[edgeCount];
            double[] vy = new double[edgeCount];
            for (int i = 0; i < edgeCount; i++) {
                double angle = polyStartAngle + 2.0 * Math.PI * i / edgeCount;
                vx[i] = centerX + radius * Math.cos(angle);
                vy[i] = centerY + radius * Math.sin(angle);
            }
            double edgeLength = Math.hypot(vx[1] - vx[0], vy[1] - vy[0]);
            double totalLength = edgeCount * edgeLength;
            double fillLength = Math.abs(lengthPercent) * totalLength;
            // Convert startAngle (0=top, CW) to math angle for ray intersection.
            // Math convention: 0=right, counter-clockwise positive.
            // Screen coords: y increases downward, so sin is negated.
            double mathAngle = Math.toRadians(90 - startAngle);
            double rayDx = Math.cos(mathAngle);
            double rayDy = -Math.sin(mathAngle); // negate for screen coords
            // Find anchor position on perimeter by intersecting ray from center with polygon edges.
            double anchorPos = findAnchorPos(centerX, centerY, rayDx, rayDy,
                    vx, vy, edgeCount, edgeLength);
            // Build path(s) based on direction.
            // Vertex order is clockwise on screen. Forward = CW, backward = CCW.
            if (anchor == StrokeAnchor.MIDDLE) {
                double halfLength = fillLength / 2.0;
                QPainterPath cwPath = traceAlongPerimeter(
                        vx, vy, edgeCount, edgeLength, totalLength, anchorPos, halfLength, true);
                QPainterPath ccwPath = traceAlongPerimeter(
                        vx, vy, edgeCount, edgeLength, totalLength, anchorPos, halfLength, false);
                QPainterPath combined = ccwPath.toReversed();
                combined.connectPath(cwPath);
                cwPath.dispose();
                ccwPath.dispose();
                return combined;
            }
            else {
                boolean forward = lengthPercent > 0;
                return traceAlongPerimeter(
                        vx, vy, edgeCount, edgeLength, totalLength, anchorPos, fillLength, forward);
            }
        }

        /**
         * Finds the perimeter position (distance along polygon edges from vertex 0)
         * where a ray from center in direction (rayDx, rayDy) intersects the polygon.
         */
        private static double findAnchorPos(double centerX, double centerY,
                                            double rayDx, double rayDy,
                                            double[] vx, double[] vy,
                                            int edgeCount, double edgeLength) {
            double bestT = Double.MAX_VALUE;
            int bestEdge = 0;
            double bestFrac = 0;
            for (int i = 0; i < edgeCount; i++) {
                int j = (i + 1) % edgeCount;
                double ex = vx[j] - vx[i];
                double ey = vy[j] - vy[i];
                // Solve: center + t * ray = vertex[i] + s * edge
                double denom = rayDx * ey - rayDy * ex;
                if (Math.abs(denom) < 1e-12)
                    continue;
                double dx = vx[i] - centerX;
                double dy = vy[i] - centerY;
                double t = (dx * ey - dy * ex) / denom;
                double s = (dx * rayDy - dy * rayDx) / denom;
                if (t > 1e-9 && s >= -1e-9 && s <= 1 + 1e-9) {
                    if (t < bestT) {
                        bestT = t;
                        bestEdge = i;
                        bestFrac = Math.max(0, Math.min(1, s));
                    }
                }
            }
            return bestEdge * edgeLength + bestFrac * edgeLength;
        }

        /**
         * Traces a path along the polygon perimeter starting from anchorPos
         * for the given length, either forward (increasing vertex index) or
         * backward (decreasing vertex index).
         */
        private static QPainterPath traceAlongPerimeter(double[] vx, double[] vy,
                                                        int edgeCount, double edgeLength,
                                                        double totalLength, double anchorPos,
                                                        double length, boolean forward) {
            // Compute start point on the perimeter.
            double startPos = forward ? anchorPos : anchorPos;
            int startEdge = (int) (startPos / edgeLength);
            if (startEdge >= edgeCount)
                startEdge = edgeCount - 1;
            double startFrac = (startPos - startEdge * edgeLength) / edgeLength;
            startFrac = Math.max(0, Math.min(1, startFrac));
            int v0 = startEdge;
            int v1 = (startEdge + 1) % edgeCount;
            double sx = vx[v0] + startFrac * (vx[v1] - vx[v0]);
            double sy = vy[v0] + startFrac * (vy[v1] - vy[v0]);
            QPainterPath path = new QPainterPath();
            path.moveTo(sx, sy);
            double remaining = length;
            if (forward) {
                double distInCurrentEdge = (1 - startFrac) * edgeLength;
                int currentEdge = startEdge;
                while (remaining > 1e-6) {
                    int nextV = (currentEdge + 1) % edgeCount;
                    if (remaining >= distInCurrentEdge - 1e-6) {
                        path.lineTo(vx[nextV], vy[nextV]);
                        remaining -= distInCurrentEdge;
                        currentEdge = (currentEdge + 1) % edgeCount;
                        distInCurrentEdge = edgeLength;
                    }
                    else {
                        double frac = remaining / edgeLength;
                        int curV = currentEdge;
                        int nxtV = (currentEdge + 1) % edgeCount;
                        double ex = vx[curV] + frac * (vx[nxtV] - vx[curV]);
                        double ey = vy[curV] + frac * (vy[nxtV] - vy[curV]);
                        path.lineTo(ex, ey);
                        remaining = 0;
                    }
                }
            }
            else {
                // Backward: traverse edges in decreasing index order.
                double distInCurrentEdge = startFrac * edgeLength;
                int currentEdge = startEdge;
                while (remaining > 1e-6) {
                    int curV = currentEdge;
                    if (remaining >= distInCurrentEdge - 1e-6) {
                        path.lineTo(vx[curV], vy[curV]);
                        remaining -= distInCurrentEdge;
                        currentEdge = (currentEdge - 1 + edgeCount) % edgeCount;
                        distInCurrentEdge = edgeLength;
                    }
                    else {
                        int nextV = (currentEdge + 1) % edgeCount;
                        double frac = 1.0 - remaining / edgeLength;
                        double ex = vx[curV] + frac * (vx[nextV] - vx[curV]);
                        double ey = vy[curV] + frac * (vy[nextV] - vy[curV]);
                        path.lineTo(ex, ey);
                        remaining = 0;
                    }
                }
            }
            return path;
        }

        void drawContent(QPainter painter, QColor fillColor, QColor strokeColor) {
            PolygonLayout layout = polygonLayout(layerSize, edgeCount);
            double centerX = width() / 2.0 + layout.offsetX;
            double centerY = height() / 2.0 + layout.offsetY;
            QPainterPath path = polygonPath(centerX, centerY, layout.radius, edgeCount);
            if (fillColor.alpha() != 0) {
                painter.setPen(Qt.PenStyle.NoPen);
                painter.setBrush(brush(fillColor, fillSweep));
                painter.drawPath(path);
            }
            double strokeThickness = stroke.thickness() * strokeScale;
            if (strokeThickness > 0 && strokeColor.alpha() != 0 && stroke.lengthPercent() != 0) {
                QPen pen = new QPen(strokeColor);
                if (strokeSweep != null)
                    pen.setBrush(brush(strokeColor, strokeSweep));
                pen.setWidthF(strokeThickness);
                pen.setJoinStyle(Qt.PenJoinStyle.MiterJoin);
                painter.setBrush(Qt.BrushStyle.NoBrush);
                if (Math.abs(stroke.lengthPercent()) >= 1) {
                    painter.setPen(pen);
                    painter.drawPath(path);
                }
                else {
                    pen.setCapStyle(Qt.PenCapStyle.FlatCap);
                    painter.setPen(pen);
                    QPainterPath strokePath = partialPolygonPath(centerX, centerY,
                            layout.radius, edgeCount, stroke.startAngle(), stroke.lengthPercent(),
                            stroke.anchor());
                    painter.drawPath(strokePath);
                    strokePath.dispose();
                }
                pen.dispose();
            }
            path.dispose();
        }

        void redrawSourceOverShadow(QPainter painter) {
            painter.translate(x(), y());
            drawContent(painter, fillColor, strokeColor);
            painter.translate(-x(), -y());
        }

        private static QColor opaque(QColor color) {
            return color.alpha() == 0 ? new QColor(0, 0, 0, 0) :
                    new QColor(color.red(), color.green(), color.blue());
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            QPainter painter = new QPainter(this);
            if (cleared) {
                // Paint fully transparent so DWM's cached surface is blank.
                painter.setCompositionMode(
                        QPainter.CompositionMode.CompositionMode_Clear);
                QRect r = rect();
                QColor c = new QColor(0, 0, 0, 0);
                painter.fillRect(r, c);
                r.dispose();
                c.dispose();
                painter.end();
                painter.dispose();
                return;
            }
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            QColor opaqueFillColor = opaque(fillColor);
            QColor opaqueStrokeColor = opaque(strokeColor);
            drawContent(painter, opaqueFillColor, opaqueStrokeColor);
            opaqueFillColor.dispose();
            opaqueStrokeColor.dispose();
            painter.end();
            painter.dispose();
        }
    }

    public static class IndicatorShadowEffect extends StackedShadowEffect {

        private final IndicatorRenderer renderer;

        IndicatorShadowEffect(IndicatorRenderer renderer) {
            this.renderer = renderer;
        }

        @Override
        protected void draw(QPainter painter) {
            if (renderer.cleared) {
                // Just draw the source (triggers paintEvent which clears).
                // Skip shadow and redrawSourceOverShadow.
                drawSource(painter);
                return;
            }
            super.draw(painter);
        }

        @Override
        protected void redrawSourceOverShadow(QPainter painter) {
            if (!renderer.indicatorHasTransparency())
                return;
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            List<IndicatorLayerWidget> widgets =
                    renderer.widgets.subList(0, renderer.currentEnabledLayers.size());
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Clear);
            for (IndicatorLayerWidget widget : widgets)
                widget.redrawSourceOverShadow(painter);
            painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
            for (IndicatorLayerWidget widget : widgets)
                widget.redrawSourceOverShadow(painter);
        }
    }


    private class IndicatorLabelWidget extends QWidget {

        private String labelText;
        private QFont labelFont;
        private QColor labelColor;
        private int outlineThickness;
        private QColor outlineColor;
        private int edgeCount;
        private double layerSize;

        IndicatorLabelWidget(QWidget parent) {
            super(parent);
            setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents);
        }

        void setLayerSize(double layerSize) {
            this.layerSize = layerSize;
        }

        void setLabel(String labelText, QFont labelFont, QColor labelColor,
                      int outlineThickness, QColor outlineColor,
                      int edgeCount) {
            if (this.labelFont != null)
                this.labelFont.dispose();
            if (this.labelColor != null)
                this.labelColor.dispose();
            if (this.outlineColor != null)
                this.outlineColor.dispose();
            this.labelText = labelText;
            this.labelFont = labelFont;
            this.labelColor = labelColor;
            this.outlineThickness = outlineThickness;
            this.outlineColor = outlineColor;
            this.edgeCount = edgeCount;
            update();
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            if (labelText == null || labelFont == null || labelColor == null)
                return;
            QPainter painter = new QPainter(this);
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            painter.setFont(labelFont);
            IndicatorLayerWidget.PolygonLayout polygonLayout =
                    IndicatorLayerWidget.polygonLayout(layerSize, edgeCount);
            double centerX = width() / 2.0 + polygonLayout.offsetX();
            double centerY = height() / 2.0 + polygonLayout.offsetY();
            drawLabelText(painter, labelText, labelFont, centerX, centerY,
                    outlineThickness, outlineColor, labelColor);
            painter.end();
            painter.dispose();
        }
    }

}
