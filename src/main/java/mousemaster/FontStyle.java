package mousemaster;

public record FontStyle(String fontName, FontWeight fontWeight,
                        double fontSize, double fontSpacingPercent, String fontHexColor,
                        double fontOpacity,
                        double fontOutlineThickness, String fontOutlineHexColor,
                        double fontOutlineOpacity,
                        double fontShadowBlurRadius, String fontShadowHexColor,
                        double fontShadowOpacity, double fontShadowHorizontalOffset,
                        double fontShadowVerticalOffset) {

    public FontStyleBuilder builder() {
        return new FontStyleBuilder(this);
    }

    public static class FontStyleBuilder {

        private String fontName;
        private FontWeight fontWeight;
        private Double fontSize;
        private Double fontSpacingPercent;
        private String fontHexColor;
        private Double fontOpacity;
        private Double fontOutlineThickness;
        private String fontOutlineHexColor;
        private Double fontOutlineOpacity;
        private Double fontShadowBlurRadius;
        private String fontShadowHexColor;
        private Double fontShadowOpacity;
        private Double fontShadowHorizontalOffset;
        private Double fontShadowVerticalOffset;

        public FontStyleBuilder() {

        }

        public FontStyleBuilder(FontStyle fontStyle) {
            this.fontName = fontStyle.fontName;
            this.fontWeight = fontStyle.fontWeight;
            this.fontSize = fontStyle.fontSize;
            this.fontSpacingPercent = fontStyle.fontSpacingPercent;
            this.fontHexColor = fontStyle.fontHexColor;
            this.fontOpacity = fontStyle.fontOpacity;
            this.fontOutlineThickness = fontStyle.fontOutlineThickness;
            this.fontOutlineHexColor = fontStyle.fontOutlineHexColor;
            this.fontOutlineOpacity = fontStyle.fontOutlineOpacity;
            this.fontShadowBlurRadius = fontStyle.fontShadowBlurRadius;
            this.fontShadowHexColor = fontStyle.fontShadowHexColor;
            this.fontShadowOpacity = fontStyle.fontShadowOpacity;
            this.fontShadowHorizontalOffset = fontStyle.fontShadowHorizontalOffset;
            this.fontShadowVerticalOffset = fontStyle.fontShadowVerticalOffset;
        }

        public String fontName() {
            return fontName;
        }

        public FontWeight fontWeight() {
            return fontWeight;
        }

        public Double fontSize() {
            return fontSize;
        }

        public Double fontSpacingPercent() {
            return fontSpacingPercent;
        }

        public String fontHexColor() {
            return fontHexColor;
        }

        public Double fontOpacity() {
            return fontOpacity;
        }

        public Double fontOutlineThickness() {
            return fontOutlineThickness;
        }

        public String fontOutlineHexColor() {
            return fontOutlineHexColor;
        }

        public Double fontOutlineOpacity() {
            return fontOutlineOpacity;
        }

        public Double fontShadowBlurRadius() {
            return fontShadowBlurRadius;
        }

        public String fontShadowHexColor() {
            return fontShadowHexColor;
        }

        public Double fontShadowOpacity() {
            return fontShadowOpacity;
        }

        public Double fontShadowHorizontalOffset() {
            return fontShadowHorizontalOffset;
        }

        public Double fontShadowVerticalOffset() {
            return fontShadowVerticalOffset;
        }

        public FontStyleBuilder fontName(String fontName) {
            this.fontName = fontName;
            return this;
        }

        public FontStyleBuilder fontWeight(FontWeight fontWeight) {
            this.fontWeight = fontWeight;
            return this;
        }

        public FontStyleBuilder fontSize(Double fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        public FontStyleBuilder fontSpacingPercent(Double fontSpacingPercent) {
            this.fontSpacingPercent = fontSpacingPercent;
            return this;
        }

        public FontStyleBuilder fontHexColor(String fontHexColor) {
            this.fontHexColor = fontHexColor;
            return this;
        }

        public FontStyleBuilder fontOpacity(Double fontOpacity) {
            this.fontOpacity = fontOpacity;
            return this;
        }

        public FontStyleBuilder fontOutlineThickness(Double fontOutlineThickness) {
            this.fontOutlineThickness = fontOutlineThickness;
            return this;
        }

        public FontStyleBuilder fontOutlineHexColor(String fontOutlineHexColor) {
            this.fontOutlineHexColor = fontOutlineHexColor;
            return this;
        }

        public FontStyleBuilder fontOutlineOpacity(Double fontOutlineOpacity) {
            this.fontOutlineOpacity = fontOutlineOpacity;
            return this;
        }

        public FontStyleBuilder fontShadowBlurRadius(Double fontShadowBlurRadius) {
            this.fontShadowBlurRadius = fontShadowBlurRadius;
            return this;
        }

        public FontStyleBuilder fontShadowHexColor(String fontShadowHexColor) {
            this.fontShadowHexColor = fontShadowHexColor;
            return this;
        }

        public FontStyleBuilder fontShadowOpacity(Double fontShadowOpacity) {
            this.fontShadowOpacity = fontShadowOpacity;
            return this;
        }

        public FontStyleBuilder fontShadowHorizontalOffset(
                Double fontShadowHorizontalOffset) {
            this.fontShadowHorizontalOffset = fontShadowHorizontalOffset;
            return this;
        }

        public FontStyleBuilder fontShadowVerticalOffset(
                Double fontShadowVerticalOffset) {
            this.fontShadowVerticalOffset = fontShadowVerticalOffset;
            return this;
        }

        public FontStyle build(FontStyle defaultStyle) {
            return new FontStyle(
                    fontName == null ? defaultStyle.fontName : fontName,
                    fontWeight == null ? defaultStyle.fontWeight : fontWeight,
                    fontSize == null ? defaultStyle.fontSize : fontSize,
                    fontSpacingPercent == null ? defaultStyle.fontSpacingPercent : fontSpacingPercent,
                    fontHexColor == null ? defaultStyle.fontHexColor : fontHexColor,
                    fontOpacity == null ? defaultStyle.fontOpacity : fontOpacity,
                    fontOutlineThickness == null ? defaultStyle.fontOutlineThickness : fontOutlineThickness,
                    fontOutlineHexColor == null ? defaultStyle.fontOutlineHexColor : fontOutlineHexColor,
                    fontOutlineOpacity == null ? defaultStyle.fontOutlineOpacity : fontOutlineOpacity,
                    fontShadowBlurRadius == null ? defaultStyle.fontShadowBlurRadius : fontShadowBlurRadius,
                    fontShadowHexColor == null ? defaultStyle.fontShadowHexColor : fontShadowHexColor,
                    fontShadowOpacity == null ? defaultStyle.fontShadowOpacity : fontShadowOpacity,
                    fontShadowHorizontalOffset == null ? defaultStyle.fontShadowHorizontalOffset : fontShadowHorizontalOffset,
                    fontShadowVerticalOffset == null ? defaultStyle.fontShadowVerticalOffset : fontShadowVerticalOffset
            );
        }

    }

}
