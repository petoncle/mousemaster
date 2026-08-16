package mousemaster;

import mousemaster.FontStyle.FontStyleBuilder;
import mousemaster.IndicatorOutline.IndicatorOutlineBuilder;
import mousemaster.Shadow.ShadowBuilder;

import java.time.Duration;

public record IndicatorConfiguration(boolean enabled,
                                     boolean fadeAnimationEnabled,
                                     Duration fadeAnimationDuration,
                                     Duration transitionAnimationDuration,
                                     Easing transitionAnimationEasing,
                                     double transitionAnimationOvershoot,
                                     IndicatorColorChange transitionAnimationColorChange,
                                     boolean renderAsCursor,
                                     int size, int edgeCount, String hexColor,
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

    public static IndicatorConfiguration lerp(IndicatorConfiguration from,
                                              IndicatorConfiguration to, double t) {
        IndicatorConfiguration colored =
                to.transitionAnimationColorChange == IndicatorColorChange.IMMEDIATE ? to : from;
        return new IndicatorConfiguration(to.enabled, to.fadeAnimationEnabled,
                to.fadeAnimationDuration, to.transitionAnimationDuration,
                to.transitionAnimationEasing, to.transitionAnimationOvershoot,
                to.transitionAnimationColorChange, to.renderAsCursor,
                (int) Math.round(lerp(from.size, to.size, t)),
                lerpEdgeCount(from.edgeCount, to.edgeCount, t),
                colored.hexColor, lerp(from.opacity, to.opacity, t),
                lerp(from.outerOutline, to.outerOutline, colored.outerOutline, t),
                lerp(from.innerOutline, to.innerOutline, colored.innerOutline, t),
                lerp(from.shadow, to.shadow, colored.shadow, t), to.labelEnabled,
                to.labelText,
                lerp(from.labelFontStyle, to.labelFontStyle, colored.labelFontStyle, t),
                to.position);
    }

    private static IndicatorOutline lerp(IndicatorOutline from, IndicatorOutline to,
                                         IndicatorOutline colored, double t) {
        return new IndicatorOutline(lerp(from.thickness(), to.thickness(), t),
                colored.hexColor(), lerp(from.opacity(), to.opacity(), t),
                lerp(from.fillPercent(), to.fillPercent(), t), to.fillStartAngle(),
                to.fillDirection());
    }

    private static Shadow lerp(Shadow from, Shadow to, Shadow colored, double t) {
        return new Shadow(lerp(from.blurRadius(), to.blurRadius(), t), colored.hexColor(),
                lerp(from.opacity(), to.opacity(), t),
                lerp(from.horizontalOffset(), to.horizontalOffset(), t),
                lerp(from.verticalOffset(), to.verticalOffset(), t), to.stackCount());
    }

    private static FontStyle lerp(FontStyle from, FontStyle to, FontStyle colored, double t) {
        return new FontStyle(to.name(), to.weight(), lerp(from.size(), to.size(), t),
                colored.hexColor(), lerp(from.opacity(), to.opacity(), t),
                lerp(from.outlineThickness(), to.outlineThickness(), t),
                colored.outlineHexColor(), lerp(from.outlineOpacity(), to.outlineOpacity(), t),
                lerp(from.shadow(), to.shadow(), colored.shadow(), t), to.verticalAlignment());
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    /** The polygon puts a vertex at the top for an odd edge count and a flat edge for an even
     *  one, so the morph steps two edges at a time: changing parity would rock the shape. */
    private static int lerpEdgeCount(int from, int to, double t) {
        return from + 2 * (int) Math.round((lerp(from, to, t) - from) / 2);
    }

    public static class IndicatorConfigurationBuilder {

        private Boolean enabled;
        private Boolean fadeAnimationEnabled;
        private Duration fadeAnimationDuration;
        private Duration transitionAnimationDuration;
        private Easing transitionAnimationEasing;
        private Double transitionAnimationOvershoot;
        private IndicatorColorChange transitionAnimationColorChange;
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

        public IndicatorConfigurationBuilder() {
        }

        public IndicatorConfigurationBuilder(IndicatorConfiguration indicator) {
            this.enabled = indicator.enabled;
            this.fadeAnimationEnabled = indicator.fadeAnimationEnabled;
            this.fadeAnimationDuration = indicator.fadeAnimationDuration;
            this.transitionAnimationDuration = indicator.transitionAnimationDuration;
            this.transitionAnimationEasing = indicator.transitionAnimationEasing;
            this.transitionAnimationOvershoot = indicator.transitionAnimationOvershoot;
            this.transitionAnimationColorChange = indicator.transitionAnimationColorChange;
            this.renderAsCursor = indicator.renderAsCursor;
            this.size = indicator.size;
            this.edgeCount = indicator.edgeCount;
            this.hexColor = indicator.hexColor;
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

        public IndicatorConfigurationBuilder transitionAnimationDuration(Duration transitionAnimationDuration) {
            this.transitionAnimationDuration = transitionAnimationDuration;
            return this;
        }

        public Duration transitionAnimationDuration() {
            return transitionAnimationDuration;
        }

        public IndicatorConfigurationBuilder transitionAnimationEasing(Easing transitionAnimationEasing) {
            this.transitionAnimationEasing = transitionAnimationEasing;
            return this;
        }

        public Easing transitionAnimationEasing() {
            return transitionAnimationEasing;
        }

        public IndicatorConfigurationBuilder transitionAnimationOvershoot(double transitionAnimationOvershoot) {
            this.transitionAnimationOvershoot = transitionAnimationOvershoot;
            return this;
        }

        public Double transitionAnimationOvershoot() {
            return transitionAnimationOvershoot;
        }

        public IndicatorConfigurationBuilder transitionAnimationColorChange(IndicatorColorChange transitionAnimationColorChange) {
            this.transitionAnimationColorChange = transitionAnimationColorChange;
            return this;
        }

        public IndicatorColorChange transitionAnimationColorChange() {
            return transitionAnimationColorChange;
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
            if (transitionAnimationDuration == null) transitionAnimationDuration = parent.transitionAnimationDuration;
            if (transitionAnimationEasing == null) transitionAnimationEasing = parent.transitionAnimationEasing;
            if (transitionAnimationOvershoot == null) transitionAnimationOvershoot = parent.transitionAnimationOvershoot;
            if (transitionAnimationColorChange == null) transitionAnimationColorChange = parent.transitionAnimationColorChange;
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
                    fadeAnimationDuration, transitionAnimationDuration,
                    transitionAnimationEasing, transitionAnimationOvershoot,
                    transitionAnimationColorChange,
                    renderAsCursor, size, edgeCount, hexColor,
                    opacity, outerOutline.build(), innerOutline.build(), shadow.build(),
                    labelEnabled, labelText, labelFontStyle.build(), position);
        }
    }
}
