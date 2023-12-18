package jmouseable.jmouseable;

public record IndicatorConfiguration(boolean enabled, int size, String idleHexColor, String moveHexColor,
                                     String wheelHexColor, String mousePressHexColor,
                                     String unhandledKeyPressHexColor) {
    public static class IndicatorConfigurationBuilder {
        private Boolean enabled;
        private Integer size;
        private String idleHexColor;
        private String moveHexColor;
        private String wheelHexColor;
        private String mousePressHexColor;
        private String unhandledKeyPressHexColor;

        public IndicatorConfigurationBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public IndicatorConfigurationBuilder size(int size) {
            this.size = size;
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

        public IndicatorConfigurationBuilder unhandledKeyPressHexColor(
                String unhandledKeyPressHexColor) {
            this.unhandledKeyPressHexColor = unhandledKeyPressHexColor;
            return this;
        }

        public Boolean enabled() {
            return enabled;
        }
        public Integer size() {
            return size;
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

        public String unhandledKeyPressHexColor() {
            return unhandledKeyPressHexColor;
        }

        public IndicatorConfiguration build() {
            return new IndicatorConfiguration(enabled, size, idleHexColor, moveHexColor, wheelHexColor,
                    mousePressHexColor, unhandledKeyPressHexColor);
        }

    }
}
