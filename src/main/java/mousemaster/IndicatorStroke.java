package mousemaster;

public record IndicatorStroke(double thickness, Color color, double opacity,
                              double startAngle, double lengthPercent, StrokeAnchor anchor) {

    public static class IndicatorStrokeBuilder {

        private Double thickness;
        private Color color;
        private Double opacity;
        private Double startAngle;
        private Double lengthPercent;
        private StrokeAnchor anchor;

        public IndicatorStrokeBuilder() {
        }

        public IndicatorStrokeBuilder(IndicatorStroke stroke) {
            this.thickness = stroke.thickness;
            this.color = stroke.color;
            this.opacity = stroke.opacity;
            this.startAngle = stroke.startAngle;
            this.lengthPercent = stroke.lengthPercent;
            this.anchor = stroke.anchor;
        }

        public Double thickness() {
            return thickness;
        }

        public Color color() {
            return color;
        }

        public Double opacity() {
            return opacity;
        }

        public Double startAngle() {
            return startAngle;
        }

        public Double lengthPercent() {
            return lengthPercent;
        }

        public StrokeAnchor anchor() {
            return anchor;
        }

        public IndicatorStrokeBuilder thickness(double thickness) {
            this.thickness = thickness;
            return this;
        }

        public IndicatorStrokeBuilder color(Color color) {
            this.color = color;
            return this;
        }

        public IndicatorStrokeBuilder opacity(double opacity) {
            this.opacity = opacity;
            return this;
        }

        public IndicatorStrokeBuilder startAngle(double startAngle) {
            this.startAngle = startAngle;
            return this;
        }

        public IndicatorStrokeBuilder lengthPercent(double lengthPercent) {
            this.lengthPercent = lengthPercent;
            return this;
        }

        public IndicatorStrokeBuilder anchor(StrokeAnchor anchor) {
            this.anchor = anchor;
            return this;
        }

        public void extend(IndicatorStrokeBuilder parent) {
            if (thickness == null) thickness = parent.thickness;
            if (color == null) color = parent.color;
            if (opacity == null) opacity = parent.opacity;
            if (startAngle == null) startAngle = parent.startAngle;
            if (lengthPercent == null) lengthPercent = parent.lengthPercent;
            if (anchor == null) anchor = parent.anchor;
        }

        public IndicatorStroke build() {
            return new IndicatorStroke(thickness, color, opacity, startAngle, lengthPercent, anchor);
        }

    }

}
