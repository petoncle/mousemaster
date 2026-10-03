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
import java.util.Collections;
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
        // shift as the size changes parity. A miter reaches as far as the stroke is thick,
        // Qt's default miter limit.
        int size = (int) Math.ceil(layerSize(layer, screenScale) +
                2 * layer.stroke().thickness() * screenScale);
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

    /** Repositions the current indicator for the cursor, screen and zoom. */
    public void reposition(Rectangle mouseRectangle, Point cursorVisualCenter,
                           Screen activeScreen, Zoom zoom, String lastSelectedHintBoxHexColor) {
        setGradientSampling(mouseRectangle, cursorVisualCenter, activeScreen);
        reposition(currentIndicator, mouseRectangle, cursorVisualCenter, activeScreen, zoom);
        if (!looksTheSameAnywhere(currentIndicator))
            showIndicator(currentIndicator, activeScreen.scale(), lastSelectedHintBoxHexColor);
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
        // The window covers the screen and stays put: Qt moves a window at once but repaints
        // it later, so a layer that does not follow the mouse would shake inside a moving one.
        window.coverInPixels(activeScreen);
        Rectangle screen = activeScreen.rectangle();
        placeLayers(enabledLayers, topLefts,
                new Point(layersRectangle.x(), layersRectangle.y()),
                layersRectangle.width(), layersRectangle.height(),
                new Point(screen.x(), screen.y()), screenScale,
                Os.macos ? activeScreen.scale() : 1);
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
        widget.setShape(layer.shape(), layer.aspectRatio(), layer.borderRadius(),
                layer.points());
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
                    (int) Math.round(labelFontStyle.outlineThickness()), labelOutlineColor);
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
            labelWidget.setLabel(null, null, null, 0, null);
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
        private IndicatorShape shape;
        private double aspectRatio;
        private double borderRadius;
        private List<Point> points;
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

        void setShape(IndicatorShape shape, double aspectRatio, double borderRadius,
                      List<Point> points) {
            this.shape = shape;
            this.aspectRatio = aspectRatio;
            this.borderRadius = borderRadius;
            this.points = points;
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

        private QBrush brush(QColor color, GradientColor sweep) {
            return IndicatorRenderer.brush(color, sweep, sweepArea);
        }

        void setSweepArea(Rectangle sweepArea) {
            this.sweepArea = sweepArea;
        }

        private static final List<Point> trianglePoints =
                List.of(new Point(0.5, 0), new Point(1, 1), new Point(0, 1));

        private static final List<Point> starPoints = starPoints();

        private static List<Point> starPoints() {
            List<Point> starPoints = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                double radius = i % 2 == 0 ? 1 : 0.4;
                double angle = -Math.PI / 2 + Math.PI * i / 5;
                starPoints.add(new Point(radius * Math.cos(angle), radius * Math.sin(angle)));
            }
            return List.copyOf(starPoints);
        }

        /** The shape fills a box centered in the widget: size is the box's largest dimension
         *  and aspectRatio its width over its height. */
        private QPainterPath shapePath() {
            double boxWidth = aspectRatio >= 1 ? layerSize : layerSize * aspectRatio;
            double boxHeight = aspectRatio >= 1 ? layerSize / aspectRatio : layerSize;
            double left = (width() - boxWidth) / 2;
            double top = (height() - boxHeight) / 2;
            QPainterPath path = new QPainterPath();
            switch (shape) {
                case CIRCLE -> path.addEllipse(left, top, boxWidth, boxHeight);
                case RECTANGLE -> path.addRoundedRect(left, top, boxWidth, boxHeight,
                        borderRadius * strokeScale, borderRadius * strokeScale,
                        Qt.SizeMode.AbsoluteSize);
                case TRIANGLE -> addPolygon(path, trianglePoints, left, top, boxWidth, boxHeight);
                case STAR -> addPolygon(path, starPoints, left, top, boxWidth, boxHeight);
                case PATH -> addPolygon(path, points, left, top, boxWidth, boxHeight);
                case LINE -> {
                    path.moveTo(left, top + boxHeight / 2);
                    path.lineTo(left + boxWidth, top + boxHeight / 2);
                }
                case CROSS -> {
                    path.moveTo(left, top);
                    path.lineTo(left + boxWidth, top + boxHeight);
                    path.moveTo(left + boxWidth, top);
                    path.lineTo(left, top + boxHeight);
                }
            }
            return path;
        }

        /** Moves and stretches the points so that the rectangle around them fills the shape's
         *  box: points can be written in any units. */
        private static void addPolygon(QPainterPath path, List<Point> points, double left,
                                       double top, double width, double height) {
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (Point point : points) {
                minX = Math.min(minX, point.x());
                maxX = Math.max(maxX, point.x());
                minY = Math.min(minY, point.y());
                maxY = Math.max(maxY, point.y());
            }
            for (int i = 0; i < points.size(); i++) {
                double x = left + (points.get(i).x() - minX) / (maxX - minX) * width;
                double y = top + (points.get(i).y() - minY) / (maxY - minY) * height;
                if (i == 0)
                    path.moveTo(x, y);
                else
                    path.lineTo(x, y);
            }
            path.closeSubpath();
        }

        /**
         * Builds an open path tracing a portion of a closed shape's outline.
         * startAngle: 0 = top (12 o'clock), increases clockwise, in degrees.
         * lengthPercent: a fraction of the perimeter, negative going counterclockwise.
         * anchor: MIDDLE = expand symmetrically from startAngle.
         */
        private static QPainterPath partialPath(QPainterPath shapePath, double centerX,
                                                double centerY, double startAngle,
                                                double lengthPercent, StrokeAnchor anchor) {
            QTransform identity = new QTransform();
            QPolygonF polygon = shapePath.toFillPolygon(identity);
            identity.dispose();
            // A closed polygon: the last point repeats the first.
            List<Point> vertices = new ArrayList<>();
            double twiceSignedArea = 0;
            for (QPointF point : polygon) {
                vertices.add(new Point(point.x(), point.y()));
                if (vertices.size() > 1) {
                    Point previous = vertices.get(vertices.size() - 2);
                    twiceSignedArea += previous.x() * point.y() - point.x() * previous.y();
                }
            }
            polygon.dispose();
            // Clockwise on screen, so that a positive length goes clockwise whatever order a
            // path's points come in.
            if (twiceSignedArea < 0)
                Collections.reverse(vertices);
            double[] positions = new double[vertices.size()];
            for (int i = 1; i < vertices.size(); i++)
                positions[i] = positions[i - 1] + Math.hypot(
                        vertices.get(i).x() - vertices.get(i - 1).x(),
                        vertices.get(i).y() - vertices.get(i - 1).y());
            double totalLength = positions[vertices.size() - 1];
            // startAngle goes clockwise from 12 o'clock, a math angle counterclockwise from
            // 3 o'clock, and y grows downward on screen.
            double mathAngle = Math.toRadians(90 - startAngle);
            double anchorPosition = findAnchorPosition(centerX, centerY, Math.cos(mathAngle),
                    -Math.sin(mathAngle), vertices, positions);
            double length = Math.abs(lengthPercent) * totalLength;
            double start = anchor == StrokeAnchor.MIDDLE ? anchorPosition - length / 2 :
                    lengthPercent > 0 ? anchorPosition : anchorPosition - length;
            return tracePerimeter(vertices, positions, start, length);
        }

        /**
         * Finds the perimeter position (distance along the polygon's edges from vertex 0)
         * where a ray from center in direction (rayX, rayY) intersects the polygon.
         */
        private static double findAnchorPosition(double centerX, double centerY,
                                                 double rayX, double rayY,
                                                 List<Point> vertices, double[] positions) {
            double bestRayDistance = Double.MAX_VALUE;
            double bestPosition = 0;
            for (int i = 0; i < vertices.size() - 1; i++) {
                double edgeX = vertices.get(i + 1).x() - vertices.get(i).x();
                double edgeY = vertices.get(i + 1).y() - vertices.get(i).y();
                // Solve: center + rayDistance * ray = vertex + edgeFraction * edge
                double denominator = rayX * edgeY - rayY * edgeX;
                if (Math.abs(denominator) < 1e-12)
                    continue;
                double centerToVertexX = vertices.get(i).x() - centerX;
                double centerToVertexY = vertices.get(i).y() - centerY;
                double rayDistance =
                        (centerToVertexX * edgeY - centerToVertexY * edgeX) / denominator;
                double edgeFraction =
                        (centerToVertexX * rayY - centerToVertexY * rayX) / denominator;
                if (rayDistance > 1e-9 && edgeFraction >= -1e-9 && edgeFraction <= 1 + 1e-9 &&
                    rayDistance < bestRayDistance) {
                    bestRayDistance = rayDistance;
                    bestPosition = positions[i] + Math.max(0, Math.min(1, edgeFraction)) *
                                                  (positions[i + 1] - positions[i]);
                }
            }
            return bestPosition;
        }

        /** Traces the outline clockwise from a perimeter position over a length, going around
         *  past vertex 0 as needed. */
        private static QPainterPath tracePerimeter(List<Point> vertices, double[] positions,
                                                   double start, double length) {
            double totalLength = positions[positions.length - 1];
            double end = start + length;
            QPainterPath path = new QPainterPath();
            Point startPoint = perimeterPoint(vertices, positions, start);
            path.moveTo(startPoint.x(), startPoint.y());
            for (double lap = Math.floor(start / totalLength) * totalLength; lap < end;
                 lap += totalLength) {
                for (int i = 0; i < positions.length - 1; i++) {
                    double position = lap + positions[i];
                    if (position > start && position < end)
                        path.lineTo(vertices.get(i).x(), vertices.get(i).y());
                }
            }
            Point endPoint = perimeterPoint(vertices, positions, end);
            path.lineTo(endPoint.x(), endPoint.y());
            return path;
        }

        private static Point perimeterPoint(List<Point> vertices, double[] positions,
                                            double position) {
            double totalLength = positions[positions.length - 1];
            double wrapped = (position % totalLength + totalLength) % totalLength;
            int i = 0;
            while (i < positions.length - 2 && positions[i + 1] <= wrapped)
                i++;
            double fraction = (wrapped - positions[i]) / (positions[i + 1] - positions[i]);
            Point from = vertices.get(i);
            Point to = vertices.get(i + 1);
            return new Point(from.x() + fraction * (to.x() - from.x()),
                    from.y() + fraction * (to.y() - from.y()));
        }

        void drawContent(QPainter painter, QColor fillColor, QColor strokeColor) {
            QPainterPath path = shapePath();
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
                if (Math.abs(stroke.lengthPercent()) >= 1 || !shape.closed()) {
                    painter.setPen(pen);
                    painter.drawPath(path);
                }
                else {
                    pen.setCapStyle(Qt.PenCapStyle.FlatCap);
                    painter.setPen(pen);
                    QPainterPath strokePath = partialPath(path, width() / 2.0, height() / 2.0,
                            stroke.startAngle(), stroke.lengthPercent(), stroke.anchor());
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

        IndicatorLabelWidget(QWidget parent) {
            super(parent);
            setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents);
        }

        void setLabel(String labelText, QFont labelFont, QColor labelColor,
                      int outlineThickness, QColor outlineColor) {
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
            update();
        }

        @Override
        protected void paintEvent(QPaintEvent event) {
            if (labelText == null || labelFont == null || labelColor == null)
                return;
            QPainter painter = new QPainter(this);
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            painter.setFont(labelFont);
            drawLabelText(painter, labelText, labelFont, width() / 2.0, height() / 2.0,
                    outlineThickness, outlineColor, labelColor);
            painter.end();
            painter.dispose();
        }
    }

}
