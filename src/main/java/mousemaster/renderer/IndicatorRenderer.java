package mousemaster.renderer;

import mousemaster.Color.LastSelectedHintColors;
import mousemaster.qt.*;

import io.qt.core.*;
import io.qt.gui.*;
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
 * Cross-platform Qt rendering of the mouse indicator: paints the layers and their shadows into
 * images, and computes where and how big to draw each layer.
 * The platform overlay draws the images it renders in its own windows, and supplies the
 * cursor, screen and zoom.
 */
public final class IndicatorRenderer {

    private Rectangle layersArea;
    private final List<DrawnLayer> drawnLayers = new ArrayList<>();
    private final List<ShadowCast> shadowCasts = new ArrayList<>();
    private Rectangle shadowSourceArea;
    private double shadowScale;
    private List<PositionedLayer> currentLayers;
    private Rectangle gradientArea;
    private final Map<String, LayerAnchor> anchorByLayerName;
    private final Map<TextKey, QPainterPath> outlineByText = new HashMap<>();
    private final Map<TextKey, QTextDocument> documentByText = new HashMap<>();
    private QImage windowImage;
    private WindowImageKey windowImageKey;

    public IndicatorRenderer() {
        anchorByLayerName = new HashMap<>();
    }

    public IndicatorRenderer(IndicatorRenderer renderer) {
        anchorByLayerName = renderer.anchorByLayerName;
    }

    private DrawnLayer drawnLayer(int index) {
        if (index == drawnLayers.size())
            drawnLayers.add(new DrawnLayer());
        return drawnLayers.get(index);
    }

