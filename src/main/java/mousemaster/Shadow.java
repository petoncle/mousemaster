package mousemaster;

public record Shadow(double blurRadius, String hexColor, double opacity,
                     double horizontalOffset, double verticalOffset,
                     int stackCount) {

    public static class ShadowBuilder {

        private Double blurRadius;
        private String hexColor;
        private Double opacity;
        private Double horizontalOffset;
        private Double verticalOffset;
        private Integer stackCount;

        public ShadowBuilder() {
        }

        public ShadowBuilder(Shadow shadow) {
            this.blurRadius = shadow.blurRadius;
            this.hexColor = shadow.hexColor;
            this.opacity = shadow.opacity;
            this.horizontalOffset = shadow.horizontalOffset;
            this.verticalOffset = shadow.verticalOffset;
            this.stackCount = shadow.stackCount;
        }

        public Double blurRadius() {
            return blurRadius;
        }

        public String hexColor() {
            return hexColor;
        }

        public Double opacity() {
            return opacity;
        }

        public Double horizontalOffset() {
            return horizontalOffset;
        }

        public Double verticalOffset() {
            return verticalOffset;
        }

        public Integer stackCount() {
            return stackCount;
        }

        public ShadowBuilder blurRadius(Double blurRadius) {
            this.blurRadius = blurRadius;
            return this;
        }

        public ShadowBuilder hexColor(String hexColor) {
            this.hexColor = hexColor;
            return this;
        }

        public ShadowBuilder opacity(Double opacity) {
            this.opacity = opacity;
            return this;
        }

        public ShadowBuilder horizontalOffset(Double horizontalOffset) {
            this.horizontalOffset = horizontalOffset;
            return this;
        }

        public ShadowBuilder verticalOffset(Double verticalOffset) {
            this.verticalOffset = verticalOffset;
            return this;
        }

        public ShadowBuilder stackCount(Integer stackCount) {
            this.stackCount = stackCount;
            return this;
        }

        public void extend(ShadowBuilder defaultShadow) {
            if (blurRadius == null) blurRadius = defaultShadow.blurRadius;
            if (hexColor == null) hexColor = defaultShadow.hexColor;
            if (opacity == null) opacity = defaultShadow.opacity;
            if (horizontalOffset == null) horizontalOffset = defaultShadow.horizontalOffset;
            if (verticalOffset == null) verticalOffset = defaultShadow.verticalOffset;
            if (stackCount == null) stackCount = defaultShadow.stackCount;
        }

        public Shadow build() {
            return new Shadow(blurRadius, hexColor, opacity, horizontalOffset, verticalOffset,
                    stackCount);
        }

    }

}
