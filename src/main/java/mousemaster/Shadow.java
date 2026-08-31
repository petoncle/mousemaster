package mousemaster;

public record Shadow(double blurRadius, Color color, double opacity,
                     double horizontalOffset, double verticalOffset,
                     int stackCount) {

    public static class ShadowBuilder {

        private Double blurRadius;
        private Color color;
        private Double opacity;
        private Double horizontalOffset;
        private Double verticalOffset;
        private Integer stackCount;

        public ShadowBuilder() {
        }

        public ShadowBuilder(Shadow shadow) {
            this.blurRadius = shadow.blurRadius;
            this.color = shadow.color;
            this.opacity = shadow.opacity;
            this.horizontalOffset = shadow.horizontalOffset;
            this.verticalOffset = shadow.verticalOffset;
            this.stackCount = shadow.stackCount;
        }

        public Double blurRadius() {
            return blurRadius;
        }

        public Color color() {
            return color;
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

        public ShadowBuilder color(Color color) {
            this.color = color;
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
            if (color == null) color = defaultShadow.color;
            if (opacity == null) opacity = defaultShadow.opacity;
            if (horizontalOffset == null) horizontalOffset = defaultShadow.horizontalOffset;
            if (verticalOffset == null) verticalOffset = defaultShadow.verticalOffset;
            if (stackCount == null) stackCount = defaultShadow.stackCount;
        }

        public Shadow build() {
            return new Shadow(blurRadius, color, opacity, horizontalOffset, verticalOffset,
                    stackCount);
        }

    }

}
