package mousemaster;

import mousemaster.FontStyle.FontStyleBuilder;
import mousemaster.IndicatorStroke.IndicatorStrokeBuilder;

public record IndicatorLayerConfiguration(boolean enabled, int z,
                                          double size, int edgeCount, Color fillColor,
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
        private Integer edgeCount;
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
            this.edgeCount = layer.edgeCount;
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

        public IndicatorLayerConfigurationBuilder edgeCount(int edgeCount) {
            this.edgeCount = edgeCount;
            return this;
        }

        public Integer edgeCount() {
            return edgeCount;
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
            if (edgeCount == null) edgeCount = parent.edgeCount;
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
            return new IndicatorLayerConfiguration(enabled, z, size, edgeCount, fillColor,
                    fillOpacity, stroke.build(),
                    labelEnabled, labelText, labelFontStyle.build(), position,
                    followMouse);
        }
    }
}
