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
import java.util.List;
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

    private static List<IndicatorLayerConfiguration> enabledLayers(
            IndicatorConfiguration indicator) {
        List<IndicatorLayerConfiguration> layers = new ArrayList<>();
        for (IndicatorLayerConfiguration layer : indicator.layerByName().values())
            if (layer.enabled())
                layers.add(layer);
        layers.sort(Comparator.comparingInt(IndicatorLayerConfiguration::z));
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

    private int layerSize(IndicatorLayerConfiguration layer, double screenScale) {
        // An odd size puts the center of a centered layer half a pixel off, so it would
        // shift as the size changes parity.
        int size = (int) Math.floor(layer.size() * screenScale);
        return size - size % 2;
    }

    private int layerOutlinePadding(IndicatorLayerConfiguration layer, double screenScale) {
        double scaled = Math.max(
                layer.outerOutline().thickness(),
                layer.innerOutline().thickness()) * screenScale;
        return (int) Math.ceil(IndicatorLayerWidget.miterPadding(scaled, layer.edgeCount()));
    }

    private int layerSizeWithOutlines(IndicatorLayerConfiguration layer, double screenScale) {
        return layerSize(layer, screenScale) +
               2 * layerOutlinePadding(layer, screenScale);
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
    public void setIndicator(IndicatorConfiguration indicator,
                             Rectangle mouseRectangle, Point cursorVisualCenter,
                             Screen activeScreen, Zoom zoom, String lastSelectedHintBoxHexColor) {
        setGradientSampling(mouseRectangle, cursorVisualCenter, activeScreen);
        if (showing && indicator.equals(currentIndicator))
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

    private void reposition(IndicatorConfiguration indicator, Rectangle mouseRectangle,
                            Point cursorVisualCenter, Screen activeScreen, Zoom zoom) {
        double screenScale = activeScreen.scale();
        List<IndicatorLayerConfiguration> enabledLayers = enabledLayers(indicator);
        // Screen pixels: the configured size does not change with the zoom. Only the
        // position does, because the cursor it marks is a desktop point.
        List<Point> topLefts = new ArrayList<>();
        for (IndicatorLayerConfiguration layer : enabledLayers) {
            Point topLeft = layerTopLeft(mouseRectangle, cursorVisualCenter, activeScreen,
                    zoom, layer, layerSizeWithOutlines(layer, screenScale));
            topLefts.add(new Point(Math.round(topLeft.x()), Math.round(topLeft.y())));
        }
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
            int layerSizeWithOutlines = layerSizeWithOutlines(layers.get(i), screenScale);
            int x = (int) topLefts.get(i).x();
            int y = (int) topLefts.get(i).y();
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x + layerSizeWithOutlines);
            bottom = Math.max(bottom, y + layerSizeWithOutlines);
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
            int layerSizeWithOutlines =
                    points(layerSizeWithOutlines(layer, screenScale), pointsPerPixel);
            IndicatorLayerWidget widget = widget(i);
            widget.setOutlineScale(screenScale);
            widget.move(points(topLeft.x() - layersTopLeft.x(), pointsPerPixel),
                    points(topLeft.y() - layersTopLeft.y(), pointsPerPixel));
            widget.resize(layerSizeWithOutlines, layerSizeWithOutlines);
            IndicatorLabelWidget labelWidget = labelWidgets.get(i);
            labelWidget.move(points(topLeft.x() - windowTopLeft.x(), pointsPerPixel),
                    points(topLeft.y() - windowTopLeft.y(), pointsPerPixel));
            labelWidget.resize(layerSizeWithOutlines, layerSizeWithOutlines);
            labelWidget.setLayerOutlinePadding(
                    points(layerOutlinePadding(layer, screenScale), pointsPerPixel));
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
                               int layerSizeWithOutlines) {
        Rectangle screen = activeScreen.rectangle();
        if (layer.position() == IndicatorPosition.CENTER) {
            double centerX = mouseRectangle.x() + cursorVisualCenter.x();
            double centerY = mouseRectangle.y() + cursorVisualCenter.y();
            centerX = Math.max(screen.x(), Math.min(centerX,
                    screen.x() + screen.width()));
            centerY = Math.max(screen.y(), Math.min(centerY,
                    screen.y() + screen.height()));
            return new Point(zoomedX(centerX, zoom) - layerSizeWithOutlines / 2.0,
                    zoomedY(centerY, zoom) - layerSizeWithOutlines / 2.0);
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
                mouseX + mouseRectangle.width() / 2 : mouseX - layerSizeWithOutlines;
        boolean nearBottomEdge = mouseY >=
                screen.y() + screen.height() - layerEdgeThreshold;
        boolean nearTopEdge = mouseY <=
                screen.y() + layerEdgeThreshold;
        boolean placeBottom = defaultBottom ? !nearBottomEdge : nearTopEdge;
        int layerY = placeBottom ?
                mouseY + mouseRectangle.height() / 2 : mouseY - layerSizeWithOutlines;
        return new Point(zoomedX(layerX, zoom), zoomedY(layerY, zoom));
    }

    private static double zoomedX(double x, Zoom zoom) {
        return zoom == null ? x : zoom.zoomedX(x);
    }

    private static double zoomedY(double y, Zoom zoom) {
        return zoom == null ? y : zoom.zoomedY(y);
    }

    /** An offscreen-rendered indicator: premultiplied ARGB (0xAARRGGBB), row-major. */
    public record CursorImage(int[] argb, int width, int height) {}

    /** Renders the indicator's widget tree into a premultiplied-ARGB image for use as the
     *  system cursor, centered on the indicator's visual center. */
    public CursorImage renderCursorImage(IndicatorConfiguration indicator, double scale,
                                         String lastSelectedHintBoxHexColor,
                                         Rectangle mouseRectangle, Point cursorVisualCenter,
                                         Screen activeScreen) {
        setGradientSampling(mouseRectangle, cursorVisualCenter, activeScreen);
        List<IndicatorLayerConfiguration> enabledLayers = enabledLayers(indicator);
        int maxLayerSize = 0;
        int maxLayerSizeWithOutlines = 0;
        for (IndicatorLayerConfiguration layer : enabledLayers) {
            maxLayerSize = Math.max(maxLayerSize, layerSize(layer, scale));
            maxLayerSizeWithOutlines = Math.max(maxLayerSizeWithOutlines,
                    layerSizeWithOutlines(layer, scale));
        }
        if (maxLayerSize <= 0)
            return null;
        int shadowPadding = indicatorShadowPadding(indicator.shadow(), scale);
        int imageSize = maxLayerSizeWithOutlines + 2 * shadowPadding;
        List<Point> topLefts = new ArrayList<>();
        for (IndicatorLayerConfiguration layer : enabledLayers) {
            int layerSizeWithOutlines = layerSizeWithOutlines(layer, scale);
            topLefts.add(new Point(gradientPoint.x() - layerSizeWithOutlines / 2.0,
                    gradientPoint.y() - layerSizeWithOutlines / 2.0));
        }
        Point layersTopLeft = new Point(gradientPoint.x() - maxLayerSizeWithOutlines / 2.0,
                gradientPoint.y() - maxLayerSizeWithOutlines / 2.0);
        window();
        window.resize(imageSize, imageSize);
        placeLayers(enabledLayers, topLefts, layersTopLeft, maxLayerSizeWithOutlines,
                maxLayerSizeWithOutlines,
                new Point(layersTopLeft.x() - shadowPadding, layersTopLeft.y() - shadowPadding),
                scale, 1);
        applyIndicator(indicator, scale, lastSelectedHintBoxHexColor);
        QImage image = new QImage(imageSize, imageSize,
                QImage.Format.Format_ARGB32_Premultiplied);
        image.fill(0);
        // The label's point-size font resolves against the image's DPI; match the target
        // screen so it renders at the right size on any screen.
        HintMeshRenderer.setQImageDpiForScreen(image, scale);
        window.render(image);
        int[] argb = new int[imageSize * imageSize];
        ByteBuffer buffer = image.bits();
        buffer.position(0);
        buffer.order(ByteOrder.nativeOrder()).asIntBuffer().get(argb);
        image.dispose();
        return new CursorImage(argb, imageSize, imageSize);
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
        widget.setColor(QtColorUtil.qColor(hex(layer.color(), lastSelectedHintBoxHexColor), layer.opacity()),
                sweep(layer.color()));
        IndicatorOutline outer = layer.outerOutline();
        IndicatorOutline inner = layer.innerOutline();
        widget.setOutlines(
                outer.thickness(),
                QtColorUtil.qColor(hex(outer.color(), lastSelectedHintBoxHexColor), outer.opacity()),
                sweep(outer.color()),
                outer.fillPercent(),
                outer.fillStartAngle(),
                outer.fillDirection(),
                inner.thickness(),
                QtColorUtil.qColor(hex(inner.color(), lastSelectedHintBoxHexColor), inner.opacity()),
                sweep(inner.color()),
                inner.fillPercent(),
                inner.fillStartAngle(),
                inner.fillDirection());
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
        for (IndicatorLayerConfiguration layer : currentEnabledLayers) {
            IndicatorOutline outer = layer.outerOutline();
            IndicatorOutline inner = layer.innerOutline();
            if (layer.opacity() < 1.0 ||
                outer.thickness() > 0 && outer.opacity() < 1.0 ||
                inner.thickness() > 0 && inner.opacity() < 1.0)
                return true;
        }
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

        private QColor color;
        private int edgeCount;
        private double outerOutlineThickness;
        private QColor outerOutlineColor;
        private double outerOutlineFillPercent;
        private double outerOutlineFillStartAngle;
        private FillDirection outerOutlineFillDirection;
        private double innerOutlineThickness;
        private QColor innerOutlineColor;
        private GradientColor sweep;
        private GradientColor outerOutlineSweep;
        private GradientColor innerOutlineSweep;
        private Rectangle sweepArea;
        private double innerOutlineFillPercent;
        private double innerOutlineFillStartAngle;
        private FillDirection innerOutlineFillDirection;
        private double outlineScale;

        IndicatorLayerWidget(QWidget parent) {
            super(parent);
        }

        void setOutlineScale(double outlineScale) {
            this.outlineScale = outlineScale;
        }

        void setColor(QColor color, GradientColor sweep) {
            if (this.color != null)
                this.color.dispose();
            this.color = color;
            this.sweep = sweep;
        }

        void setEdgeCount(int edgeCount) {
            this.edgeCount = edgeCount;
        }

        void setOutlines(double outerOutlineThickness, QColor outerOutlineColor,
                         GradientColor outerOutlineSweep,
                         double outerOutlineFillPercent,
                         double outerOutlineFillStartAngle,
                         FillDirection outerOutlineFillDirection,
                         double innerOutlineThickness, QColor innerOutlineColor,
                         GradientColor innerOutlineSweep,
                         double innerOutlineFillPercent,
                         double innerOutlineFillStartAngle,
                         FillDirection innerOutlineFillDirection) {
            if (this.outerOutlineColor != null)
                this.outerOutlineColor.dispose();
            if (this.innerOutlineColor != null)
                this.innerOutlineColor.dispose();
            this.outerOutlineThickness = outerOutlineThickness;
            this.outerOutlineColor = outerOutlineColor;
            this.outerOutlineSweep = outerOutlineSweep;
            this.outerOutlineFillPercent = outerOutlineFillPercent;
            this.outerOutlineFillStartAngle = outerOutlineFillStartAngle;
            this.outerOutlineFillDirection = outerOutlineFillDirection;
            this.innerOutlineThickness = innerOutlineThickness;
            this.innerOutlineColor = innerOutlineColor;
            this.innerOutlineSweep = innerOutlineSweep;
            this.innerOutlineFillPercent = innerOutlineFillPercent;
            this.innerOutlineFillStartAngle = innerOutlineFillStartAngle;
            this.innerOutlineFillDirection = innerOutlineFillDirection;
        }

        /**
         * Radial distance from a fill vertex to the outline's miter tip,
         * measured along the circumradius direction.
         * The pen center path is at fillRadius + (corrected - 1) / 2 from center
         * (1 = inward overlap). For a regular n-gon, offsetting edges outward by
         * penWidth/2 gives a circumradius of R + penWidth / (2*cos(pi/n)),
         * where penWidth = corrected + 1 (includes inward overlap).
         */
        private static double radialMiterPadding(double visualThickness, int edgeCount) {
            double cos = Math.cos(Math.PI / edgeCount);
            double corrected = (2 * visualThickness - (1 - cos)) / (1 + cos);
            return (corrected - 1.0) / 2.0 + (corrected + 1.0) / (2.0 * cos);
        }

        /**
         * Axis-aligned padding needed around the fill's bounding box to fit
         * the outline's miter tips within a rectangular widget.
         * Projects the radial miter extension onto the x/y axes for each vertex
         * and returns the maximum.
         */
        static double miterPadding(double visualThickness, int edgeCount) {
            double radial = radialMiterPadding(visualThickness, edgeCount);
            double startAngle = polygonStartAngle(edgeCount);
            double maxProjection = 0;
            for (int i = 0; i < edgeCount; i++) {
                double angle = startAngle + 2.0 * Math.PI * i / edgeCount;
                maxProjection = Math.max(maxProjection,
                        Math.max(Math.abs(Math.cos(angle)), Math.abs(Math.sin(angle))));
            }
            return radial * maxProjection;
        }

        private double correctedOutlineThickness(double visualThickness) {
            double cos = Math.cos(Math.PI / edgeCount);
            return (2 * visualThickness - (1 - cos)) / (1 + cos);
        }

        private QBrush brush(QColor color, GradientColor sweep) {
            return IndicatorRenderer.brush(color, sweep, sweepArea);
        }

        void setSweepArea(Rectangle sweepArea) {
            this.sweepArea = sweepArea;
        }

        double maxOutlineThickness() {
            double scaled = Math.max(outerOutlineThickness, innerOutlineThickness) * outlineScale;
            return miterPadding(scaled, edgeCount);
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
         * fillStartAngle: 0 = top (12 o'clock), increases clockwise, in degrees.
         * fillDirection: BOTH = expand symmetrically from anchor.
         */
        private static QPainterPath partialPolygonPath(double centerX, double centerY,
                                                       double radius, int edgeCount,
                                                       double fillPercent,
                                                       double fillStartAngle,
                                                       FillDirection fillDirection) {
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
            double fillLength = fillPercent * totalLength;
            // Convert fillStartAngle (0=top, CW) to math angle for ray intersection.
            // Math convention: 0=right, counter-clockwise positive.
            // Screen coords: y increases downward, so sin is negated.
            double mathAngle = Math.toRadians(90 - fillStartAngle);
            double rayDx = Math.cos(mathAngle);
            double rayDy = -Math.sin(mathAngle); // negate for screen coords
            // Find anchor position on perimeter by intersecting ray from center with polygon edges.
            double anchorPos = findAnchorPos(centerX, centerY, rayDx, rayDy,
                    vx, vy, edgeCount, edgeLength);
            // Build path(s) based on direction.
            // Vertex order is clockwise on screen. Forward = CW, backward = CCW.
            if (fillDirection == FillDirection.BOTH) {
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
                boolean forward = fillDirection == FillDirection.CLOCKWISE;
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

        private void drawOutline(QPainter painter, double centerX, double centerY,
                                 double fillRadius, double thickness, QColor color,
                                 GradientColor sweep,
                                 double fillPercent, double fillStartAngle,
                                 FillDirection fillDirection, double inwardOverlap) {
            if (thickness <= 0 || color == null || color.alpha() == 0 || fillPercent <= 0)
                return;
            // Extend the inner edge inward by inwardOverlap so that the
            // antialiased inner pixels blend with the layer below (e.g. fill)
            // rather than with a different-colored outline underneath.
            double effectiveThickness = thickness + inwardOverlap;
            QPen pen = new QPen(color);
            if (sweep != null)
                pen.setBrush(brush(color, sweep));
            pen.setWidthF(effectiveThickness);
            pen.setJoinStyle(Qt.PenJoinStyle.MiterJoin);
            painter.setBrush(Qt.BrushStyle.NoBrush);
            // Outer edge stays at fillRadius + thickness.
            // Inner edge moves to fillRadius - inwardOverlap.
            double outlineRadius = fillRadius + (thickness - inwardOverlap) / 2.0;
            if (fillPercent >= 1.0) {
                painter.setPen(pen);
                QPainterPath outlinePath = polygonPath(centerX, centerY, outlineRadius, edgeCount);
                painter.drawPath(outlinePath);
                outlinePath.dispose();
            }
            else {
                pen.setCapStyle(Qt.PenCapStyle.FlatCap);
                painter.setPen(pen);
                QPainterPath outlinePath = partialPolygonPath(
                        centerX, centerY, outlineRadius, edgeCount, fillPercent,
                        fillStartAngle, fillDirection);
                painter.drawPath(outlinePath);
                outlinePath.dispose();
            }
            pen.dispose();
        }

        void drawContent(QPainter painter, QColor fillColor,
                         QColor outerOutlineColor, QColor innerOutlineColor) {
            double maxOutlinePadding = maxOutlineThickness();
            int outlinePadding = (int) Math.ceil(maxOutlinePadding);
            double availableSize = Math.min(width(), height()) - 2 * outlinePadding;
            PolygonLayout layout = polygonLayout(availableSize, edgeCount);
            double centerX = width() / 2.0 + layout.offsetX;
            double centerY = height() / 2.0 + layout.offsetY;
            double fillRadius = layout.radius;
            QPainterPath fillPath = polygonPath(centerX, centerY, fillRadius, edgeCount);
            double scaledOuter = outerOutlineThickness * outlineScale;
            double scaledInner = innerOutlineThickness * outlineScale;
            double correctedOuter = correctedOutlineThickness(scaledOuter);
            double correctedInner = correctedOutlineThickness(scaledInner);
            // Draw fill first, then outlines on top. Outlines cover the fill
            // boundary with their inwardOverlap, preventing artifacts from
            // opacity differences between fill and outline.
            if (fillColor.alpha() != 0) {
                painter.setPen(Qt.PenStyle.NoPen);
                painter.setBrush(brush(fillColor, sweep));
                painter.drawPath(fillPath);
            }
            // Draw outer outline on top of fill.
            drawOutline(painter, centerX, centerY, fillRadius,
                    correctedOuter, outerOutlineColor, outerOutlineSweep, outerOutlineFillPercent,
                    outerOutlineFillStartAngle, outerOutlineFillDirection, 1.0);
            // Draw inner outline on top of outer outline. Compute a larger
            // inwardOverlap so the inner outline's inner miter tip extends
            // past the outer outline's inner miter tip by at least `margin`
            // pixels. Without this, the outer outline color bleeds through
            // the inner outline's antialiased inner edge, especially at
            // vertices of low-edge-count polygons (e.g. triangles).
            // Formula derived from equating the radial miter tip positions:
            //   tip = fillRadius + (corrected - overlap)/2
            //         - (corrected + overlap) / (2*cos(PI/n))
            double innerInwardOverlap;
            if (correctedOuter > 0 && correctedInner > 0) {
                double cos = Math.cos(Math.PI / edgeCount);
                double D = correctedOuter - correctedInner;
                double margin = 1.5;
                innerInwardOverlap = D * (1 - cos) / (1 + cos)
                        + 1.0 + 2.0 * margin * cos / (1 + cos);
                innerInwardOverlap = Math.max(innerInwardOverlap, 1.0);
            }
            else {
                innerInwardOverlap = fillColor.alpha() == 0 ? 0 : 1.0;
            }
            drawOutline(painter, centerX, centerY, fillRadius,
                    correctedInner, innerOutlineColor, innerOutlineSweep, innerOutlineFillPercent,
                    innerOutlineFillStartAngle, innerOutlineFillDirection, innerInwardOverlap);
            fillPath.dispose();
        }

        void redrawSourceOverShadow(QPainter painter) {
            painter.translate(x(), y());
            drawContent(painter, color, outerOutlineColor, innerOutlineColor);
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
            QColor opaqueColor = opaque(color);
            QColor opaqueOuterOutlineColor = opaque(outerOutlineColor);
            QColor opaqueInnerOutlineColor = opaque(innerOutlineColor);
            drawContent(painter, opaqueColor, opaqueOuterOutlineColor, opaqueInnerOutlineColor);
            opaqueColor.dispose();
            opaqueOuterOutlineColor.dispose();
            opaqueInnerOutlineColor.dispose();
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
        private int layerOutlinePadding;

        IndicatorLabelWidget(QWidget parent) {
            super(parent);
            setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents);
        }

        void setLayerOutlinePadding(int padding) {
            this.layerOutlinePadding = padding;
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
            double availableSize = Math.min(width(), height()) - 2 * layerOutlinePadding;
            IndicatorLayerWidget.PolygonLayout polygonLayout =
                    IndicatorLayerWidget.polygonLayout(availableSize, edgeCount);
            double centerX = width() / 2.0 + polygonLayout.offsetX();
            double centerY = height() / 2.0 + polygonLayout.offsetY();
            drawLabelText(painter, labelText, labelFont, centerX, centerY,
                    outlineThickness, outlineColor, labelColor);
            painter.end();
            painter.dispose();
        }
    }

}
