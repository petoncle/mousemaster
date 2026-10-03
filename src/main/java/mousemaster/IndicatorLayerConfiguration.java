package mousemaster;

import mousemaster.IndicatorStroke.IndicatorStrokeBuilder;

import java.util.List;

public record IndicatorLayerConfiguration(boolean enabled, int z,
                                          IndicatorShape shape, double size,
                                          double aspectRatio, double borderRadius,
                                          List<Point> points, double x, double y,
                                          double rotation, Color fillColor,
                                          double fillOpacity,
                                          IndicatorStroke stroke,
                                          String text, String fontName, double fontSize,
                                          FontWeight fontWeight,
                                          IndicatorPosition position, boolean followMouse) {

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
        private Double rotation;
        private Color fillColor;
        private Double fillOpacity;
        private IndicatorStrokeBuilder stroke = new IndicatorStrokeBuilder();
        private String text;
        private String fontName;
        private Double fontSize;
        private FontWeight fontWeight;
        private IndicatorPosition position;
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
            this.rotation = layer.rotation;
            this.fillColor = layer.fillColor;
            this.fillOpacity = layer.fillOpacity;
            this.stroke = new IndicatorStrokeBuilder(layer.stroke);
            this.text = layer.text;
            this.fontName = layer.fontName;
            this.fontSize = layer.fontSize;
            this.fontWeight = layer.fontWeight;
            this.position = layer.position;
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

        public IndicatorLayerConfigurationBuilder position(IndicatorPosition position) {
            this.position = position;
            return this;
        }

        public IndicatorPosition position() {
            return position;
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
            if (rotation == null) rotation = parent.rotation;
            if (fillColor == null) fillColor = parent.fillColor;
            if (fillOpacity == null) fillOpacity = parent.fillOpacity;
            stroke.extend(parent.stroke);
            if (text == null) text = parent.text;
            if (fontName == null) fontName = parent.fontName;
            if (fontSize == null) fontSize = parent.fontSize;
            if (fontWeight == null) fontWeight = parent.fontWeight;
            if (position == null) position = parent.position;
            if (followMouse == null) followMouse = parent.followMouse;
        }

        public IndicatorLayerConfiguration build() {
            return new IndicatorLayerConfiguration(enabled, z, shape, size, aspectRatio,
                    borderRadius, points, x, y, rotation, fillColor, fillOpacity, stroke.build(),
                    text, fontName, fontSize, fontWeight, position, followMouse);
        }
    }
}
