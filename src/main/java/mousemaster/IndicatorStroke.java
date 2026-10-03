package mousemaster;

public record IndicatorStroke(double thickness, Color color, double opacity,
                              double startAngle, double lengthPercent, StrokeAnchor anchor,
                              double dashLength, double dashGap, double dashOffset,
                              StrokeCap cap) {

    public static class IndicatorStrokeBuilder {

        private Double thickness;
        private Color color;
        private Double opacity;
        private Double startAngle;
        private Double lengthPercent;
        private StrokeAnchor anchor;
        private Double dashLength;
        private Double dashGap;
        private Double dashOffset;
        private StrokeCap cap;

        public IndicatorStrokeBuilder() {
        }

        public IndicatorStrokeBuilder(IndicatorStroke stroke) {
            this.thickness = stroke.thickness;
            this.color = stroke.color;
            this.opacity = stroke.opacity;
            this.startAngle = stroke.startAngle;
            this.lengthPercent = stroke.lengthPercent;
            this.anchor = stroke.anchor;
            this.dashLength = stroke.dashLength;
            this.dashGap = stroke.dashGap;
            this.dashOffset = stroke.dashOffset;
            this.cap = stroke.cap;
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

        public Double dashLength() {
            return dashLength;
        }

        public Double dashGap() {
            return dashGap;
        }

        public Double dashOffset() {
            return dashOffset;
        }

        public StrokeCap cap() {
            return cap;
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

        public IndicatorStrokeBuilder dashLength(double dashLength) {
            this.dashLength = dashLength;
            return this;
        }

        public IndicatorStrokeBuilder dashGap(double dashGap) {
            this.dashGap = dashGap;
            return this;
        }

        public IndicatorStrokeBuilder dashOffset(double dashOffset) {
            this.dashOffset = dashOffset;
            return this;
        }

        public IndicatorStrokeBuilder cap(StrokeCap cap) {
            this.cap = cap;
            return this;
        }

        public void extend(IndicatorStrokeBuilder parent) {
            if (thickness == null) thickness = parent.thickness;
            if (color == null) color = parent.color;
            if (opacity == null) opacity = parent.opacity;
            if (startAngle == null) startAngle = parent.startAngle;
            if (lengthPercent == null) lengthPercent = parent.lengthPercent;
            if (anchor == null) anchor = parent.anchor;
            if (dashLength == null) dashLength = parent.dashLength;
            if (dashGap == null) dashGap = parent.dashGap;
            if (dashOffset == null) dashOffset = parent.dashOffset;
            if (cap == null) cap = parent.cap;
        }

        public IndicatorStroke build() {
            return new IndicatorStroke(thickness, color, opacity, startAngle, lengthPercent,
                    anchor, dashLength, dashGap, dashOffset, cap);
        }

    }

}
