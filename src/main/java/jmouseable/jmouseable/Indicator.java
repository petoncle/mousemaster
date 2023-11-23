package jmouseable.jmouseable;

public record Indicator(boolean enabled, String idleHexColor, String moveHexColor,
                        String wheelHexColor, String mousePressHexColor,
                        String nonComboKeyPressHexColor) {
    public static class IndicatorBuilder {
        private Boolean enabled;
        private String idleHexColor;
        private String moveHexColor;
        private String wheelHexColor;
        private String mousePressHexColor;
        private String nonComboKeyPressHexColor;

        public IndicatorBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public IndicatorBuilder idleHexColor(String idleHexColor) {
            this.idleHexColor = idleHexColor;
            return this;
        }

        public IndicatorBuilder moveHexColor(String moveHexColor) {
            this.moveHexColor = moveHexColor;
            return this;
        }

        public IndicatorBuilder wheelHexColor(String wheelHexColor) {
            this.wheelHexColor = wheelHexColor;
            return this;
        }

        public IndicatorBuilder mousePressHexColor(String mousePressHexColor) {
            this.mousePressHexColor = mousePressHexColor;
            return this;
        }

        public IndicatorBuilder nonComboKeyPressHexColor(
                String nonComboKeyPressHexColor) {
            this.nonComboKeyPressHexColor = nonComboKeyPressHexColor;
            return this;
        }

        public Boolean enabled() {
            return enabled;
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

        public String nonComboKeyPressHexColor() {
            return nonComboKeyPressHexColor;
        }

        public Indicator build() {
            return new Indicator(enabled, idleHexColor, moveHexColor, wheelHexColor,
                    mousePressHexColor, nonComboKeyPressHexColor);
        }
    }
}
