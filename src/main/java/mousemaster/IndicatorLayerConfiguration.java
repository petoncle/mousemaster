package mousemaster;

import mousemaster.FontStyle.FontStyleBuilder;
import mousemaster.IndicatorStroke.IndicatorStrokeBuilder;

import java.util.List;

public record IndicatorLayerConfiguration(boolean enabled, int z,
                                          IndicatorShape shape, double size,
                                          double aspectRatio, double borderRadius,
                                          List<Point> points, Color fillColor,
                                          double fillOpacity,
                                          IndicatorStroke stroke,
                                          boolean labelEnabled, String labelText,
                                          FontStyle labelFontStyle,
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
        private Color fillColor;
        private Double fillOpacity;
        private IndicatorStrokeBuilder stroke = new IndicatorStrokeBuilder();
        private Boolean labelEnabled;
        private String labelText;
        private FontStyleBuilder labelFontStyle = new FontStyleBuilder();
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
            this.fillColor = layer.fillColor;
            this.fillOpacity = layer.fillOpacity;
            this.stroke = new IndicatorStrokeBuilder(layer.stroke);
            this.labelEnabled = layer.labelEnabled;
            this.labelText = layer.labelText;
            this.labelFontStyle = new FontStyleBuilder(layer.labelFontStyle);
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

        public IndicatorLayerConfigurationBuilder labelEnabled(boolean labelEnabled) {
            this.labelEnabled = labelEnabled;
            return this;
        }

        public Boolean labelEnabled() {
            return labelEnabled;
        }

        public IndicatorLayerConfigurationBuilder labelText(String labelText) {
            this.labelText = labelText;
            return this;
        }

        public String labelText() {
            return labelText;
        }

        public FontStyleBuilder labelFontStyle() {
            return labelFontStyle;
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
            if (fillColor == null) fillColor = parent.fillColor;
            if (fillOpacity == null) fillOpacity = parent.fillOpacity;
            stroke.extend(parent.stroke);
            if (labelEnabled == null) labelEnabled = parent.labelEnabled;
            if (labelText == null) labelText = parent.labelText;
            labelFontStyle.extend(parent.labelFontStyle);
            if (position == null) position = parent.position;
            if (followMouse == null) followMouse = parent.followMouse;
        }

        public IndicatorLayerConfiguration build() {
            return new IndicatorLayerConfiguration(enabled, z, shape, size, aspectRatio,
                    borderRadius, points, fillColor, fillOpacity, stroke.build(),
                    labelEnabled, labelText, labelFontStyle.build(), position, followMouse);
        }
    }
}
