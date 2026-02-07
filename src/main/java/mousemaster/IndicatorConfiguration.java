package mousemaster;

import mousemaster.FontStyle.FontStyleBuilder;
import mousemaster.IndicatorOutline.IndicatorOutlineBuilder;
import mousemaster.Shadow.ShadowBuilder;

public record IndicatorConfiguration(boolean enabled, int size, int edgeCount,
                                     String idleHexColor, String moveHexColor,
                                     String wheelHexColor,
                                     String mousePressHexColor,
                                     String leftMousePressHexColor,
                                     String middleMousePressHexColor,
                                     String rightMousePressHexColor,
                                     String unhandledKeyPressHexColor,
                                     double opacity,
                                     IndicatorOutline firstOutline,
                                     IndicatorOutline secondOutline,
                                     Shadow shadow,
                                     boolean labelEnabled,
                                     String labelText, FontStyle labelFontStyle) {
    public static class IndicatorConfigurationBuilder {
        private Boolean enabled;
        private Integer size;
        private Integer edgeCount;
        private String idleHexColor;
        private String moveHexColor;
        private String wheelHexColor;
        private String mousePressHexColor;
        private String leftMousePressHexColor;
        private String middleMousePressHexColor;
        private String rightMousePressHexColor;
        private String unhandledKeyPressHexColor;
        private Double opacity;
        private IndicatorOutlineBuilder firstOutline = new IndicatorOutlineBuilder();
        private IndicatorOutlineBuilder secondOutline = new IndicatorOutlineBuilder();
        private ShadowBuilder shadow = new ShadowBuilder();
        private Boolean labelEnabled;
        private String labelText;
        private FontStyleBuilder labelFontStyle = new FontStyleBuilder();

        public IndicatorConfigurationBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public IndicatorConfigurationBuilder size(int size) {
            this.size = size;
            return this;
        }

        public IndicatorConfigurationBuilder edgeCount(int edgeCount) {
            this.edgeCount = edgeCount;
            return this;
        }

        public IndicatorConfigurationBuilder idleHexColor(String idleHexColor) {
            this.idleHexColor = idleHexColor;
            return this;
        }

        public IndicatorConfigurationBuilder moveHexColor(String moveHexColor) {
            this.moveHexColor = moveHexColor;
            return this;
        }

        public IndicatorConfigurationBuilder wheelHexColor(String wheelHexColor) {
            this.wheelHexColor = wheelHexColor;
            return this;
        }

        public IndicatorConfigurationBuilder mousePressHexColor(String mousePressHexColor) {
            this.mousePressHexColor = mousePressHexColor;
            return this;
        }

        public IndicatorConfigurationBuilder leftMousePressHexColor(String leftMousePressHexColor) {
            this.leftMousePressHexColor = leftMousePressHexColor;
            return this;
        }

        public IndicatorConfigurationBuilder middleMousePressHexColor(String middleMousePressHexColor) {
            this.middleMousePressHexColor = middleMousePressHexColor;
            return this;
        }

        public IndicatorConfigurationBuilder rightMousePressHexColor(String rightMousePressHexColor) {
            this.rightMousePressHexColor = rightMousePressHexColor;
            return this;
        }

        public IndicatorConfigurationBuilder unhandledKeyPressHexColor(
                String unhandledKeyPressHexColor) {
            this.unhandledKeyPressHexColor = unhandledKeyPressHexColor;
            return this;
        }

        public IndicatorConfigurationBuilder opacity(double opacity) {
            this.opacity = opacity;
            return this;
        }

        public Double opacity() {
            return opacity;
        }

        public IndicatorOutlineBuilder firstOutline() {
            return firstOutline;
        }

        public IndicatorOutlineBuilder secondOutline() {
            return secondOutline;
        }

        public ShadowBuilder shadow() {
            return shadow;
        }

        public Boolean enabled() {
            return enabled;
        }

        public Integer size() {
            return size;
        }

        public Integer edgeCount() {
            return edgeCount;
        }

        public String idleHexColor() {
            return idleHexColor;
        }

        public String moveHexColor() {
            return moveHexColor;
        }

        public String wheelHexColor() {
            return wheelHexColor;
        }

        public String mousePressHexColor() {
            return mousePressHexColor;
        }

        public String leftMousePressHexColor() {
            return leftMousePressHexColor;
        }

        public String middleMousePressHexColor() {
            return middleMousePressHexColor;
        }

        public String rightMousePressHexColor() {
            return rightMousePressHexColor;
        }

        public String unhandledKeyPressHexColor() {
            return unhandledKeyPressHexColor;
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

        public IndicatorConfiguration build() {
            return new IndicatorConfiguration(enabled, size, edgeCount, idleHexColor, moveHexColor, wheelHexColor,
                    mousePressHexColor,
                    leftMousePressHexColor,
                    middleMousePressHexColor,
                    rightMousePressHexColor,
                    unhandledKeyPressHexColor,
                    opacity,
                    firstOutline.build(),
                    secondOutline.build(),
                    shadow.build(),
                    labelEnabled, labelText, labelFontStyle.build());
        }

    }
}
