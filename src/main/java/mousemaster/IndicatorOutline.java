package mousemaster;

public record IndicatorOutline(double thickness, String hexColor, double opacity,
                               double fillPercent, double fillStartAngle,
                               FillDirection fillDirection) {

    public static class IndicatorOutlineBuilder {

        private Double thickness;
        private String hexColor;
        private Double opacity;
        private Double fillPercent;
        private Double fillStartAngle;
        private FillDirection fillDirection;

        public Double thickness() {
            return thickness;
        }

        public String hexColor() {
            return hexColor;
        }

        public Double opacity() {
            return opacity;
        }

        public Double fillPercent() {
            return fillPercent;
        }

        public Double fillStartAngle() {
            return fillStartAngle;
        }

        public FillDirection fillDirection() {
            return fillDirection;
        }

        public IndicatorOutlineBuilder thickness(double thickness) {
            this.thickness = thickness;
            return this;
        }

        public IndicatorOutlineBuilder hexColor(String hexColor) {
            this.hexColor = hexColor;
            return this;
        }

        public IndicatorOutlineBuilder opacity(double opacity) {
            this.opacity = opacity;
            return this;
        }

        public IndicatorOutlineBuilder fillPercent(double fillPercent) {
            this.fillPercent = fillPercent;
            return this;
        }

        public IndicatorOutlineBuilder fillStartAngle(double fillStartAngle) {
            this.fillStartAngle = fillStartAngle;
            return this;
        }

        public IndicatorOutlineBuilder fillDirection(FillDirection fillDirection) {
            this.fillDirection = fillDirection;
            return this;
        }

        public void extend(IndicatorOutlineBuilder parent) {
            if (thickness == null) thickness = parent.thickness;
            if (hexColor == null) hexColor = parent.hexColor;
            if (opacity == null) opacity = parent.opacity;
            if (fillPercent == null) fillPercent = parent.fillPercent;
            if (fillStartAngle == null) fillStartAngle = parent.fillStartAngle;
            if (fillDirection == null) fillDirection = parent.fillDirection;
        }

        public IndicatorOutline build() {
            return new IndicatorOutline(thickness, hexColor, opacity, fillPercent,
                    fillStartAngle, fillDirection);
        }

    }

}
