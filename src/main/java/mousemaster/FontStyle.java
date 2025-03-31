package mousemaster;

public record FontStyle(String name, FontWeight weight,
                        double size, double spacingPercent, String hexColor,
                        double opacity,
                        double outlineThickness, String outlineHexColor,
                        double outlineOpacity,
                        double shadowBlurRadius, String shadowHexColor,
                        double shadowOpacity, double shadowHorizontalOffset,
                        double shadowVerticalOffset) {

    public FontStyleBuilder builder() {
        return new FontStyleBuilder(this);
    }

    public static class FontStyleBuilder {

        private String name;
        private FontWeight weight;
        private Double size;
        private Double spacingPercent;
        private String hexColor;
        private Double hpacity;
        private Double outlineThickness;
        private String outlineHexColor;
        private Double outlineOpacity;
        private Double shadowBlurRadius;
        private String shadowHexColor;
        private Double shadowOpacity;
        private Double shadowHorizontalOffset;
        private Double shadowVerticalOffset;

        public FontStyleBuilder() {

        }

        public FontStyleBuilder(FontStyle fontStyle) {
            this.name = fontStyle.name;
            this.weight = fontStyle.weight;
            this.size = fontStyle.size;
            this.spacingPercent = fontStyle.spacingPercent;
            this.hexColor = fontStyle.hexColor;
            this.hpacity = fontStyle.opacity;
            this.outlineThickness = fontStyle.outlineThickness;
            this.outlineHexColor = fontStyle.outlineHexColor;
            this.outlineOpacity = fontStyle.outlineOpacity;
            this.shadowBlurRadius = fontStyle.shadowBlurRadius;
            this.shadowHexColor = fontStyle.shadowHexColor;
            this.shadowOpacity = fontStyle.shadowOpacity;
            this.shadowHorizontalOffset = fontStyle.shadowHorizontalOffset;
            this.shadowVerticalOffset = fontStyle.shadowVerticalOffset;
        }

        public String name() {
            return name;
        }

        public FontWeight weight() {
            return weight;
        }

        public Double size() {
            return size;
        }

        public Double spacingPercent() {
            return spacingPercent;
        }

        public String hexColor() {
            return hexColor;
        }

        public Double opacity() {
            return hpacity;
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

        public Double shadowBlurRadius() {
            return shadowBlurRadius;
        }

        public String shadowHexColor() {
            return shadowHexColor;
        }

        public Double shadowOpacity() {
            return shadowOpacity;
        }

        public Double shadowHorizontalOffset() {
            return shadowHorizontalOffset;
        }

        public Double shadowVerticalOffset() {
            return shadowVerticalOffset;
        }

        public FontStyleBuilder name(String fontName) {
            this.name = fontName;
            return this;
        }

        public FontStyleBuilder weight(FontWeight fontWeight) {
            this.weight = fontWeight;
            return this;
        }

        public FontStyleBuilder size(Double fontSize) {
            this.size = fontSize;
            return this;
        }

        public FontStyleBuilder spacingPercent(Double fontSpacingPercent) {
            this.spacingPercent = fontSpacingPercent;
            return this;
        }

        public FontStyleBuilder hexColor(String fontHexColor) {
            this.hexColor = fontHexColor;
            return this;
        }

        public FontStyleBuilder opacity(Double fontOpacity) {
            this.hpacity = fontOpacity;
            return this;
        }

        public FontStyleBuilder outlineThickness(Double fontOutlineThickness) {
            this.outlineThickness = fontOutlineThickness;
            return this;
        }

        public FontStyleBuilder outlineHexColor(String fontOutlineHexColor) {
            this.outlineHexColor = fontOutlineHexColor;
            return this;
        }

        public FontStyleBuilder outlineOpacity(Double fontOutlineOpacity) {
            this.outlineOpacity = fontOutlineOpacity;
            return this;
        }

        public FontStyleBuilder shadowBlurRadius(Double fontShadowBlurRadius) {
            this.shadowBlurRadius = fontShadowBlurRadius;
            return this;
        }

        public FontStyleBuilder shadowHexColor(String fontShadowHexColor) {
            this.shadowHexColor = fontShadowHexColor;
            return this;
        }

        public FontStyleBuilder shadowOpacity(Double fontShadowOpacity) {
            this.shadowOpacity = fontShadowOpacity;
            return this;
        }

        public FontStyleBuilder shadowHorizontalOffset(
                Double fontShadowHorizontalOffset) {
            this.shadowHorizontalOffset = fontShadowHorizontalOffset;
            return this;
        }

        public FontStyleBuilder shadowVerticalOffset(
                Double fontShadowVerticalOffset) {
            this.shadowVerticalOffset = fontShadowVerticalOffset;
            return this;
        }

        public FontStyle build(FontStyle defaultStyle) {
            return new FontStyle(
                    name == null ? defaultStyle.name : name,
                    weight == null ? defaultStyle.weight : weight,
                    size == null ? defaultStyle.size : size,
                    spacingPercent == null ? defaultStyle.spacingPercent : spacingPercent,
                    hexColor == null ? defaultStyle.hexColor : hexColor,
                    hpacity == null ? defaultStyle.opacity : hpacity,
                    outlineThickness == null ? defaultStyle.outlineThickness : outlineThickness,
                    outlineHexColor == null ? defaultStyle.outlineHexColor : outlineHexColor,
                    outlineOpacity == null ? defaultStyle.outlineOpacity : outlineOpacity,
                    shadowBlurRadius == null ? defaultStyle.shadowBlurRadius : shadowBlurRadius,
                    shadowHexColor == null ? defaultStyle.shadowHexColor : shadowHexColor,
                    shadowOpacity == null ? defaultStyle.shadowOpacity : shadowOpacity,
                    shadowHorizontalOffset == null ? defaultStyle.shadowHorizontalOffset : shadowHorizontalOffset,
                    shadowVerticalOffset == null ? defaultStyle.shadowVerticalOffset : shadowVerticalOffset
            );
        }

    }

}
