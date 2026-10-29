package mousemaster.renderer;

import mousemaster.qt.*;

import io.qt.core.*;
import io.qt.gui.*;
import io.qt.widgets.*;
import mousemaster.*;
import mousemaster.GradientColor.GradientArea;
import mousemaster.GradientColor.GradientStep;
import mousemaster.IndicatorPosition.PointIndicatorPosition;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cross-platform Qt rendering of the mouse indicator: owns a widget per layer and the shadow
 * effect, and computes where and how big to draw each layer.
 * The platform overlay owns the native window (styles its handle) and supplies the cursor,
 * screen and zoom.
 */
public final class IndicatorRenderer {

    private TransparentWindow window;
    private QWidget layersWidget;
    private final List<IndicatorLayerWidget> widgets = new ArrayList<>();
    private IndicatorShadowEffect shadowEffect;
    private final List<ShadowCast> shadowCasts = new ArrayList<>();
    private double shadowScale;
    private IndicatorConfiguration currentIndicator;
    private List<PositionedLayer> currentLayers;
    private Rectangle gradientArea;
    private Point gradientPoint;
    private double pointsPerPixel;
    private final Map<String, LayerAnchor> anchorByLayerName = new HashMap<>();
    private final Map<TextKey, QPainterPath> outlineByText = new HashMap<>();
    private final Map<TextKey, QTextDocument> documentByText = new HashMap<>();
    private IndicatorImage windowImage;
    private WindowImageKey windowImageKey;
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
        if (index == widgets.size())
            widgets.add(new IndicatorLayerWidget(layersWidget));
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
                               Screen activeScreen,
                               Map<IndicatorPosition, List<Point>> pointsByPosition,
                               Map<HintGridArea, Rectangle> rectangleByArea) {
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

    /** One indicator for each enabled layer that does not follow the mouse, in z order, so
     *  that a layer that changes does not redraw the others. */
    public static Map<String, IndicatorConfiguration> anchoredLayers(
            IndicatorConfiguration indicator) {
        Map<String, IndicatorConfiguration> indicatorByLayerName = new LinkedHashMap<>();
        for (String layerName : enabledLayerNames(indicator)) {
            IndicatorLayerConfiguration layer = indicator.layerByName().get(layerName);
            if (!layer.followMouse())
                indicatorByLayerName.put(layerName,
                        new IndicatorConfiguration(indicator.renderAsCursor(),
                                indicator.excludeFromCapture(), Map.of(layerName, layer)));
        }
        return indicatorByLayerName;
    }

    public static IndicatorConfiguration followingMouse(IndicatorConfiguration indicator) {
        Map<String, IndicatorLayerConfiguration> layerByName = new LinkedHashMap<>();
        for (Map.Entry<String, IndicatorLayerConfiguration> entry : indicator.layerByName()
                                                                             .entrySet())
            if (entry.getValue().followMouse())
                layerByName.put(entry.getKey(), entry.getValue());
        return new IndicatorConfiguration(indicator.renderAsCursor(),
                indicator.excludeFromCapture(), layerByName);
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

    /** The screen a sweep runs over, relative to the widget the brush spans, in its points. */
    private Rectangle sweepArea(Point topLeft) {
        return new Rectangle(points(snapped(gradientArea.x() - topLeft.x()), pointsPerPixel),
                points(snapped(gradientArea.y() - topLeft.y()), pointsPerPixel),
                points(gradientArea.width(), pointsPerPixel),
                points(gradientArea.height(), pointsPerPixel));
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
        if (layer.shape() != IndicatorShape.TEXT && layer.text().isEmpty())
            return layer.size() * screenScale;
        QRectF bounds = textBox(layer, screenScale);
        double layerSize = 2 * Math.max(
                Math.max(-bounds.left(), bounds.right()),
                Math.max(-bounds.top(), bounds.bottom()));
        bounds.dispose();
        return layerSize;
    }

    private record TextKey(String text, String fontName, double fontSize, FontWeight fontWeight,
                           String textStyleSheet, double scale) {
    }

    /** The layers are placed in the image, so that the image holds while they move together. */
    private record WindowImageKey(List<PositionedLayer> layers, double scale,
                                  String lastSelectedHintBoxHexColor) {
    }

    private static TextKey textKey(IndicatorLayerConfiguration layer, double scale) {
        return new TextKey(layer.text(), layer.fontName(), layer.fontSize(), layer.fontWeight(),
                layer.textStyleSheet(), scale);
    }

    private boolean drawn(TextKey key) {
        for (PositionedLayer positionedLayer : currentLayers)
            if (key.equals(textKey(positionedLayer.layer(), key.scale())))
                return true;
        return false;
    }

    private QRectF textBox(IndicatorLayerConfiguration layer, double scale) {
        if (layer.shape() == IndicatorShape.TEXT)
            return textOutline(layer, scale).boundingRect();
        QSizeF size = textDocument(layer).size();
        QRectF box = new QRectF(-size.width() / 2 * scale, -size.height() / 2 * scale,
                size.width() * scale, size.height() * scale);
        size.dispose();
        return box;
    }

    /** The text's outline, centered on its line box so that the baseline stays put whatever
     *  the letters are. Laying text out costs about 100us, and the layout is asked for on
     *  every mouse move. */
    private QPainterPath textOutline(IndicatorLayerConfiguration layer, double scale) {
        return outlineByText.computeIfAbsent(textKey(layer, scale), key -> {
            QFont font = textFont(layer, scale);
            QFontMetricsF metrics = new QFontMetricsF(font);
            QPainterPath outline = new QPainterPath();
            outline.addText(-metrics.horizontalAdvance(key.text()) / 2,
                    (metrics.ascent() - metrics.descent()) / 2, font, key.text());
            metrics.dispose();
            font.dispose();
            return outline;
        });
    }

    /** Laid out at scale 1 and drawn scaled, so that the layout is the same on every screen. */
    private QTextDocument textDocument(IndicatorLayerConfiguration layer) {
        return documentByText.computeIfAbsent(textKey(layer, 1), key -> {
            QTextDocument document = new QTextDocument();
            document.setDocumentMargin(0);
            QFont font = textFont(layer, 1);
            document.setDefaultFont(font);
            font.dispose();
            document.setDefaultStyleSheet(key.textStyleSheet());
            document.setHtml(key.text());
            document.setTextWidth(1 << 24);
            document.setTextWidth(document.idealWidth());
            return document;
        });
    }

    /** A point is 1/72 inch, and a screen at scale 1 has 96 dots per inch. */
    private static QFont textFont(IndicatorLayerConfiguration layer, double scale) {
        QFont font = QtHintFont.qFont(layer.fontName(), layer.fontSize(), layer.fontWeight());
        font.setPixelSize((int) Math.round(layer.fontSize() * 96 / 72 * scale));
        return font;
    }

    private double aspectRatio(IndicatorLayerConfiguration layer) {
        if (layer.shape() != IndicatorShape.TEXT && !layer.text().isEmpty()) {
            QSizeF size = textDocument(layer).size();
            double aspectRatio = size.width() / size.height();
            size.dispose();
            return aspectRatio;
        }
        // The box of an area or of two points is given, whatever the points of the shape.
        if (layer.area() != null || IndicatorPosition.runThrough(layer.positions()))
            return layer.aspectRatio();
        return switch (layer.shape()) {
            case TRIANGLE, STAR, PATH -> {
                List<Point> points = IndicatorLayerWidget.points(layer);
                Point min = IndicatorLayerWidget.minPoint(points);
                Point max = IndicatorLayerWidget.maxPoint(points);
                yield layer.aspectRatio() * (max.x() - min.x()) / (max.y() - min.y());
            }
            default -> layer.aspectRatio();
        };
    }

    private record LayerSize(int width, int height) {
    }

    /** The box turned by its rotation. A miter reaches as far as the stroke is thick, Qt's
     *  default miter limit. */
    private LayerSize layerSizeWithStroke(IndicatorLayerConfiguration layer,
                                          double screenScale) {
        double layerSize = layerSize(layer, screenScale);
        double aspectRatio = aspectRatio(layer);
        double boxWidth = aspectRatio >= 1 ? layerSize : layerSize * aspectRatio;
        double boxHeight = aspectRatio >= 1 ? layerSize / aspectRatio : layerSize;
        double cos = Math.abs(Math.cos(Math.toRadians(layer.rotation())));
        double sin = Math.abs(Math.sin(Math.toRadians(layer.rotation())));
        double stroke = 2 * layer.stroke().thickness() * screenScale;
        return new LayerSize(even(boxWidth * cos + boxHeight * sin + stroke),
                even(boxWidth * sin + boxHeight * cos + stroke));
    }

    /** An odd size puts the center of a centered layer half a pixel off, so it would shift as
     *  the size changes parity. */
    private static int even(double size) {
        int ceiledSize = (int) Math.ceil(size);
        return ceiledSize + ceiledSize % 2;
    }

    private static int shadowPadding(List<PositionedLayer> positionedLayers, double scale) {
        int shadowPadding = 0;
        for (PositionedLayer positionedLayer : positionedLayers) {
            Shadow shadow = positionedLayer.layer().shadow();
            if (shadow.blurRadius() != 0)
                shadowPadding = Math.max(shadowPadding, (int) Math.ceil((shadow.blurRadius() +
                        Math.max(Math.abs(shadow.horizontalOffset()),
                                 Math.abs(shadow.verticalOffset()))) * scale));
        }
        return shadowPadding;
    }


    /** Shows/updates the indicator: repositions and renders unless nothing changed. The
     *  overlay supplies the cursor rectangle, its visual center, and the active screen and
     *  zoom. */
    public void setIndicator(IndicatorConfiguration indicator, Set<String> layerNamesToAnchor,
                             Map<IndicatorPosition, List<Point>> pointsByPosition,
                             Map<HintGridArea, Rectangle> rectangleByArea,
                             Rectangle mouseRectangle, Point cursorVisualCenter,
                             Screen activeScreen, Zoom zoom, String lastSelectedHintBoxHexColor) {
        setGradientSampling(mouseRectangle, cursorVisualCenter, activeScreen);
        anchor(layerNamesToAnchor, pointsByPosition, rectangleByArea, mouseRectangle,
                cursorVisualCenter, activeScreen);
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

    private void anchor(Set<String> layerNamesToAnchor,
                        Map<IndicatorPosition, List<Point>> pointsByPosition,
                        Map<HintGridArea, Rectangle> rectangleByArea,
                        Rectangle mouseRectangle, Point cursorVisualCenter,
                        Screen activeScreen) {
        for (String layerName : layerNamesToAnchor)
            anchorByLayerName.put(layerName, new LayerAnchor(mouseRectangle, cursorVisualCenter,
                    activeScreen, pointsByPosition, rectangleByArea));
    }

    private LayerAnchor anchor(String layerName, IndicatorLayerConfiguration layer,
                               LayerAnchor mouseAnchor) {
        return layer.followMouse() ? mouseAnchor :
                anchorByLayerName.computeIfAbsent(layerName, name -> mouseAnchor);
    }

    /** The points a layer is laid out by: its positions, or the corners of its area, with the
     *  insets cut to a fraction of a pixel. */
    private List<Point> positionPoints(LayerAnchor anchor, Zoom zoom,
                                       IndicatorLayerConfiguration layer) {
        if (layer.area() != null) {
            Rectangle area = anchor.rectangleByArea().get(layer.area());
            HintGridAreaSize size = layer.area().size();
            double scale = anchor.activeScreen().scale();
            return List.of(new Point(zoomedX(area.x(), zoom) + size.leftInset() * scale,
                            zoomedY(area.y(), zoom) + size.topInset() * scale),
                    new Point(zoomedX(area.x() + area.width(), zoom) -
                              size.rightInset() * scale,
                            zoomedY(area.y() + area.height(), zoom) -
                            size.bottomInset() * scale));
        }
        List<Point> points = new ArrayList<>();
        for (IndicatorPosition position : layer.positions()) {
            List<Point> positionPoints = anchor.pointsByPosition().get(position);
            if (positionPoints == null)
                points.add(layerTopLeft(anchor, zoom, position, new LayerSize(0, 0)));
            else
                for (Point point : positionPoints)
                    points.add(new Point(zoomedX(point.x(), zoom), zoomedY(point.y(), zoom)));
        }
        return points;
    }

    private record PositionedLayer(IndicatorLayerConfiguration layer, Point topLeft) {
    }

    /** The enabled layers and their top-lefts, in z order. A layer with an area covers it. A
     *  layer through several positions is a layer for each two consecutive points, which runs
     *  from the first to the second: their distance is its length, its size is its width, and
     *  its rotation turns it from their direction. */
    private List<PositionedLayer> positionedLayers(IndicatorConfiguration indicator,
                                                   LayerAnchor mouseAnchor, Zoom zoom) {
        // Screen pixels: the configured size does not change with the zoom. Only the
        // position does, because the cursor it marks is a desktop point.
        double scale = mouseAnchor.activeScreen().scale();
        List<PositionedLayer> positionedLayers = new ArrayList<>();
        for (String layerName : enabledLayerNames(indicator)) {
            IndicatorLayerConfiguration layer = indicator.layerByName().get(layerName);
            LayerAnchor anchor = anchor(layerName, layer, mouseAnchor);
            List<Point> points = positionPoints(anchor, zoom, layer);
            if (layer.area() != null) {
                double deltaX = points.getLast().x() - points.getFirst().x();
                double deltaY = points.getLast().y() - points.getFirst().y();
                add(positionedLayers, layer.builder()
                                           .size(Math.max(deltaX, deltaY) / scale)
                                           .aspectRatio(deltaX / deltaY)
                                           .build(),
                        points.getFirst(), points.getLast(), anchor, scale);
            }
            else if (!IndicatorPosition.runThrough(layer.positions())) {
                Point topLeft = layerTopLeft(anchor, zoom, layer.positions().getFirst(),
                        layerSizeWithStroke(layer, scale));
                add(positionedLayers, layer, topLeft, anchor, scale);
            }
            else {
                for (int i = 1; i < points.size(); i++) {
                    Point from = points.get(i - 1);
                    Point to = points.get(i);
                    double deltaX = to.x() - from.x();
                    double deltaY = to.y() - from.y();
                    double length = Math.hypot(deltaX, deltaY) / scale;
                    add(positionedLayers, layer.builder()
                                               .size(Math.max(length, layer.size()))
                                               .aspectRatio(length / layer.size())
                                               .rotation(layer.rotation() + Math.toDegrees(
                                                       Math.atan2(deltaY, deltaX)))
                                               .build(),
                            from, to, anchor, scale);
                }
            }
        }
        return positionedLayers;
    }

    private void add(List<PositionedLayer> positionedLayers, IndicatorLayerConfiguration layer,
                     Point from, Point to, LayerAnchor anchor, double scale) {
        LayerSize layerSizeWithStroke = layerSizeWithStroke(layer, scale);
        add(positionedLayers, layer,
                new Point((from.x() + to.x() - layerSizeWithStroke.width()) / 2,
                        (from.y() + to.y() - layerSizeWithStroke.height()) / 2), anchor, scale);
    }

    private void add(List<PositionedLayer> positionedLayers, IndicatorLayerConfiguration layer,
                     Point topLeft, LayerAnchor anchor, double scale) {
        Point offset = offset(layer, scale);
        topLeft = new Point(topLeft.x() + offset.x(), topLeft.y() + offset.y());
        if (!layer.text().isEmpty())
            topLeft = keptOnScreen(topLeft, layerSizeWithStroke(layer, scale), layer, scale,
                    anchor.activeScreen());
        // The stroke of an area goes on its edges, so the layer keeps its fraction of a pixel.
        positionedLayers.add(new PositionedLayer(layer, layer.area() != null ? topLeft :
                new Point(Math.round(topLeft.x()), Math.round(topLeft.y()))));
    }

    private static Point offset(IndicatorLayerConfiguration layer, double scale) {
        double radians = Math.toRadians(layer.angle());
        return new Point((layer.x() + layer.distance() * Math.sin(radians)) * scale,
                (layer.y() - layer.distance() * Math.cos(radians)) * scale);
    }

    private Point keptOnScreen(Point topLeft, LayerSize layerSizeWithStroke,
                               IndicatorLayerConfiguration layer, double scale, Screen screen) {
        double halfWidth, halfHeight;
        if (layer.rotation() % 360 == 0) {
            QRectF box = textBox(layer, scale);
            halfWidth = Math.max(-box.left(), box.right()) + layer.stroke().thickness() * scale;
            halfHeight = Math.max(-box.top(), box.bottom()) + layer.stroke().thickness() * scale;
            box.dispose();
        }
        else {
            halfWidth = layerSizeWithStroke.width() / 2.0;
            halfHeight = layerSizeWithStroke.height() / 2.0;
        }
        Rectangle rectangle = screen.rectangle();
        double centerX = Math.max(rectangle.x() + halfWidth, Math.min(
                topLeft.x() + layerSizeWithStroke.width() / 2.0,
                rectangle.x() + rectangle.width() - halfWidth));
        double centerY = Math.max(rectangle.y() + halfHeight, Math.min(
                topLeft.y() + layerSizeWithStroke.height() / 2.0,
                rectangle.y() + rectangle.height() - halfHeight));
        return new Point(centerX - layerSizeWithStroke.width() / 2.0,
                centerY - layerSizeWithStroke.height() / 2.0);
    }

    private void reposition(IndicatorConfiguration indicator, Rectangle mouseRectangle,
                            Point cursorVisualCenter, Screen activeScreen, Zoom zoom) {
        double screenScale = activeScreen.scale();
        LayerAnchor mouseAnchor =
                new LayerAnchor(mouseRectangle, cursorVisualCenter, activeScreen, Map.of(),
                        Map.of());
        List<PositionedLayer> positionedLayers = positionedLayers(indicator, mouseAnchor, zoom);
        Rectangle layersRectangle = layersRectangle(positionedLayers, screenScale);
        // The window covers the screen and stays put: Qt moves a window at once but repaints
        // it later, so a layer that does not follow the mouse would shake inside a moving one.
        window.coverInPixels(activeScreen);
        Rectangle screen = activeScreen.rectangle();
        placeLayers(positionedLayers, new Point(layersRectangle.x(), layersRectangle.y()),
                layersRectangle.width(), layersRectangle.height(),
                new Point(screen.x(), screen.y()), screenScale,
                Os.macos ? activeScreen.scale() : 1);
    }

    private Rectangle layersRectangle(List<PositionedLayer> positionedLayers,
                                      double screenScale) {
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (PositionedLayer positionedLayer : positionedLayers) {
            LayerSize layerSizeWithStroke =
                    layerSizeWithStroke(positionedLayer.layer(), screenScale);
            int x = (int) Math.round(positionedLayer.topLeft().x());
            int y = (int) Math.round(positionedLayer.topLeft().y());
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x + layerSizeWithStroke.width());
            bottom = Math.max(bottom, y + layerSizeWithStroke.height());
        }
        return new Rectangle(left, top, right - left, bottom - top);
    }

    private void placeLayers(List<PositionedLayer> positionedLayers, Point layersTopLeft,
                             int layersWidth, int layersHeight, Point windowTopLeft,
                             double screenScale, double pointsPerPixel) {
        layersWidget.move(points(layersTopLeft.x() - windowTopLeft.x(), pointsPerPixel),
                points(layersTopLeft.y() - windowTopLeft.y(), pointsPerPixel));
        layersWidget.resize(points(layersWidth, pointsPerPixel),
                points(layersHeight, pointsPerPixel));
        currentLayers = positionedLayers;
        this.pointsPerPixel = pointsPerPixel;
        for (int i = 0; i < positionedLayers.size(); i++) {
            IndicatorLayerConfiguration layer = positionedLayers.get(i).layer();
            Point topLeft = positionedLayers.get(i).topLeft();
            LayerSize layerSizeWithStroke = layerSizeWithStroke(layer, screenScale);
            IndicatorLayerWidget widget = widget(i);
            widget.setScale(screenScale / pointsPerPixel);
            widget.setLayerSize(layerSize(layer, screenScale) / pointsPerPixel);
            double widgetX = (topLeft.x() - layersTopLeft.x()) / pointsPerPixel;
            double widgetY = (topLeft.y() - layersTopLeft.y()) / pointsPerPixel;
            widget.move((int) Math.round(widgetX), (int) Math.round(widgetY));
            widget.setSubPixelOffset(new Point(widgetX - Math.round(widgetX),
                    widgetY - Math.round(widgetY)));
            widget.resize(points(layerSizeWithStroke.width(), pointsPerPixel),
                    points(layerSizeWithStroke.height(), pointsPerPixel));
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
    private Point layerTopLeft(LayerAnchor anchor, Zoom zoom, IndicatorPosition position,
                               LayerSize layerSizeWithStroke) {
        Rectangle mouseRectangle = anchor.mouseRectangle();
        Rectangle screen = anchor.activeScreen().rectangle();
        if (position == PointIndicatorPosition.SCREEN_CENTER)
            return new Point(screen.x() + (screen.width() - layerSizeWithStroke.width()) / 2.0,
                    screen.y() + (screen.height() - layerSizeWithStroke.height()) / 2.0);
        List<Point> points = anchor.pointsByPosition().get(position);
        if (points != null)
            return new Point(
                    zoomedX(points.getFirst().x(), zoom) - layerSizeWithStroke.width() / 2.0,
                    zoomedY(points.getFirst().y(), zoom) - layerSizeWithStroke.height() / 2.0);
        if (position == PointIndicatorPosition.MOUSE || position == PointIndicatorPosition.TARGET ||
            position == PointIndicatorPosition.LAST_SELECTED_HINT) {
            double centerX = mouseRectangle.x() + anchor.cursorVisualCenter().x();
            double centerY = mouseRectangle.y() + anchor.cursorVisualCenter().y();
            centerX = Math.max(screen.x(), Math.min(centerX,
                    screen.x() + screen.width()));
            centerY = Math.max(screen.y(), Math.min(centerY,
                    screen.y() + screen.height()));
            return new Point(zoomedX(centerX, zoom) - layerSizeWithStroke.width() / 2.0,
                    zoomedY(centerY, zoom) - layerSizeWithStroke.height() / 2.0);
        }
        int mouseX = Math.max(screen.x(), Math.min(mouseRectangle.x(),
                screen.x() + screen.width()));
        int mouseY = Math.max(screen.y(), Math.min(mouseRectangle.y(),
                screen.y() + screen.height()));
        boolean defaultRight = position == PointIndicatorPosition.MOUSE_BOTTOM_RIGHT ||
                               position == PointIndicatorPosition.MOUSE_TOP_RIGHT;
        boolean defaultBottom = position == PointIndicatorPosition.MOUSE_BOTTOM_RIGHT ||
                                position == PointIndicatorPosition.MOUSE_BOTTOM_LEFT;
        boolean nearRightEdge = mouseX >=
                screen.x() + screen.width() - layerEdgeThreshold;
        boolean nearLeftEdge = mouseX <=
                screen.x() + layerEdgeThreshold;
        boolean placeRight = defaultRight ? !nearRightEdge : nearLeftEdge;
        int layerX = placeRight ?
                mouseX + mouseRectangle.width() / 2 : mouseX - layerSizeWithStroke.width();
        boolean nearBottomEdge = mouseY >=
                screen.y() + screen.height() - layerEdgeThreshold;
        boolean nearTopEdge = mouseY <=
                screen.y() + layerEdgeThreshold;
        boolean placeBottom = defaultBottom ? !nearBottomEdge : nearTopEdge;
        int layerY = placeBottom ?
                mouseY + mouseRectangle.height() / 2 : mouseY - layerSizeWithStroke.height();
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
                                         Map<IndicatorPosition, List<Point>> pointsByPosition,
                                         Map<HintGridArea, Rectangle> rectangleByArea,
                                         Rectangle mouseRectangle, Point cursorVisualCenter,
                                         Screen activeScreen, Zoom zoom,
                                         String lastSelectedHintBoxHexColor) {
        setGradientSampling(mouseRectangle, cursorVisualCenter, activeScreen);
        anchor(layerNamesToAnchor, pointsByPosition, rectangleByArea, mouseRectangle,
                cursorVisualCenter, activeScreen);
        double screenScale = activeScreen.scale();
        LayerAnchor mouseAnchor =
                new LayerAnchor(mouseRectangle, cursorVisualCenter, activeScreen, Map.of(),
                        Map.of());
        List<PositionedLayer> positionedLayers = positionedLayers(indicator, mouseAnchor, zoom);
        Rectangle layersRectangle = layersRectangle(positionedLayers, screenScale);
        int shadowPadding = shadowPadding(positionedLayers, screenScale);
        int x = layersRectangle.x() - shadowPadding;
        int y = layersRectangle.y() - shadowPadding;
        List<PositionedLayer> imageLayers = new ArrayList<>();
        for (PositionedLayer positionedLayer : positionedLayers)
            imageLayers.add(new PositionedLayer(positionedLayer.layer(),
                    new Point(positionedLayer.topLeft().x() - x,
                            positionedLayer.topLeft().y() - y)));
        WindowImageKey key =
                new WindowImageKey(imageLayers, screenScale, lastSelectedHintBoxHexColor);
        if (key.equals(windowImageKey) && looksTheSameAnywhere(indicator))
            return new WindowImage(windowImage, x, y);
        int width = layersRectangle.width() + 2 * shadowPadding;
        int height = layersRectangle.height() + 2 * shadowPadding;
        window();
        window.resize(width, height);
        placeLayers(positionedLayers, new Point(layersRectangle.x(), layersRectangle.y()),
                layersRectangle.width(), layersRectangle.height(), new Point(x, y),
                screenScale, 1);
        applyIndicator(indicator, screenScale, lastSelectedHintBoxHexColor);
        windowImage = render(width, height, screenScale);
        windowImageKey = key;
        return new WindowImage(windowImage, x, y);
    }

    public static boolean looksTheSameAnywhere(IndicatorConfiguration indicator) {
        for (IndicatorLayerConfiguration layer : enabledLayers(indicator))
            if (!looksTheSameAnywhere(layer.fillColor()) ||
                !looksTheSameAnywhere(layer.stroke().color()) ||
                !looksTheSameAnywhere(layer.shadow().color()))
                return false;
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
        // The image is centered on the cursor, so it reaches as far on every side as its
        // farthest layer does on one.
        int layersSize = 0;
        for (IndicatorLayerConfiguration layer : enabledLayers)
            layersSize = Math.max(layersSize, Math.max(layerSizeWithStroke(layer, scale).width(),
                    layerSizeWithStroke(layer, scale).height()) + 2 * (int)
                    Math.round((Math.max(Math.abs(layer.x()), Math.abs(layer.y())) +
                                Math.abs(layer.distance())) * scale));
        List<PositionedLayer> positionedLayers = new ArrayList<>();
        for (IndicatorLayerConfiguration layer : enabledLayers) {
            LayerSize layerSizeWithStroke = layerSizeWithStroke(layer, scale);
            Point offset = offset(layer, scale);
            positionedLayers.add(new PositionedLayer(layer, new Point(
                    gradientPoint.x() - layerSizeWithStroke.width() / 2.0 + Math.round(offset.x()),
                    gradientPoint.y() - layerSizeWithStroke.height() / 2.0 + Math.round(offset.y()))));
        }
        int shadowPadding = shadowPadding(positionedLayers, scale);
        int imageSize = layersSize + 2 * shadowPadding;
        Point layersTopLeft = new Point(gradientPoint.x() - layersSize / 2.0,
                gradientPoint.y() - layersSize / 2.0);
        window();
        window.resize(imageSize, imageSize);
        placeLayers(positionedLayers, layersTopLeft, layersSize, layersSize,
                new Point(layersTopLeft.x() - shadowPadding, layersTopLeft.y() - shadowPadding),
                scale, 1);
        applyIndicator(indicator, scale, lastSelectedHintBoxHexColor);
        return render(imageSize, imageSize, scale);
    }

    private IndicatorImage render(int width, int height, double scale) {
        QImage image = new QImage(width, height, QImage.Format.Format_ARGB32_Premultiplied);
        image.fill(0);
        window.render(image);
        int[] argb = new int[width * height];
        ByteBuffer buffer = image.bits();
        buffer.position(0);
        buffer.order(ByteOrder.nativeOrder()).asIntBuffer().get(argb);
        image.dispose();
        return new IndicatorImage(argb, width, height);
    }

    /** Applies the indicator to the widgets (shape, fill, stroke, shadow effect) without
     *  showing or positioning. Shared by the on-screen path and the offscreen cursor render. */
    private void applyIndicator(IndicatorConfiguration indicator, double shadowScale,
                                String lastSelectedHintBoxHexColor) {
        currentIndicator = indicator;
        outlineByText.entrySet().removeIf(entry -> {
            if (drawn(entry.getKey()))
                return false;
            entry.getValue().dispose();
            return true;
        });
        documentByText.entrySet().removeIf(entry -> {
            if (drawn(entry.getKey()))
                return false;
            entry.getValue().dispose();
            return true;
        });
        cleared = false;
        applyShadowEffect(shadowScale, lastSelectedHintBoxHexColor);
        for (int i = 0; i < widgets.size(); i++) {
            if (i < currentLayers.size())
                applyLayer(currentLayers.get(i).layer(), widgets.get(i),
                        currentLayers.get(i).topLeft(), lastSelectedHintBoxHexColor);
            else
                widgets.get(i).hide();
        }
    }

    private void applyLayer(IndicatorLayerConfiguration layer, IndicatorLayerWidget widget,
                            Point topLeft, String lastSelectedHintBoxHexColor) {
        widget.setSweepArea(sweepArea(topLeft));
        widget.setLayer(layer);
        widget.setFill(QtColorUtil.qColor(hex(layer.fillColor(), lastSelectedHintBoxHexColor), layer.fillOpacity()),
                sweep(layer.fillColor()));
        IndicatorStroke stroke = layer.stroke();
        widget.setStroke(
                QtColorUtil.qColor(hex(stroke.color(), lastSelectedHintBoxHexColor), stroke.opacity()),
                sweep(stroke.color()));
        widget.show();
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
        for (PositionedLayer positionedLayer : currentLayers)
            if (positionedLayer.layer().fillOpacity() < 1.0 ||
                positionedLayer.layer().stroke().thickness() > 0 &&
                positionedLayer.layer().stroke().opacity() < 1.0)
                return true;
        return false;
    }

    /** The layers that cast one shadow, blurred together as one shape. */
    private record ShadowCast(Shadow shadow, List<IndicatorLayerWidget> widgets, QColor color,
                              QBrush brush) {
    }

    private void applyShadowEffect(double scale, String lastSelectedHintBoxHexColor) {
        for (ShadowCast shadowCast : shadowCasts)
            shadowCast.color().dispose();
        shadowCasts.clear();
        shadowScale = scale;
        Map<Shadow, List<IndicatorLayerWidget>> widgetsByShadow = new LinkedHashMap<>();
        for (int i = 0; i < currentLayers.size(); i++) {
            Shadow shadow = currentLayers.get(i).layer().shadow();
            if (shadow.opacity() > 0 && shadow.blurRadius() > 0)
                widgetsByShadow.computeIfAbsent(shadow, shadow_ -> new ArrayList<>())
                               .add(widgets.get(i));
        }
        if (!widgetsByShadow.isEmpty()) {
            double left = 0, right = 0, top = 0, bottom = 0;
            for (Map.Entry<Shadow, List<IndicatorLayerWidget>> entry : widgetsByShadow.entrySet()) {
                Shadow shadow = entry.getKey();
                QColor baseColor =
                        QtColorUtil.qColor(hex(shadow.color(), lastSelectedHintBoxHexColor), 1.0);
                QColor shadowColor = new QColor(baseColor.red(), baseColor.green(),
                        baseColor.blue(), (int) Math.round(shadow.opacity() * 255));
                baseColor.dispose();
                GradientColor shadowSweep = sweep(shadow.color());
                shadowCasts.add(new ShadowCast(shadow, entry.getValue(), shadowColor,
                        shadowSweep == null ? null :
                                brush(shadowColor, shadowSweep, sweepArea(layersTopLeft()))));
                double radius = shadow.blurRadius() * scale;
                left = Math.max(left, radius - shadow.horizontalOffset() * scale);
                right = Math.max(right, radius + shadow.horizontalOffset() * scale);
                top = Math.max(top, radius - shadow.verticalOffset() * scale);
                bottom = Math.max(bottom, radius + shadow.verticalOffset() * scale);
            }
            // Reused rather than replaced: installing a graphics effect sets up Qt machinery
            // that costs milliseconds, which an animating indicator would pay every frame.
            IndicatorShadowEffect effect = reusableShadowEffect();
            effect.setTransparencyOnly(false);
            // Its own shadow is never drawn: it pads the source for the farthest shadow on
            // each side.
            effect.setOffset((right - left) / 2, (bottom - top) / 2);
            effect.setBlurRadius(Math.max(left + right, top + bottom) / 2);
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
    }

    private Point layersTopLeft() {
        double x = Double.MAX_VALUE;
        double y = Double.MAX_VALUE;
        for (PositionedLayer positionedLayer : currentLayers) {
            x = Math.min(x, positionedLayer.topLeft().x());
            y = Math.min(y, positionedLayer.topLeft().y());
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
        private IndicatorLayerConfiguration layer;
        private QColor fillColor;
        private GradientColor fillSweep;
        private QColor strokeColor;
        private GradientColor strokeSweep;
        private Rectangle sweepArea;
        private double scale;
        private Point subPixelOffset;
        private final Map<ImageKey, QImage> imageByKey = new HashMap<>();

        private record ImageKey(IndicatorLayerConfiguration layer, double layerSize, double scale,
                                int width, int height, double devicePixelRatio, int fillRgba,
                                int strokeRgba, Point subPixelOffset) {
        }

        IndicatorLayerWidget(QWidget parent) {
            super(parent);
        }

        void setLayerSize(double layerSize) {
            this.layerSize = layerSize;
        }

        void setScale(double scale) {
            this.scale = scale;
        }

        void setSubPixelOffset(Point subPixelOffset) {
            this.subPixelOffset = subPixelOffset;
        }

        void setLayer(IndicatorLayerConfiguration layer) {
            this.layer = layer;
        }

        void setFill(QColor fillColor, GradientColor fillSweep) {
            if (this.fillColor != null)
                this.fillColor.dispose();
            this.fillColor = fillColor;
            this.fillSweep = fillSweep;
        }

        void setStroke(QColor strokeColor, GradientColor strokeSweep) {
            if (this.strokeColor != null)
                this.strokeColor.dispose();
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
            double aspectRatio = aspectRatio(layer);
            double boxWidth = aspectRatio >= 1 ? layerSize : layerSize * aspectRatio;
            double boxHeight = aspectRatio >= 1 ? layerSize / aspectRatio : layerSize;
            double left = (width() - boxWidth) / 2;
            double top = (height() - boxHeight) / 2;
            QPainterPath path = new QPainterPath();
            switch (layer.shape()) {
                case CIRCLE -> path.addEllipse(left, top, boxWidth, boxHeight);
                case RECTANGLE -> path.addRoundedRect(left, top, boxWidth, boxHeight,
                        layer.borderRadius() * scale, layer.borderRadius() * scale,
                        Qt.SizeMode.AbsoluteSize);
                case TRIANGLE, STAR, PATH ->
                        addPolygon(path, corners(points(layer), left, top, boxWidth, boxHeight),
                                layer.borderRadius() * scale);
                case ARROW -> addPolygon(path, arrowCorners(left, top, boxWidth, boxHeight),
                        layer.borderRadius() * scale);
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
                case TEXT -> {
                    path.addPath(textOutline(layer, scale));
                    path.translate(width() / 2.0, height() / 2.0);
                }
            }
            return path;
        }

        /** Moves and stretches the points so that the rectangle around them fills the shape's
         *  box: points can be written in any units. */
        private static List<Point> corners(List<Point> points, double left, double top,
                                           double width, double height) {
            Point min = minPoint(points);
            Point max = maxPoint(points);
            List<Point> corners = new ArrayList<>();
            for (Point point : points)
                corners.add(new Point(left + (point.x() - min.x()) / (max.x() - min.x()) * width,
                        top + (point.y() - min.y()) / (max.y() - min.y()) * height));
            return corners;
        }

        /** A head as long as the box is tall, on a shaft a quarter as wide. An arrow that is
         *  not longer than its head is its head. */
        private static List<Point> arrowCorners(double left, double top, double width,
                                                double height) {
            double headLeft = left + Math.max(0, width - height);
            double middle = top + height / 2;
            if (headLeft == left)
                return List.of(new Point(left, top), new Point(left + width, middle),
                        new Point(left, top + height));
            double shaftHalfHeight = height / 8;
            return List.of(new Point(left, middle - shaftHalfHeight),
                    new Point(headLeft, middle - shaftHalfHeight), new Point(headLeft, top),
                    new Point(left + width, middle), new Point(headLeft, top + height),
                    new Point(headLeft, middle + shaftHalfHeight),
                    new Point(left, middle + shaftHalfHeight));
        }

        private static void addPolygon(QPainterPath path, List<Point> corners,
                                       double borderRadius) {
            for (int i = 0; i < corners.size(); i++) {
                Point previous = corners.get((i + corners.size() - 1) % corners.size());
                Point corner = corners.get(i);
                Point next = corners.get((i + 1) % corners.size());
                double toPrevious =
                        Math.hypot(previous.x() - corner.x(), previous.y() - corner.y());
                double toNext = Math.hypot(next.x() - corner.x(), next.y() - corner.y());
                double angle = Math.acos(((previous.x() - corner.x()) * (next.x() - corner.x()) +
                                          (previous.y() - corner.y()) * (next.y() - corner.y())) /
                                         (toPrevious * toNext));
                double cut = borderRadius == 0 ? 0 : Math.min(borderRadius / Math.tan(angle / 2),
                        Math.min(toPrevious, toNext) / 2);
                Point from = towards(corner, previous, cut);
                if (i == 0)
                    path.moveTo(from.x(), from.y());
                else
                    path.lineTo(from.x(), from.y());
                if (cut > 0) {
                    Point to = towards(corner, next, cut);
                    path.quadTo(corner.x(), corner.y(), to.x(), to.y());
                }
            }
            path.closeSubpath();
        }

        private static Point towards(Point from, Point to, double distance) {
            if (distance == 0)
                return from;
            double length = Math.hypot(to.x() - from.x(), to.y() - from.y());
            return new Point(from.x() + (to.x() - from.x()) * distance / length,
                    from.y() + (to.y() - from.y()) * distance / length);
        }

        private static List<Point> points(IndicatorLayerConfiguration layer) {
            return switch (layer.shape()) {
                case TRIANGLE -> trianglePoints;
                case STAR -> starPoints;
                default -> layer.points();
            };
        }

        private static Point minPoint(List<Point> points) {
            double x = Double.MAX_VALUE, y = Double.MAX_VALUE;
            for (Point point : points) {
                x = Math.min(x, point.x());
                y = Math.min(y, point.y());
            }
            return new Point(x, y);
        }

        private static Point maxPoint(List<Point> points) {
            double x = -Double.MAX_VALUE, y = -Double.MAX_VALUE;
            for (Point point : points) {
                x = Math.max(x, point.x());
                y = Math.max(y, point.y());
            }
            return new Point(x, y);
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

        private void drawShape(QPainter painter, QColor fillColor, QColor strokeColor) {
            painter.save();
            painter.translate(subPixelOffset.x(), subPixelOffset.y());
            painter.translate(width() / 2.0, height() / 2.0);
            painter.rotate(layer.rotation());
            painter.translate(-width() / 2.0, -height() / 2.0);
            QPainterPath path = shapePath();
            if (fillColor.alpha() != 0) {
                painter.setPen(Qt.PenStyle.NoPen);
                painter.setBrush(brush(fillColor, fillSweep));
                painter.drawPath(path);
            }
            IndicatorStroke stroke = layer.stroke();
            double strokeThickness = stroke.thickness() * scale;
            if (strokeThickness > 0 && strokeColor.alpha() != 0 && stroke.lengthPercent() != 0) {
                QPen pen = new QPen(strokeColor);
                if (strokeSweep != null)
                    pen.setBrush(brush(strokeColor, strokeSweep));
                pen.setWidthF(strokeThickness);
                pen.setJoinStyle(Qt.PenJoinStyle.MiterJoin);
                pen.setCapStyle(switch (stroke.cap()) {
                    case FLAT -> Qt.PenCapStyle.FlatCap;
                    case ROUND -> Qt.PenCapStyle.RoundCap;
                    case SQUARE -> Qt.PenCapStyle.SquareCap;
                });
                // Qt measures dashes in pen widths.
                if (stroke.dashLength() > 0 && stroke.dashGap() > 0) {
                    pen.setDashPattern(List.of(stroke.dashLength() * scale / strokeThickness,
                            stroke.dashGap() * scale / strokeThickness));
                    pen.setDashOffset(stroke.dashOffset() * scale / strokeThickness);
                }
                painter.setBrush(Qt.BrushStyle.NoBrush);
                if (Math.abs(stroke.lengthPercent()) >= 1 || !layer.shape().hasOneOutline()) {
                    painter.setPen(pen);
                    painter.drawPath(path);
                }
                else {
                    if (strokeSweep != null && strokeSweep.area() == GradientArea.ELEMENT) {
                        QRectF bounds = path.boundingRect();
                        pen.setBrush(QtColorUtil.qBrush(strokeSweep, strokeColor.alphaF(),
                                new Rectangle((int) Math.round(bounds.x()),
                                        (int) Math.round(bounds.y()),
                                        (int) Math.round(bounds.width()),
                                        (int) Math.round(bounds.height()))));
                        bounds.dispose();
                    }
                    painter.setPen(pen);
                    QPainterPath strokePath = partialPath(path, width() / 2.0, height() / 2.0,
                            stroke.startAngle(), stroke.lengthPercent(), stroke.anchor());
                    painter.drawPath(strokePath);
                    strokePath.dispose();
                }
                pen.dispose();
            }
            path.dispose();
            if (layer.shape() != IndicatorShape.TEXT && !layer.text().isEmpty()) {
                painter.translate(width() / 2.0, height() / 2.0);
                drawDocument(painter);
            }
            painter.restore();
        }

        private void drawDocument(QPainter painter) {
            QTextDocument document = textDocument(layer);
            QSizeF size = document.size();
            painter.scale(scale, scale);
            painter.translate(-size.width() / 2, -size.height() / 2);
            QAbstractTextDocumentLayout.PaintContext context =
                    new QAbstractTextDocumentLayout.PaintContext();
            document.documentLayout().draw(painter, context);
            context.dispose();
            size.dispose();
        }

        void drawContent(QPainter painter, QColor fillColor, QColor strokeColor) {
            if (fillSweep != null || strokeSweep != null) {
                drawShape(painter, fillColor, strokeColor);
                return;
            }
            double devicePixelRatio = painter.device().devicePixelRatioF();
            ImageKey key = new ImageKey(layer, layerSize, scale, width(), height(),
                    devicePixelRatio, fillColor.rgba(), strokeColor.rgba(), subPixelOffset);
            QImage image = imageByKey.get(key);
            if (image == null) {
                imageByKey.entrySet().removeIf(entry -> {
                    if (entry.getKey().layer().equals(layer) &&
                        entry.getKey().layerSize() == layerSize &&
                        entry.getKey().width() == width() && entry.getKey().height() == height())
                        return false;
                    entry.getValue().dispose();
                    return true;
                });
                image = new QImage((int) Math.ceil((width() + 2 * miterReach()) * devicePixelRatio),
                        (int) Math.ceil((height() + 2 * miterReach()) * devicePixelRatio),
                        QImage.Format.Format_ARGB32_Premultiplied);
                image.setDevicePixelRatio(devicePixelRatio);
                image.fill(0);
                QPainter imagePainter = new QPainter(image);
                imagePainter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
                imagePainter.translate(miterReach(), miterReach());
                drawShape(imagePainter, fillColor, strokeColor);
                imagePainter.end();
                imagePainter.dispose();
                imageByKey.put(key, image);
            }
            painter.drawImage(-miterReach(), -miterReach(), image);
        }

        private int miterReach() {
            return (int) Math.ceil(layer.stroke().thickness() * scale);
        }

        void redrawSourceOverShadow(QPainter painter, boolean opaque) {
            painter.translate(x(), y());
            QColor drawnFillColor = opaque ? opaque(fillColor) : fillColor;
            QColor drawnStrokeColor = opaque ? opaque(strokeColor) : strokeColor;
            drawContent(painter, drawnFillColor, drawnStrokeColor);
            if (opaque) {
                drawnFillColor.dispose();
                drawnStrokeColor.dispose();
            }
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
        protected void drawShadow(QPainter painter) {
            if (transparencyOnly()) {
                super.drawShadow(painter);
                return;
            }
            QPoint sourceOffset = new QPoint();
            QPixmap sourcePixmap = sourcePixmap(Qt.CoordinateSystem.DeviceCoordinates,
                    sourceOffset, PixmapPadMode.PadToEffectiveBoundingRect);
            QTransform savedTransform = painter.worldTransform();
            QTransform identity = new QTransform();
            painter.setWorldTransform(identity);
            for (ShadowCast shadowCast : renderer.shadowCasts) {
                QPixmap castPixmap =
                        shadowCast.widgets().size() == renderer.currentLayers.size() ?
                                sourcePixmap :
                                castPixmap(sourcePixmap,
                                        (int) Math.round(savedTransform.dx()) - sourceOffset.x(),
                                        (int) Math.round(savedTransform.dy()) - sourceOffset.y(),
                                        shadowCast.widgets());
                Shadow shadow = shadowCast.shadow();
                drawShadow(painter, castPixmap, sourceOffset,
                        shadow.blurRadius() * renderer.shadowScale,
                        shadow.horizontalOffset() * renderer.shadowScale,
                        shadow.verticalOffset() * renderer.shadowScale, shadowCast.color(),
                        shadowCast.brush(), shadow.stackCount());
                if (castPixmap != sourcePixmap)
                    castPixmap.dispose();
            }
            painter.drawPixmap(sourceOffset, sourcePixmap);
            painter.setWorldTransform(savedTransform);
            savedTransform.dispose();
            identity.dispose();
            sourcePixmap.dispose();
            sourceOffset.dispose();
            redrawSourceOverShadow(painter);
        }

        /** The source as only these layers draw it. */
        private static QPixmap castPixmap(QPixmap sourcePixmap, int x, int y,
                                          List<IndicatorLayerWidget> widgets) {
            QPixmap castPixmap = new QPixmap(sourcePixmap.width(), sourcePixmap.height());
            castPixmap.setDevicePixelRatio(sourcePixmap.devicePixelRatio());
            QColor transparent = new QColor(0, 0, 0, 0);
            castPixmap.fill(transparent);
            transparent.dispose();
            QPainter painter = new QPainter(castPixmap);
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            painter.translate(x, y);
            for (IndicatorLayerWidget widget : widgets) {
                painter.setClipRect(widget.x(), widget.y(), widget.width(), widget.height());
                widget.redrawSourceOverShadow(painter, true);
            }
            painter.end();
            painter.dispose();
            return castPixmap;
        }

        @Override
        protected void redrawSourceOverShadow(QPainter painter) {
            if (!renderer.indicatorHasTransparency())
                return;
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            List<IndicatorLayerWidget> widgets =
                    renderer.widgets.subList(0, renderer.currentLayers.size());
            if (!transparencyOnly()) {
                painter.setCompositionMode(
                        QPainter.CompositionMode.CompositionMode_DestinationOut);
                for (IndicatorLayerWidget widget : widgets)
                    widget.redrawSourceOverShadow(painter, true);
                painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
            }
            for (IndicatorLayerWidget widget : widgets)
                widget.redrawSourceOverShadow(painter, false);
        }
    }

}
