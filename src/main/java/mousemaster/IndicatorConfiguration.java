package mousemaster;

import mousemaster.FontStyle.FontStyleBuilder;
import mousemaster.IndicatorOutline.IndicatorOutlineBuilder;
import mousemaster.Shadow.ShadowBuilder;

import java.time.Duration;

public record IndicatorConfiguration(boolean enabled,
                                     boolean fadeAnimationEnabled,
                                     Duration fadeAnimationDuration,
                                     boolean renderAsCursor,
                                     int size, int edgeCount, String hexColor,
                                     double opacity,
                                     IndicatorOutline outerOutline,
                                     IndicatorOutline innerOutline,
                                     Shadow shadow,
                                     boolean labelEnabled, String labelText,
                                     FontStyle labelFontStyle,
                                     IndicatorPosition position) {

    public static class IndicatorConfigurationBuilder {

        private Boolean enabled;
        private Boolean fadeAnimationEnabled;
        private Duration fadeAnimationDuration;
        private Boolean renderAsCursor;
        private Integer size;
        private Integer edgeCount;
        private String hexColor;
        private Double opacity;
        private IndicatorOutlineBuilder outerOutline = new IndicatorOutlineBuilder();
        private IndicatorOutlineBuilder innerOutline = new IndicatorOutlineBuilder();
        private ShadowBuilder shadow = new ShadowBuilder();
        private Boolean labelEnabled;
        private String labelText;
        private FontStyleBuilder labelFontStyle = new FontStyleBuilder();
        private IndicatorPosition position;

        public IndicatorConfigurationBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Boolean enabled() {
            return enabled;
        }

        public IndicatorConfigurationBuilder fadeAnimationEnabled(boolean fadeAnimationEnabled) {
            this.fadeAnimationEnabled = fadeAnimationEnabled;
            return this;
        }

        public Boolean fadeAnimationEnabled() {
            return fadeAnimationEnabled;
        }

        public IndicatorConfigurationBuilder fadeAnimationDuration(Duration fadeAnimationDuration) {
            this.fadeAnimationDuration = fadeAnimationDuration;
            return this;
        }

        public Duration fadeAnimationDuration() {
            return fadeAnimationDuration;
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

        public IndicatorConfigurationBuilder hexColor(String hexColor) {
            this.hexColor = hexColor;
            return this;
        }

        public String hexColor() {
            return hexColor;
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
            if (fadeAnimationEnabled == null) fadeAnimationEnabled = parent.fadeAnimationEnabled;
            if (fadeAnimationDuration == null) fadeAnimationDuration = parent.fadeAnimationDuration;
            if (renderAsCursor == null) renderAsCursor = parent.renderAsCursor;
            if (size == null) size = parent.size;
            if (edgeCount == null) edgeCount = parent.edgeCount;
            if (hexColor == null) hexColor = parent.hexColor;
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
            return new IndicatorConfiguration(enabled, fadeAnimationEnabled,
                    fadeAnimationDuration, renderAsCursor, size, edgeCount, hexColor,
                    opacity, outerOutline.build(), innerOutline.build(), shadow.build(),
                    labelEnabled, labelText, labelFontStyle.build(), position);
        }
    }
}
