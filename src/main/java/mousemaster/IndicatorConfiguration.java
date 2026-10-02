package mousemaster;

import mousemaster.FontStyle.FontStyleBuilder;
import mousemaster.IndicatorOutline.IndicatorOutlineBuilder;
import mousemaster.Shadow.ShadowBuilder;

public record IndicatorConfiguration(boolean enabled, boolean renderAsCursor,
                                     int size, int edgeCount, Color color,
                                     double opacity,
                                     IndicatorOutline outerOutline,
                                     IndicatorOutline innerOutline,
                                     Shadow shadow,
                                     boolean labelEnabled, String labelText,
                                     FontStyle labelFontStyle,
                                     IndicatorPosition position) {

    public IndicatorConfigurationBuilder builder() {
        return new IndicatorConfigurationBuilder(this);
    }

    public static class IndicatorConfigurationBuilder {

        private Boolean enabled;
        private Boolean renderAsCursor;
        private Integer size;
        private Integer edgeCount;
        private Color color;
        private Double opacity;
        private IndicatorOutlineBuilder outerOutline = new IndicatorOutlineBuilder();
        private IndicatorOutlineBuilder innerOutline = new IndicatorOutlineBuilder();
        private ShadowBuilder shadow = new ShadowBuilder();
        private Boolean labelEnabled;
        private String labelText;
        private FontStyleBuilder labelFontStyle = new FontStyleBuilder();
        private IndicatorPosition position;

        public IndicatorConfigurationBuilder() {
        }

        public IndicatorConfigurationBuilder(IndicatorConfiguration indicator) {
            this.enabled = indicator.enabled;
            this.renderAsCursor = indicator.renderAsCursor;
            this.size = indicator.size;
            this.edgeCount = indicator.edgeCount;
            this.color = indicator.color;
            this.opacity = indicator.opacity;
            this.outerOutline = new IndicatorOutlineBuilder(indicator.outerOutline);
            this.innerOutline = new IndicatorOutlineBuilder(indicator.innerOutline);
            this.shadow = new ShadowBuilder(indicator.shadow);
            this.labelEnabled = indicator.labelEnabled;
            this.labelText = indicator.labelText;
            this.labelFontStyle = new FontStyleBuilder(indicator.labelFontStyle);
            this.position = indicator.position;
        }

        public IndicatorConfigurationBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Boolean enabled() {
            return enabled;
        }

        public IndicatorConfigurationBuilder renderAsCursor(boolean renderAsCursor) {
            this.renderAsCursor = renderAsCursor;
            return this;
        }

        public Boolean renderAsCursor() {
            return renderAsCursor;
        }

        public IndicatorConfigurationBuilder size(int size) {
            this.size = size;
            return this;
        }

        public Integer size() {
            return size;
        }

        public IndicatorConfigurationBuilder edgeCount(int edgeCount) {
            this.edgeCount = edgeCount;
            return this;
        }

        public Integer edgeCount() {
            return edgeCount;
        }

        public IndicatorConfigurationBuilder color(Color color) {
            this.color = color;
            return this;
        }

        public Color color() {
            return color;
        }

        public IndicatorConfigurationBuilder opacity(double opacity) {
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

        public ShadowBuilder shadow() {
            return shadow;
        }

        public IndicatorConfigurationBuilder labelEnabled(boolean labelEnabled) {
            this.labelEnabled = labelEnabled;
            return this;
        }

        public Boolean labelEnabled() {
            return labelEnabled;
        }

        public IndicatorConfigurationBuilder labelText(String labelText) {
            this.labelText = labelText;
            return this;
        }

        public String labelText() {
            return labelText;
        }

        public FontStyleBuilder labelFontStyle() {
            return labelFontStyle;
        }

        public IndicatorConfigurationBuilder position(IndicatorPosition position) {
            this.position = position;
            return this;
        }

        public IndicatorPosition position() {
            return position;
        }

        public void extend(IndicatorConfigurationBuilder parent) {
            if (enabled == null) enabled = parent.enabled;
            if (renderAsCursor == null) renderAsCursor = parent.renderAsCursor;
            if (size == null) size = parent.size;
            if (edgeCount == null) edgeCount = parent.edgeCount;
            if (color == null) color = parent.color;
            if (opacity == null) opacity = parent.opacity;
            outerOutline.extend(parent.outerOutline);
            innerOutline.extend(parent.innerOutline);
            shadow.extend(parent.shadow);
            if (labelEnabled == null) labelEnabled = parent.labelEnabled;
            if (labelText == null) labelText = parent.labelText;
            labelFontStyle.extend(parent.labelFontStyle);
            if (position == null) position = parent.position;
        }

        public IndicatorConfiguration build() {
            return new IndicatorConfiguration(enabled, renderAsCursor, size, edgeCount, color,
                    opacity, outerOutline.build(), innerOutline.build(), shadow.build(),
                    labelEnabled, labelText, labelFontStyle.build(), position);
        }
    }
}
