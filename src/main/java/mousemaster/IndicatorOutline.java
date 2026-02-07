package mousemaster;

public record IndicatorOutline(double thickness, String hexColor, double opacity,
                               double fillPercent) {

    public static class IndicatorOutlineBuilder {

        private Double thickness;
        private String hexColor;
        private Double opacity;
        private Double fillPercent;

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

        public void extend(IndicatorOutlineBuilder parent) {
            if (thickness == null) thickness = parent.thickness;
            if (hexColor == null) hexColor = parent.hexColor;
            if (opacity == null) opacity = parent.opacity;
            if (fillPercent == null) fillPercent = parent.fillPercent;
        }

        public IndicatorOutline build() {
            return new IndicatorOutline(thickness, hexColor, opacity, fillPercent);
        }

    }

}
