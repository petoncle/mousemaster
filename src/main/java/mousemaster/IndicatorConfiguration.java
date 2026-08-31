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
                                     IndicatorSwitchAt transitionAnimationSwitchAt,
                                     boolean renderAsCursor,
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

    /** The indicator part way from one to the other: the sizes and opacities are eased, and
     *  what cannot be eased is switched, at the start or at the end. */
    public static IndicatorConfiguration lerp(IndicatorConfiguration from,
                                              IndicatorConfiguration to, double t) {
        return lerp(from, to,
                to.transitionAnimationSwitchAt == IndicatorSwitchAt.START ? to : from, t);
    }

    /** A copy of this indicator taking its colors, label and position from the given one. */
    public IndicatorConfiguration switching(IndicatorConfiguration switched) {
        return lerp(this, switched, switched, 0);
    }

    private static IndicatorConfiguration lerp(IndicatorConfiguration from,
                                               IndicatorConfiguration to,
                                               IndicatorConfiguration switched, double t) {
        return new IndicatorConfiguration(to.enabled, to.fadeAnimationEnabled,
                to.fadeAnimationDuration, to.transitionAnimationDuration,
                to.transitionAnimationEasing, to.transitionAnimationSwitchAt,
                to.renderAsCursor, (int) Math.round(lerp(from.size, to.size, t)),
                lerpEdgeCount(from.edgeCount, to.edgeCount, t),
                switched.color, lerp(from.opacity, to.opacity, t),
                lerp(from.outerOutline, to.outerOutline, switched.outerOutline, t),
                lerp(from.innerOutline, to.innerOutline, switched.innerOutline, t),
                lerp(from.shadow, to.shadow, switched.shadow, t), switched.labelEnabled,
                switched.labelText,
                lerp(from.labelFontStyle, to.labelFontStyle, switched.labelFontStyle, t),
                switched.position);
    }

    private static IndicatorOutline lerp(IndicatorOutline from, IndicatorOutline to,
                                         IndicatorOutline switched, double t) {
        return new IndicatorOutline(lerp(from.thickness(), to.thickness(), t),
                switched.color(), lerp(from.opacity(), to.opacity(), t),
                lerp(from.fillPercent(), to.fillPercent(), t), switched.fillStartAngle(),
                switched.fillDirection());
    }

    private static Shadow lerp(Shadow from, Shadow to, Shadow switched, double t) {
        return new Shadow(lerp(from.blurRadius(), to.blurRadius(), t), switched.color(),
                lerp(from.opacity(), to.opacity(), t),
                lerp(from.horizontalOffset(), to.horizontalOffset(), t),
                lerp(from.verticalOffset(), to.verticalOffset(), t), switched.stackCount());
    }

    private static FontStyle lerp(FontStyle from, FontStyle to, FontStyle switched, double t) {
        return new FontStyle(switched.name(), switched.weight(),
                lerp(from.size(), to.size(), t), switched.color(),
                lerp(from.opacity(), to.opacity(), t),
                lerp(from.outlineThickness(), to.outlineThickness(), t),
                switched.outlineColor(), lerp(from.outlineOpacity(), to.outlineOpacity(), t),
                lerp(from.shadow(), to.shadow(), switched.shadow(), t),
                switched.verticalAlignment());
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
        private IndicatorSwitchAt transitionAnimationSwitchAt;
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
            this.fadeAnimationEnabled = indicator.fadeAnimationEnabled;
            this.fadeAnimationDuration = indicator.fadeAnimationDuration;
            this.transitionAnimationDuration = indicator.transitionAnimationDuration;
            this.transitionAnimationEasing = indicator.transitionAnimationEasing;
            this.transitionAnimationSwitchAt = indicator.transitionAnimationSwitchAt;
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

        public IndicatorConfigurationBuilder transitionAnimationSwitchAt(IndicatorSwitchAt transitionAnimationSwitchAt) {
            this.transitionAnimationSwitchAt = transitionAnimationSwitchAt;
            return this;
        }

        public IndicatorSwitchAt transitionAnimationSwitchAt() {
            return transitionAnimationSwitchAt;
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
            if (fadeAnimationEnabled == null) fadeAnimationEnabled = parent.fadeAnimationEnabled;
            if (fadeAnimationDuration == null) fadeAnimationDuration = parent.fadeAnimationDuration;
            if (transitionAnimationDuration == null) transitionAnimationDuration = parent.transitionAnimationDuration;
            if (transitionAnimationEasing == null) transitionAnimationEasing = parent.transitionAnimationEasing;
            if (transitionAnimationSwitchAt == null) transitionAnimationSwitchAt = parent.transitionAnimationSwitchAt;
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
            return new IndicatorConfiguration(enabled, fadeAnimationEnabled,
                    fadeAnimationDuration, transitionAnimationDuration,
                    transitionAnimationEasing, transitionAnimationSwitchAt,
                    renderAsCursor, size, edgeCount, color,
                    opacity, outerOutline.build(), innerOutline.build(), shadow.build(),
                    labelEnabled, labelText, labelFontStyle.build(), position);
        }
    }
}