    private record LayerAnchor(Rectangle mouseRectangle, Point cursorVisualCenter,
                               Screen activeScreen,
                               Map<IndicatorPosition, List<Point>> pointsByPosition,
                               Map<Area, Rectangle> rectangleByArea,
                               Map<Point, HintRectangles> hintRectanglesByPoint) {
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

    public static List<IndicatorConfiguration> anchoredLayers(
            IndicatorConfiguration indicator, List<IndicatorConfiguration> currentIndicators,
            Set<String> layerNamesToAnchor) {
        List<Map<String, IndicatorLayerConfiguration>> layerByNames = new ArrayList<>();
        boolean previousLayerChanged = false;
        IndicatorConfiguration previousCurrentIndicator = null;
        for (String layerName : enabledLayerNames(indicator)) {
            IndicatorLayerConfiguration layer = indicator.layerByName().get(layerName);
            if (layer.followMouse())
                continue;
            IndicatorConfiguration currentIndicator =
                    currentIndicators.stream()
                                     .filter(current -> current.layerByName()
                                                               .containsKey(layerName))
                                     .findFirst()
                                     .orElse(null);
            boolean layerChanged = layerNamesToAnchor.contains(layerName) ||
                                   currentIndicator == null ||
                                   !layer.equals(currentIndicator.layerByName().get(layerName));
            // A new window where changed and unchanged layers meet keeps the z order. Layers
            // of different windows never share one, so that a window that split stays split.
            if (layerByNames.isEmpty() || layerChanged != previousLayerChanged ||
                currentIndicator != previousCurrentIndicator)
                layerByNames.add(new LinkedHashMap<>());
            layerByNames.getLast().put(layerName, layer);
            previousLayerChanged = layerChanged;
            previousCurrentIndicator = currentIndicator;
        }
        return layerByNames.stream()
                           .map(layerByName -> new IndicatorConfiguration(
                                   indicator.renderAsCursor(), indicator.excludeFromCapture(),
                                   layerByName))
                           .toList();
    }

    /** The layers the cursor image can draw: centered on the cursor, and not moved near a
     *  screen edge. */
    public static IndicatorConfiguration cursorLayers(IndicatorConfiguration indicator) {
        Map<String, IndicatorLayerConfiguration> layerByName = new LinkedHashMap<>();
        for (Map.Entry<String, IndicatorLayerConfiguration> entry : indicator.layerByName()
                                                                             .entrySet())
            if (entry.getValue().followMouse() &&
                entry.getValue().position() == PointIndicatorPosition.MOUSE &&
                entry.getValue().endPosition() == null && entry.getValue().text().isEmpty())
                layerByName.put(entry.getKey(), entry.getValue());
        return new IndicatorConfiguration(indicator.renderAsCursor(),
                indicator.excludeFromCapture(), layerByName);
    }

    public record WindowLayerGroup(boolean belowOverlay, boolean followMouse,
                                   List<IndicatorLayerCopyKey> copyKeys,
                                   List<IndicatorConfiguration> indicators) {
    }

    private record OrderedLayer(String layerName, IndicatorLayerCopyKey copyKey, int z,
                                boolean followMouse) {
    }

    public static List<WindowLayerGroup> windowLayerGroups(
            IndicatorConfiguration indicator,
            Map<IndicatorLayerCopyKey, IndicatorConfiguration> indicatorByCopyKey,
            List<WindowLayerGroup> currentWindowLayerGroups, Set<String> layerNamesToAnchor,
            int overlayZ) {
        List<OrderedLayer> layers = new ArrayList<>();
        for (String layerName : enabledLayerNames(indicator)) {
            IndicatorLayerConfiguration layer = indicator.layerByName().get(layerName);
            layers.add(new OrderedLayer(layerName, null, layer.z(), layer.followMouse()));
        }
        for (Map.Entry<IndicatorLayerCopyKey, IndicatorConfiguration> entry :
                indicatorByCopyKey.entrySet())
            if (entry.getValue().enabled())
                layers.add(new OrderedLayer(entry.getKey().layerName(), entry.getKey(),
                        entry.getValue().layerByName().get(entry.getKey().layerName()).z(),
                        false));
        layers.sort(Comparator.comparingInt(OrderedLayer::z)
                              .thenComparing(layer -> !layer.followMouse())
                              .thenComparing(layer -> layer.copyKey() == null));
        List<IndicatorConfiguration> currentIndicators = currentWindowLayerGroups.stream()
                .flatMap(windowLayerGroup -> windowLayerGroup.indicators().stream())
                .toList();
        List<WindowLayerGroup> windowLayerGroups = new ArrayList<>();
        int windowStart = 0;
        for (int index = 1; index <= layers.size(); index++) {
            if (index < layers.size() &&
                (layers.get(index).z() < overlayZ) == (layers.get(windowStart).z() < overlayZ) &&
                layers.get(index).followMouse() == layers.get(windowStart).followMouse() &&
                (layers.get(index).copyKey() == null || layers.get(index - 1).copyKey() != null))
                continue;
            List<IndicatorLayerCopyKey> copyKeys = new ArrayList<>();
            Map<String, IndicatorLayerConfiguration> layerByName = new LinkedHashMap<>();
            for (OrderedLayer layer : layers.subList(windowStart, index)) {
                if (layer.copyKey() != null)
                    copyKeys.add(layer.copyKey());
                else
                    layerByName.put(layer.layerName(),
                            indicator.layerByName().get(layer.layerName()));
            }
            IndicatorConfiguration windowIndicator = new IndicatorConfiguration(
                    indicator.renderAsCursor(), indicator.excludeFromCapture(), layerByName);
            boolean followMouse = layers.get(windowStart).followMouse();
            windowLayerGroups.add(new WindowLayerGroup(layers.get(windowStart).z() < overlayZ,
                    followMouse, copyKeys, followMouse ? List.of(windowIndicator) :
                    anchoredLayers(windowIndicator, currentIndicators, layerNamesToAnchor)));
            windowStart = index;
        }
        return windowLayerGroups;
    }

    private String hex(GradientColor color, Point gradientPoint) {
        return sweep(color) == null && color.gradient() ?
                Color.hexColor(color.rgbAt(gradientArea, gradientPoint.x(), gradientPoint.y())) :
                color.hexColor();
    }

    private Point gradientPoint(PositionedLayer positionedLayer, double scale) {
        LayerSize layerSizeWithStroke = layerSizeWithStroke(positionedLayer.layer(), scale);
        return new Point(positionedLayer.topLeft().x() + layerSizeWithStroke.width() / 2.0,
                positionedLayer.topLeft().y() + layerSizeWithStroke.height() / 2.0);
    }

    /** The sweep is cached per extent, and a screen wide ramp shifted by less than this is
     *  not a different one. */
    private static final int sweepGrid = 64;

    private static int snapped(double coordinate) {
        return (int) Math.round(coordinate / sweepGrid) * sweepGrid;
    }

    private static GradientColor sweep(GradientColor color) {
        return color.gradient() && color.step() == GradientStep.PIXEL ? color : null;
    }

    /** The screen a sweep runs over, relative to the layer the brush spans. */
    private Rectangle sweepArea(Point topLeft) {
        return new Rectangle(snapped(gradientArea.x() - topLeft.x()),
                snapped(gradientArea.y() - topLeft.y()), gradientArea.width(),
                gradientArea.height());
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

    /** The layers are placed in the image, so that the image holds while they move together,
     *  or on the screen when a color depends on where they are. */
    private record WindowImageKey(List<PositionedLayer> layers, double scale,
                                  LastSelectedHintColors lastSelectedHintColors,
                                  Rectangle gradientArea) {
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
        if (layer.area().size().source() != null || layer.endPosition() != null)
            return layer.aspectRatio();
        return switch (layer.shape()) {
            case TRIANGLE, STAR, PATH -> {
                List<Point> points = DrawnLayer.points(layer);
                Point min = DrawnLayer.minPoint(points);
                Point max = DrawnLayer.maxPoint(points);
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


    private void anchor(Set<String> layerNamesToAnchor,
                        Map<IndicatorPosition, List<Point>> pointsByPosition,
                        Map<Area, Rectangle> rectangleByArea,
                        Map<Point, HintRectangles> hintRectanglesByPoint,
                        Rectangle mouseRectangle, Point cursorVisualCenter,
                        Screen activeScreen) {
        for (String layerName : layerNamesToAnchor)
            anchorByLayerName.put(layerName, new LayerAnchor(mouseRectangle, cursorVisualCenter,
                    activeScreen, pointsByPosition, rectangleByArea, hintRectanglesByPoint));
    }

    public void anchor(String layerName, IndicatorRenderer renderer) {
        anchorByLayerName.put(layerName, renderer.anchorByLayerName.get(layerName));
    }

    private LayerAnchor anchor(String layerName, IndicatorLayerConfiguration layer,
                               LayerAnchor mouseAnchor) {
        return layer.followMouse() ? mouseAnchor :
                anchorByLayerName.computeIfAbsent(layerName, name -> mouseAnchor);
    }

    /** A point a layer is laid out by, on the screen, and the hint box or cell it starts and
     *  stops at, null when there is none. */
    private record PositionPoint(Point point, Rectangle rectangle) {
    }

    private List<PositionPoint> positionPoints(LayerAnchor anchor, Zoom zoom,
                                               IndicatorPosition position,
                                               PositionAnchor positionAnchor) {
        List<Point> points = anchor.pointsByPosition().get(position);
        if (points == null)
            return List.of(new PositionPoint(
                    layerTopLeft(anchor, zoom, position, new LayerSize(0, 0)), null));
        List<PositionPoint> positionPoints = new ArrayList<>();
        for (Point point : points)
            positionPoints.add(positionPoint(point, anchor, zoom, positionAnchor));
        return positionPoints;
    }

    private static PositionPoint positionPoint(Point point, LayerAnchor anchor, Zoom zoom,
                                               PositionAnchor positionAnchor) {
        Point zoomedPoint = new Point(zoomedX(point.x(), zoom), zoomedY(point.y(), zoom));
        HintRectangles hintRectangles = anchor.hintRectanglesByPoint().get(point);
        if (positionAnchor == PositionAnchor.POINT || hintRectangles == null)
            return new PositionPoint(zoomedPoint, null);
        return new PositionPoint(zoomedPoint,
                positionAnchor == PositionAnchor.HINT_BOX_EDGE && hintRectangles.box() != null ?
                        hintRectangles.box() :
                        zoom == null ? hintRectangles.cell() :
                                zoom.zoomedRectangle(hintRectangles.cell()));
    }

    private record PositionedLayer(IndicatorLayerConfiguration layer, Point topLeft) {
    }

    /** The enabled layers and their top-lefts, in z order. */
    private List<PositionedLayer> positionedLayers(IndicatorConfiguration indicator,
                                                   LayerAnchor mouseAnchor, Zoom zoom) {
        // Screen pixels: the configured size does not change with the zoom. Only the
        // position does, because the cursor it marks is a desktop point.
        double scale = mouseAnchor.activeScreen().scale();
        List<PositionedLayer> positionedLayers = new ArrayList<>();
        for (String layerName : enabledLayerNames(indicator)) {
            IndicatorLayerConfiguration layer = indicator.layerByName().get(layerName);
            LayerAnchor anchor = anchor(layerName, layer, mouseAnchor);
            if (layer.area().size().source() != null) {
                Rectangle area = anchor.rectangleByArea().get(layer.area());
                addLayerOverRectangle(positionedLayers, layer,
                        new Point(zoomedX(area.x(), zoom), zoomedY(area.y(), zoom)),
                        new Point(zoomedX(area.x() + area.width(), zoom),
                                zoomedY(area.y() + area.height(), zoom)),
                        anchor, scale);
            }
            else if (layer.endPosition() == null) {
                LayerSize layerSizeWithStroke = layerSizeWithStroke(layer, scale);
                List<Point> points = anchor.pointsByPosition().get(layer.position());
                if (points == null)
                    add(positionedLayers, layer,
                            layerTopLeft(anchor, zoom, layer.position(), layerSizeWithStroke),
                            anchor, scale);
                else
                    for (Point point : points) {
                        Rectangle rectangle =
                                positionPoint(point, anchor, zoom, layer.positionAnchor())
                                        .rectangle();
                        if (rectangle == null)
                            add(positionedLayers, layer, new Point(
                                    zoomedX(point.x(), zoom) - layerSizeWithStroke.width() / 2.0,
                                    zoomedY(point.y(), zoom) - layerSizeWithStroke.height() / 2.0),
                                    anchor, scale);
                        else
                            addLayerOverRectangle(positionedLayers, layer,
                                    new Point(rectangle.x(), rectangle.y()),
                                    new Point(rectangle.x() + rectangle.width(),
                                            rectangle.y() + rectangle.height()),
                                    anchor, scale);
                    }
            }
            else {
                List<PositionPoint> points =
                        positionPoints(anchor, zoom, layer.position(), layer.positionAnchor());
                List<PositionPoint> endPoints = positionPoints(anchor, zoom,
                        layer.endPosition(), layer.endPositionAnchor());
                if (layer.endPosition().equals(layer.position()))
                    for (int i = 1; i < points.size(); i++)
                        add(positionedLayers, layer, points.get(i - 1), endPoints.get(i), anchor,
                                scale);
                else
                    for (PositionPoint point : points)
                        for (PositionPoint endPoint : endPoints)
                            add(positionedLayers, layer, point, endPoint, anchor, scale);
            }
        }
        return positionedLayers;
    }

    private void add(List<PositionedLayer> positionedLayers, IndicatorLayerConfiguration layer,
                     PositionPoint from, PositionPoint to, LayerAnchor anchor, double scale) {
        double fromExit = rectangleExit(from, to);
        double toExit = rectangleExit(to, from);
        if (fromExit + toExit >= 1)
            return;
        double deltaX = to.point().x() - from.point().x();
        double deltaY = to.point().y() - from.point().y();
        double length = (1 - fromExit - toExit) * Math.hypot(deltaX, deltaY) / scale;
        add(positionedLayers, layer.builder()
                                   .size(Math.max(length, layer.size()))
                                   .aspectRatio(length / layer.size())
                                   .rotation(layer.rotation() +
                                             Math.toDegrees(Math.atan2(deltaY, deltaX)))
                                   .build(),
                new Point(from.point().x() + fromExit * deltaX,
                        from.point().y() + fromExit * deltaY),
                new Point(to.point().x() - toExit * deltaX, to.point().y() - toExit * deltaY),
                anchor, scale);
    }

    private static double rectangleExit(PositionPoint from, PositionPoint to) {
        Rectangle rectangle = from.rectangle();
        if (rectangle == null)
            return 0;
        return rectangle.exit(from.point(), to.point());
    }

    private void addLayerOverRectangle(List<PositionedLayer> positionedLayers,
                                       IndicatorLayerConfiguration layer, Point first,
                                       Point last, LayerAnchor anchor, double scale) {
        AreaSize size = layer.area().size();
        double screenScale = anchor.activeScreen().scale();
        first = new Point(first.x() + size.leftInset() * screenScale,
                first.y() + size.topInset() * screenScale);
        last = new Point(last.x() - size.rightInset() * screenScale,
                last.y() - size.bottomInset() * screenScale);
        double deltaX = last.x() - first.x();
        double deltaY = last.y() - first.y();
        add(positionedLayers, layer.builder()
                                   .size(Math.max(deltaX, deltaY) / scale)
                                   .aspectRatio(deltaX / deltaY)
                                   .build(),
                first, last, anchor, scale);
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
        positionedLayers.add(new PositionedLayer(layer,
                layer.area().size().source() != null ? topLeft :
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
                             int layersWidth, int layersHeight, Point imageTopLeft,
                             double screenScale) {
        layersArea = new Rectangle((int) Math.round(layersTopLeft.x() - imageTopLeft.x()),
                (int) Math.round(layersTopLeft.y() - imageTopLeft.y()), layersWidth,
                layersHeight);
        currentLayers = positionedLayers;
        for (int i = 0; i < positionedLayers.size(); i++) {
            IndicatorLayerConfiguration layer = positionedLayers.get(i).layer();
            Point topLeft = positionedLayers.get(i).topLeft();
            LayerSize layerSizeWithStroke = layerSizeWithStroke(layer, screenScale);
            DrawnLayer drawnLayer = drawnLayer(i);
            drawnLayer.setScale(screenScale);
            drawnLayer.setLayerSize(layerSize(layer, screenScale));
            double x = topLeft.x() - layersTopLeft.x();
            double y = topLeft.y() - layersTopLeft.y();
            drawnLayer.setSubPixelOffset(new Point(x - Math.round(x), y - Math.round(y)));
            drawnLayer.setRectangle(new Rectangle((int) Math.round(x), (int) Math.round(y),
                    layerSizeWithStroke.width(), layerSizeWithStroke.height()));
        }
    }

    private static final int layerEdgeThreshold = 100;

    /**
     * Returns the layer top-left position for the given layer size.
     * For MOUSE, the layer is centered on the cursor's visual center.
     * For a mouse corner, the layer is placed in that corner relative to the cursor,
     * flipping to the opposite side when near the corresponding screen edge.
     */
    private Point layerTopLeft(LayerAnchor anchor, Zoom zoom, IndicatorPosition position,
                               LayerSize layerSizeWithStroke) {
        Rectangle mouseRectangle = anchor.mouseRectangle();
        Rectangle screen = anchor.activeScreen().rectangle();
        if (position == PointIndicatorPosition.ACTIVE_SCREEN_CENTER)
            return new Point(screen.x() + (screen.width() - layerSizeWithStroke.width()) / 2.0,
                    screen.y() + (screen.height() - layerSizeWithStroke.height()) / 2.0);
        boolean screenRight = position == PointIndicatorPosition.ACTIVE_SCREEN_TOP_RIGHT ||
                              position == PointIndicatorPosition.ACTIVE_SCREEN_BOTTOM_RIGHT;
        boolean screenBottom = position == PointIndicatorPosition.ACTIVE_SCREEN_BOTTOM_LEFT ||
                               position == PointIndicatorPosition.ACTIVE_SCREEN_BOTTOM_RIGHT;
        if (position == PointIndicatorPosition.ACTIVE_SCREEN_TOP_LEFT || screenRight ||
            screenBottom)
            return new Point(screenRight ?
                    screen.x() + screen.width() - layerSizeWithStroke.width() : screen.x(),
                    screenBottom ?
                            screen.y() + screen.height() - layerSizeWithStroke.height() :
                            screen.y());
        if (position == PointIndicatorPosition.MOUSE || position == PointIndicatorPosition.TARGET ||
            position == PointIndicatorPosition.LAST_SELECTED_HINT ||
            position == PointIndicatorPosition.ACTIVE_WINDOW_CENTER ||
            position == PointIndicatorPosition.LAST_OVERLAY_AREA_CENTER) {
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

    /** An image of the indicator, and where it goes on the screen. The size outlives the image,
     *  which its renderer disposes when it renders the next one. */
    public record WindowImage(QImage image, int x, int y, int width, int height) {

        public Rectangle rectangle() {
            return new Rectangle(x, y, width, height);
        }

    }

    /** Where the images changed: where those that are no longer the same were, and are. */
    public static Rectangle changedRectangle(List<WindowImage> windowImages,
                                             List<WindowImage> shownWindowImages) {
        List<Rectangle> changedRectangles = new ArrayList<>();
        for (int index = 0; index < Math.max(windowImages.size(), shownWindowImages.size());
             index++) {
            WindowImage windowImage = index < windowImages.size() ? windowImages.get(index) : null;
            WindowImage shownWindowImage = index < shownWindowImages.size() ?
                    shownWindowImages.get(index) : null;
            if (windowImage != null && shownWindowImage != null &&
                windowImage.image() == shownWindowImage.image() &&
                windowImage.rectangle().equals(shownWindowImage.rectangle()))
                continue;
            if (windowImage != null)
                changedRectangles.add(windowImage.rectangle());
            if (shownWindowImage != null)
                changedRectangles.add(shownWindowImage.rectangle());
        }
        return changedRectangles.isEmpty() ? new Rectangle(0, 0, 0, 0) :
                Rectangle.union(changedRectangles);
    }

    /** Lays the indicator out at the cursor and renders it into an image the size of what it
     *  draws. When the layers keep their places inside the image, which they do when they all
     *  follow the mouse, and no color depends on where the indicator is, the last image is
     *  returned with its new screen position instead. */
    public WindowImage renderWindowImage(IndicatorConfiguration indicator,
                                         Set<String> layerNamesToAnchor,
                                         Map<IndicatorPosition, List<Point>> pointsByPosition,
                                         Map<Area, Rectangle> rectangleByArea,
                                         Map<Point, HintRectangles> hintRectanglesByPoint,
                                         Rectangle mouseRectangle, Point cursorVisualCenter,
                                         Screen activeScreen, Zoom zoom,
                                         LastSelectedHintColors lastSelectedHintColors) {
        gradientArea = activeScreen.rectangle();
        anchor(layerNamesToAnchor, pointsByPosition, rectangleByArea, hintRectanglesByPoint,
                mouseRectangle, cursorVisualCenter, activeScreen);
        double screenScale = activeScreen.scale();
        LayerAnchor mouseAnchor =
                new LayerAnchor(mouseRectangle, cursorVisualCenter, activeScreen, Map.of(),
                        Map.of(), Map.of());
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
        WindowImageKey key = new WindowImageKey(
                looksTheSameAnywhere(indicator, lastSelectedHintColors) ? imageLayers :
                        positionedLayers, screenScale, lastSelectedHintColors, gradientArea);
        if (key.equals(windowImageKey))
            return new WindowImage(windowImage, x, y, windowImage.width(), windowImage.height());
        int width = layersRectangle.width() + 2 * shadowPadding;
        int height = layersRectangle.height() + 2 * shadowPadding;
        placeLayers(positionedLayers, new Point(layersRectangle.x(), layersRectangle.y()),
                layersRectangle.width(), layersRectangle.height(), new Point(x, y),
                screenScale);
        applyIndicator(screenScale, lastSelectedHintColors);
        if (windowImage != null)
            windowImage.dispose();
        windowImage = render(width, height);
        windowImageKey = key;
        return new WindowImage(windowImage, x, y, width, height);
    }

    /** Not when a gradient is sampled where a layer is, or swept across the screen rather than
     *  across the layer itself. */
    public static boolean looksTheSameAnywhere(IndicatorConfiguration indicator,
                                               LastSelectedHintColors lastSelectedHintColors) {
        for (IndicatorLayerConfiguration layer : enabledLayers(indicator))
            for (Color color : List.of(layer.fillColor(), layer.stroke().color(),
                    layer.shadow().color())) {
                GradientColor gradientColor = color.gradientColor(lastSelectedHintColors);
                GradientColor sweep = sweep(gradientColor);
                if (sweep == null ? gradientColor.gradient() :
                        sweep.area() != GradientArea.ELEMENT)
                    return false;
            }
        return true;
    }

    /** Renders the indicator into a premultiplied-ARGB image for use as the system cursor,
     *  centered on the indicator's visual center. */
    public IndicatorImage renderCursorImage(IndicatorConfiguration indicator, double scale,
                                         LastSelectedHintColors lastSelectedHintColors,
                                         Rectangle mouseRectangle, Point cursorVisualCenter,
                                         Screen activeScreen) {
        gradientArea = activeScreen.rectangle();
        List<IndicatorLayerConfiguration> enabledLayers = enabledLayers(indicator);
        // The image is centered on the cursor, so it reaches as far on every side as its
        // farthest layer does on one.
        Point center = new Point(mouseRectangle.x() + cursorVisualCenter.x(),
                mouseRectangle.y() + cursorVisualCenter.y());
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
                    center.x() - layerSizeWithStroke.width() / 2.0 + Math.round(offset.x()),
                    center.y() - layerSizeWithStroke.height() / 2.0 + Math.round(offset.y()))));
        }
        int shadowPadding = shadowPadding(positionedLayers, scale);
        int imageSize = layersSize + 2 * shadowPadding;
        Point layersTopLeft = new Point(center.x() - layersSize / 2.0,
                center.y() - layersSize / 2.0);
        placeLayers(positionedLayers, layersTopLeft, layersSize, layersSize,
                new Point(layersTopLeft.x() - shadowPadding, layersTopLeft.y() - shadowPadding),
                scale);
        applyIndicator(scale, lastSelectedHintColors);
        QImage image = render(imageSize, imageSize);
        IndicatorImage indicatorImage = indicatorImage(image);
        image.dispose();
        return indicatorImage;
    }

    private QImage render(int width, int height) {
        QImage image = new QImage(width, height, QImage.Format.Format_ARGB32_Premultiplied);
        image.fill(0);
        QPainter painter = new QPainter(image);
        List<DrawnLayer> layers = drawnLayers.subList(0, currentLayers.size());
        if (!shadowCasts.isEmpty())
            drawShadows(painter, layers);
        if (shadowCasts.isEmpty() || indicatorHasTransparency()) {
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
            painter.translate(layersArea.x(), layersArea.y());
            if (!shadowCasts.isEmpty()) {
                painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_DestinationOut);
                drawLayers(painter, layers, true);
                painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
            }
            drawLayers(painter, layers, false);
        }
        painter.end();
        painter.dispose();
        return image;
    }

    private void drawShadows(QPainter painter, List<DrawnLayer> layers) {
        QImage source = shadowSource(layers);
        for (ShadowCast shadowCast : shadowCasts) {
            Shadow shadow = shadowCast.shadow();
            Shadows.drawShadow(painter,
                    shadowCast.drawnLayers().size() == layers.size() ? new QImage(source) :
                            shadowSource(shadowCast.drawnLayers()),
                    shadowSourceArea.x(), shadowSourceArea.y(), shadow.blurRadius() * shadowScale,
                    shadow.horizontalOffset() * shadowScale, shadow.verticalOffset() * shadowScale,
                    shadowCast.color(), shadowCast.brush(), shadow.stackCount(), true);
        }
        painter.drawImage(shadowSourceArea.x(), shadowSourceArea.y(), source);
        source.dispose();
    }

    private QImage shadowSource(List<DrawnLayer> layers) {
        QImage source = new QImage(shadowSourceArea.width(), shadowSourceArea.height(),
                QImage.Format.Format_ARGB32_Premultiplied);
        source.fill(0);
        QPainter painter = new QPainter(source);
        painter.setRenderHint(QPainter.RenderHint.Antialiasing, true);
        painter.translate(layersArea.x() - shadowSourceArea.x(),
                layersArea.y() - shadowSourceArea.y());
        drawLayers(painter, layers, true);
        painter.end();
        painter.dispose();
        return source;
    }

    private static void drawLayers(QPainter painter, List<DrawnLayer> layers, boolean opaque) {
        for (DrawnLayer layer : layers)
            layer.draw(painter, opaque);
    }

    public static IndicatorImage indicatorImage(QImage image) {
        int[] argb = new int[image.width() * image.height()];
        ByteBuffer buffer = image.bits();
        buffer.position(0);
        buffer.order(ByteOrder.nativeOrder()).asIntBuffer().get(argb);
        return new IndicatorImage(argb, image.width(), image.height());
    }

    /** Applies the indicator to the drawn layers (shape, fill, stroke) and the shadows without
     *  positioning them. Shared by the window image and the cursor image renders. */
    private void applyIndicator(double shadowScale, LastSelectedHintColors lastSelectedHintColors) {
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
        applyShadows(shadowScale, lastSelectedHintColors);
        for (int i = 0; i < currentLayers.size(); i++)
            applyLayer(currentLayers.get(i).layer(), drawnLayers.get(i),
                    currentLayers.get(i).topLeft(),
                    gradientPoint(currentLayers.get(i), shadowScale), lastSelectedHintColors);
    }

    private void applyLayer(IndicatorLayerConfiguration layer, DrawnLayer drawnLayer,
                            Point topLeft, Point gradientPoint,
                            LastSelectedHintColors lastSelectedHintColors) {
        drawnLayer.setSweepArea(sweepArea(topLeft));
        drawnLayer.setLayer(layer);
        GradientColor fillColor = layer.fillColor().gradientColor(lastSelectedHintColors);
        drawnLayer.setFill(QtColorUtil.qColor(hex(fillColor, gradientPoint), layer.fillOpacity()),
                sweep(fillColor));
        IndicatorStroke stroke = layer.stroke();
        GradientColor strokeColor = stroke.color().gradientColor(lastSelectedHintColors);
        drawnLayer.setStroke(QtColorUtil.qColor(hex(strokeColor, gradientPoint), stroke.opacity()),
                sweep(strokeColor));
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
    private record ShadowCast(Shadow shadow, List<DrawnLayer> drawnLayers, QColor color,
                              QBrush brush) {
    }

    private void applyShadows(double scale, LastSelectedHintColors lastSelectedHintColors) {
        for (ShadowCast shadowCast : shadowCasts)
            shadowCast.color().dispose();
        shadowCasts.clear();
        shadowScale = scale;
        Map<Shadow, Map<String, List<DrawnLayer>>> drawnLayersByHexColorByShadow =
                new LinkedHashMap<>();
        for (int i = 0; i < currentLayers.size(); i++) {
            Shadow shadow = currentLayers.get(i).layer().shadow();
            if (shadow.opacity() > 0 && shadow.blurRadius() > 0)
                drawnLayersByHexColorByShadow
                        .computeIfAbsent(shadow, shadow_ -> new LinkedHashMap<>())
                        .computeIfAbsent(hex(shadow.color().gradientColor(lastSelectedHintColors),
                                        gradientPoint(currentLayers.get(i), scale)),
                                hexColor -> new ArrayList<>())
                        .add(drawnLayers.get(i));
        }
        if (!drawnLayersByHexColorByShadow.isEmpty()) {
            double left = 0, right = 0, top = 0, bottom = 0;
            for (Map.Entry<Shadow, Map<String, List<DrawnLayer>>> entry :
                    drawnLayersByHexColorByShadow.entrySet()) {
                Shadow shadow = entry.getKey();
                GradientColor shadowSweep =
                        sweep(shadow.color().gradientColor(lastSelectedHintColors));
                for (Map.Entry<String, List<DrawnLayer>> hexColorEntry :
                        entry.getValue().entrySet()) {
                    QColor baseColor = QtColorUtil.qColor(hexColorEntry.getKey(), 1.0);
                    QColor shadowColor = new QColor(baseColor.red(), baseColor.green(),
                            baseColor.blue(), (int) Math.round(shadow.opacity() * 255));
                    baseColor.dispose();
                    shadowCasts.add(new ShadowCast(shadow, hexColorEntry.getValue(), shadowColor,
                            shadowSweep == null ? null :
                                    brush(shadowColor, shadowSweep, sweepArea(layersTopLeft()))));
                }
                double radius = shadow.blurRadius() * scale;
                left = Math.max(left, radius - shadow.horizontalOffset() * scale);
                right = Math.max(right, radius + shadow.horizontalOffset() * scale);
                top = Math.max(top, radius - shadow.verticalOffset() * scale);
                bottom = Math.max(bottom, radius + shadow.verticalOffset() * scale);
            }
            double radius = Math.max(left + right, top + bottom) / 2;
            QRectF layers = new QRectF(layersArea.x(), layersArea.y(), layersArea.width(),
                    layersArea.height());
            QRectF shadows = layers.translated((right - left) / 2, (bottom - top) / 2)
                                   .adjusted(-radius, -radius, radius, radius);
            QRectF layersAndShadows = layers.united(shadows);
            QRect source = layersAndShadows.toAlignedRect();
            shadowSourceArea =
                    new Rectangle(source.x(), source.y(), source.width(), source.height());
            layers.dispose();
            shadows.dispose();
            layersAndShadows.dispose();
            source.dispose();
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

    private class DrawnLayer {

        private Rectangle rectangle;
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
                                int width, int height, int fillRgba, int strokeRgba,
                                Point subPixelOffset) {
        }

        void setRectangle(Rectangle rectangle) {
            this.rectangle = rectangle;
        }

        int x() {
            return rectangle.x();
        }

        int y() {
            return rectangle.y();
        }

        int width() {
            return rectangle.width();
        }

        int height() {
            return rectangle.height();
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

        /** The shape fills a box centered in the layer: size is the box's largest dimension
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
            ImageKey key = new ImageKey(layer, layerSize, scale, width(), height(),
                    fillColor.rgba(), strokeColor.rgba(), subPixelOffset);
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
                image = new QImage(width() + 2 * miterReach(), height() + 2 * miterReach(),
                        QImage.Format.Format_ARGB32_Premultiplied);
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

        void draw(QPainter painter, boolean opaque) {
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
            return new QColor(color.red(), color.green(), color.blue());
        }
    }

}
