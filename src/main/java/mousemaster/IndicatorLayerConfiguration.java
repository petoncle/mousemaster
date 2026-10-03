package mousemaster;

import mousemaster.FontStyle.FontStyleBuilder;
import mousemaster.IndicatorOutline.IndicatorOutlineBuilder;

public record IndicatorLayerConfiguration(boolean enabled, int z,
                                          int size, int edgeCount, Color color,
                                          double opacity,
                                          IndicatorOutline outerOutline,
                                          IndicatorOutline innerOutline,
                                          boolean labelEnabled, String labelText,
                                          FontStyle labelFontStyle,
                                          IndicatorPosition position) {

    public IndicatorLayerConfigurationBuilder builder() {
        return new IndicatorLayerConfigurationBuilder(this);
    }

    public static class IndicatorLayerConfigurationBuilder {

        private Boolean enabled;
        private Integer z;
        private Integer size;
        private Integer edgeCount;
        private Color color;
        private Double opacity;
        private IndicatorOutlineBuilder outerOutline = new IndicatorOutlineBuilder();
        private IndicatorOutlineBuilder innerOutline = new IndicatorOutlineBuilder();
        private Boolean labelEnabled;
        private String labelText;
        private FontStyleBuilder labelFontStyle = new FontStyleBuilder();
        private IndicatorPosition position;

        public IndicatorLayerConfigurationBuilder() {
        }

        public IndicatorLayerConfigurationBuilder(IndicatorLayerConfiguration layer) {
            this.enabled = layer.enabled;
            this.z = layer.z;
            this.size = layer.size;
            this.edgeCount = layer.edgeCount;
            this.color = layer.color;
            this.opacity = layer.opacity;
            this.outerOutline = new IndicatorOutlineBuilder(layer.outerOutline);
            this.innerOutline = new IndicatorOutlineBuilder(layer.innerOutline);
            this.labelEnabled = layer.labelEnabled;
            this.labelText = layer.labelText;
            this.labelFontStyle = new FontStyleBuilder(layer.labelFontStyle);
            this.position = layer.position;
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

        public IndicatorLayerConfigurationBuilder size(int size) {
            this.size = size;
            return this;
        }

        public Integer size() {
            return size;
        }

        public IndicatorLayerConfigurationBuilder edgeCount(int edgeCount) {
            this.edgeCount = edgeCount;
            return this;
        }

        public Integer edgeCount() {
            return edgeCount;
        }

        public IndicatorLayerConfigurationBuilder color(Color color) {
            this.color = color;
            return this;
        }

        public Color color() {
            return color;
        }

        public IndicatorLayerConfigurationBuilder opacity(double opacity) {
            this.opacity = opacity;
            return this;
        }

        public Double opacity() {
            return opacity;
        }

        public IndicatorOutlineBuilder outerOutline() {
            return outerOutline;
        }

        public IndicatorOutlineBuilder innerOutline() {
            return innerOutline;
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

        public void extend(IndicatorLayerConfigurationBuilder parent) {
            if (enabled == null) enabled = parent.enabled;
            if (z == null) z = parent.z;
            if (size == null) size = parent.size;
            if (edgeCount == null) edgeCount = parent.edgeCount;
            if (color == null) color = parent.color;
            if (opacity == null) opacity = parent.opacity;
            outerOutline.extend(parent.outerOutline);
            innerOutline.extend(parent.innerOutline);
            if (labelEnabled == null) labelEnabled = parent.labelEnabled;
            if (labelText == null) labelText = parent.labelText;
            labelFontStyle.extend(parent.labelFontStyle);
            if (position == null) position = parent.position;
        }

        public IndicatorLayerConfiguration build() {
            return new IndicatorLayerConfiguration(enabled, z, size, edgeCount, color,
                    opacity, outerOutline.build(), innerOutline.build(),
                    labelEnabled, labelText, labelFontStyle.build(), position);
        }
    }
}
