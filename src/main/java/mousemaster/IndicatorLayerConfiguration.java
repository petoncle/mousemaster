package mousemaster;

import mousemaster.HintGridArea.HintGridAreaBuilder;
import mousemaster.IndicatorStroke.IndicatorStrokeBuilder;
import mousemaster.Shadow.ShadowBuilder;

import java.util.List;

public record IndicatorLayerConfiguration(boolean enabled, int z,
                                          IndicatorShape shape, double size,
                                          double aspectRatio, double borderRadius,
                                          List<Point> points, double x, double y,
                                          double distance, double angle,
                                          double rotation, Color fillColor,
                                          double fillOpacity,
                                          IndicatorStroke stroke, Shadow shadow,
                                          String text, String fontName, double fontSize,
                                          FontWeight fontWeight, String textStyleSheet,
                                          List<IndicatorPosition> positions,
                                          HintGridArea area, boolean followMouse) {

    public IndicatorLayerConfigurationBuilder builder() {
        return new IndicatorLayerConfigurationBuilder(this);
    }

    public static class IndicatorLayerConfigurationBuilder {

        private Boolean enabled;
        private Integer z;
        private Double size;
        private IndicatorShape shape;
        private Double aspectRatio;
        private Double borderRadius;
        private List<Point> points;
        private Double x;
        private Double y;
        private Double distance;
        private Double angle;
        private Double rotation;
        private Color fillColor;
        private Double fillOpacity;
        private IndicatorStrokeBuilder stroke = new IndicatorStrokeBuilder();
        private ShadowBuilder shadow = new ShadowBuilder();
        private String text;
        private String fontName;
        private Double fontSize;
        private FontWeight fontWeight;
        private String textStyleSheet;
        private List<IndicatorPosition> positions;
        private HintGridAreaBuilder area = new HintGridAreaBuilder();
        private Boolean followMouse;

        public IndicatorLayerConfigurationBuilder() {
        }

        public IndicatorLayerConfigurationBuilder(IndicatorLayerConfiguration layer) {
            this.enabled = layer.enabled;
            this.z = layer.z;
            this.size = layer.size;
            this.shape = layer.shape;
            this.aspectRatio = layer.aspectRatio;
            this.borderRadius = layer.borderRadius;
            this.points = layer.points;
            this.x = layer.x;
            this.y = layer.y;
            this.distance = layer.distance;
            this.angle = layer.angle;
            this.rotation = layer.rotation;
            this.fillColor = layer.fillColor;
            this.fillOpacity = layer.fillOpacity;
            this.stroke = new IndicatorStrokeBuilder(layer.stroke);
            this.shadow = new ShadowBuilder(layer.shadow);
            this.text = layer.text;
            this.fontName = layer.fontName;
            this.fontSize = layer.fontSize;
            this.fontWeight = layer.fontWeight;
            this.textStyleSheet = layer.textStyleSheet;
            this.positions = layer.positions;
            this.area = layer.area == null ? new HintGridAreaBuilder() : layer.area.builder();
            this.followMouse = layer.followMouse;
        }

        public IndicatorLayerConfigurationBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Boolean enabled() {
            return enabled;
        }

        public IndicatorLayerConfigurationBuilder z(int z) {
            this.z = z;
            return this;
        }

        public Integer z() {
            return z;
        }

        public IndicatorLayerConfigurationBuilder size(double size) {
            this.size = size;
            return this;
        }

        public Double size() {
            return size;
        }

        public IndicatorLayerConfigurationBuilder shape(IndicatorShape shape) {
            this.shape = shape;
            return this;
        }

        public IndicatorShape shape() {
            return shape;
        }

        public IndicatorLayerConfigurationBuilder aspectRatio(double aspectRatio) {
            this.aspectRatio = aspectRatio;
            return this;
        }

        public Double aspectRatio() {
            return aspectRatio;
        }

        public IndicatorLayerConfigurationBuilder borderRadius(double borderRadius) {
            this.borderRadius = borderRadius;
            return this;
        }

        public Double borderRadius() {
            return borderRadius;
        }

        public IndicatorLayerConfigurationBuilder points(List<Point> points) {
            this.points = points;
            return this;
        }

        public List<Point> points() {
            return points;
        }

        public IndicatorLayerConfigurationBuilder x(double x) {
            this.x = x;
            return this;
        }

        public Double x() {
            return x;
        }

        public IndicatorLayerConfigurationBuilder y(double y) {
            this.y = y;
            return this;
        }

        public Double y() {
            return y;
        }

        public IndicatorLayerConfigurationBuilder distance(double distance) {
            this.distance = distance;
            return this;
        }

        public Double distance() {
            return distance;
        }

        public IndicatorLayerConfigurationBuilder angle(double angle) {
            this.angle = angle;
            return this;
        }

        public Double angle() {
            return angle;
        }

        public IndicatorLayerConfigurationBuilder rotation(double rotation) {
            this.rotation = rotation;
            return this;
        }

        public Double rotation() {
            return rotation;
        }

        public IndicatorLayerConfigurationBuilder fillColor(Color fillColor) {
            this.fillColor = fillColor;
            return this;
        }

        public Color fillColor() {
            return fillColor;
        }

        public IndicatorLayerConfigurationBuilder fillOpacity(double fillOpacity) {
            this.fillOpacity = fillOpacity;
            return this;
        }

        public Double fillOpacity() {
            return fillOpacity;
        }

        public IndicatorStrokeBuilder stroke() {
            return stroke;
        }

        public ShadowBuilder shadow() {
            return shadow;
        }

        public IndicatorLayerConfigurationBuilder text(String text) {
            this.text = text;
            return this;
        }

        public String text() {
            return text;
        }

        public IndicatorLayerConfigurationBuilder fontName(String fontName) {
            this.fontName = fontName;
            return this;
        }

        public String fontName() {
            return fontName;
        }

        public IndicatorLayerConfigurationBuilder fontSize(double fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        public Double fontSize() {
            return fontSize;
        }

        public IndicatorLayerConfigurationBuilder fontWeight(FontWeight fontWeight) {
            this.fontWeight = fontWeight;
            return this;
        }

        public FontWeight fontWeight() {
            return fontWeight;
        }

        public IndicatorLayerConfigurationBuilder textStyleSheet(String textStyleSheet) {
            this.textStyleSheet = textStyleSheet;
            return this;
        }

        public String textStyleSheet() {
            return textStyleSheet;
        }

        public IndicatorLayerConfigurationBuilder positions(List<IndicatorPosition> positions) {
            this.positions = positions;
            return this;
        }

        public List<IndicatorPosition> positions() {
            return positions;
        }

        public HintGridAreaBuilder area() {
            return area;
        }

        public IndicatorLayerConfigurationBuilder followMouse(boolean followMouse) {
            this.followMouse = followMouse;
            return this;
        }

        public Boolean followMouse() {
            return followMouse;
        }

        public void extend(IndicatorLayerConfigurationBuilder parent) {
            if (enabled == null) enabled = parent.enabled;
            if (z == null) z = parent.z;
            if (size == null) size = parent.size;
            if (shape == null) shape = parent.shape;
            if (aspectRatio == null) aspectRatio = parent.aspectRatio;
            if (borderRadius == null) borderRadius = parent.borderRadius;
            if (points == null) points = parent.points;
            if (x == null) x = parent.x;
            if (y == null) y = parent.y;
            if (distance == null) distance = parent.distance;
            if (angle == null) angle = parent.angle;
            if (rotation == null) rotation = parent.rotation;
            if (fillColor == null) fillColor = parent.fillColor;
            if (fillOpacity == null) fillOpacity = parent.fillOpacity;
            stroke.extend(parent.stroke);
            shadow.extend(parent.shadow);
            if (text == null) text = parent.text;
            if (fontName == null) fontName = parent.fontName;
            if (fontSize == null) fontSize = parent.fontSize;
            if (fontWeight == null) fontWeight = parent.fontWeight;
            if (textStyleSheet == null) textStyleSheet = parent.textStyleSheet;
            if (positions == null) positions = parent.positions;
            area.extend(parent.area);
            if (followMouse == null) followMouse = parent.followMouse;
        }

        public IndicatorLayerConfiguration build() {
            return new IndicatorLayerConfiguration(enabled, z, shape, size, aspectRatio,
                    borderRadius, points, x, y, distance, angle, rotation, fillColor, fillOpacity,
                    stroke.build(), shadow.build(),
                    text, fontName, fontSize, fontWeight, textStyleSheet, positions,
                    area.source() == null ? null : area.build(),
                    followMouse && area.source() == null &&
                    !positions.contains(IndicatorPosition.PointIndicatorPosition.TARGET) &&
                    !positions.contains(
                            IndicatorPosition.PointIndicatorPosition.LAST_SELECTED_HINT) &&
                    positions.stream().noneMatch(
                            IndicatorPosition.PositionHistoryIndicatorPosition.class::isInstance));
        }
    }
}
