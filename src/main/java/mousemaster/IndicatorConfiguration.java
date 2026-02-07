package mousemaster;

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
                                     double outlineThickness, String outlineHexColor,
                                     double outlineOpacity,
                                     Shadow shadow) {
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
        private Double outlineThickness;
        private String outlineHexColor;
        private Double outlineOpacity;
        private ShadowBuilder shadow = new ShadowBuilder();

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

        public IndicatorConfigurationBuilder outlineThickness(double outlineThickness) {
            this.outlineThickness = outlineThickness;
            return this;
        }

        public IndicatorConfigurationBuilder outlineHexColor(String outlineHexColor) {
            this.outlineHexColor = outlineHexColor;
            return this;
        }

        public IndicatorConfigurationBuilder outlineOpacity(double outlineOpacity) {
            this.outlineOpacity = outlineOpacity;
            return this;
        }

        public Double outlineThickness() {
            return outlineThickness;
        }

        public String outlineHexColor() {
            return outlineHexColor;
        }

        public Double outlineOpacity() {
            return outlineOpacity;
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

        public IndicatorConfiguration build() {
            return new IndicatorConfiguration(enabled, size, edgeCount, idleHexColor, moveHexColor, wheelHexColor,
                    mousePressHexColor,
                    leftMousePressHexColor,
                    middleMousePressHexColor,
                    rightMousePressHexColor,
                    unhandledKeyPressHexColor,
                    opacity,
                    outlineThickness, outlineHexColor, outlineOpacity,
                    shadow.build());
        }

    }
}
