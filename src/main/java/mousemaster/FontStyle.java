package mousemaster;

public record FontStyle(String name, FontWeight weight,
                        double size, double spacingPercent, String hexColor,
                        double opacity,
                        String selectedFontHexColor,
                        double selectedFontOpacity,
                        String focusedFontHexColor,
                        double focusedFontOpacity,
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
        private Double opacity;
        private String selectedFontHexColor;
        private Double selectedFontOpacity;
        private String focusedFontHexColor;
        private Double focusedFontOpacity;
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
            this.opacity = fontStyle.opacity;
            this.selectedFontHexColor = fontStyle.selectedFontHexColor;
            this.selectedFontOpacity = fontStyle.selectedFontOpacity;
            this.focusedFontHexColor = fontStyle.focusedFontHexColor;
            this.focusedFontOpacity = fontStyle.focusedFontOpacity;
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
            return opacity;
        }

        public String selectedFontHexColor() {
            return selectedFontHexColor;
        }

        public Double selectedFontOpacity() {
            return selectedFontOpacity;
        }

        public String focusedFontHexColor() {
            return focusedFontHexColor;
        }

        public Double focusedFontOpacity() {
            return focusedFontOpacity;
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
            this.opacity = fontOpacity;
            return this;
        }

        public FontStyleBuilder selectedFontHexColor(String selectedFontHexColor) {
            this.selectedFontHexColor = selectedFontHexColor;
            return this;
        }

        public FontStyleBuilder selectedFontOpacity(Double selectedFontOpacity) {
            this.selectedFontOpacity = selectedFontOpacity;
            return this;
        }

        public FontStyleBuilder focusedFontHexColor(String focusedFontHexColor) {
            this.focusedFontHexColor = focusedFontHexColor;
            return this;
        }

        public FontStyleBuilder focusedFontOpacity(Double focusedFontOpacity) {
            this.focusedFontOpacity = focusedFontOpacity;
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

        void extend(FontStyleBuilder defaultStyle) {
            if (name == null) name = defaultStyle.name;
            if (weight == null) weight = defaultStyle.weight;
            if (size == null) size = defaultStyle.size;
            if (spacingPercent == null) spacingPercent = defaultStyle.spacingPercent;
            if (hexColor == null) hexColor = defaultStyle.hexColor;
            if (opacity == null) opacity = defaultStyle.opacity;
            if (selectedFontHexColor == null) selectedFontHexColor = defaultStyle.selectedFontHexColor;
            if (selectedFontOpacity == null) selectedFontOpacity = defaultStyle.selectedFontOpacity;
            if (focusedFontHexColor == null) focusedFontHexColor = defaultStyle.focusedFontHexColor;
            if (focusedFontOpacity == null) focusedFontOpacity = defaultStyle.focusedFontOpacity;
            if (outlineThickness == null) outlineThickness = defaultStyle.outlineThickness;
            if (outlineHexColor == null) outlineHexColor = defaultStyle.outlineHexColor;
            if (outlineOpacity == null) outlineOpacity = defaultStyle.outlineOpacity;
            if (shadowBlurRadius == null) shadowBlurRadius = defaultStyle.shadowBlurRadius;
            if (shadowHexColor == null) shadowHexColor = defaultStyle.shadowHexColor;
            if (shadowOpacity == null) shadowOpacity = defaultStyle.shadowOpacity;
            if (shadowHorizontalOffset == null) shadowHorizontalOffset = defaultStyle.shadowHorizontalOffset;
            if (shadowVerticalOffset == null) shadowVerticalOffset = defaultStyle.shadowVerticalOffset;
        }

        void extendSelectedAndFocusedFromMain() {
            if (selectedFontHexColor == null) selectedFontHexColor = hexColor;
            if (selectedFontOpacity == null) selectedFontOpacity = opacity;
            if (focusedFontHexColor == null) focusedFontHexColor = hexColor;
            if (focusedFontOpacity == null) focusedFontOpacity = opacity;
        }

        public FontStyle build() {
            return new FontStyle(
                    name,
                    weight,
                    size,
                    spacingPercent,
                    hexColor,
                    opacity,
                    selectedFontHexColor,
                    selectedFontOpacity,
                    focusedFontHexColor,
                    focusedFontOpacity,
                    outlineThickness,
                    outlineHexColor,
                    outlineOpacity,
                    shadowBlurRadius,
                    shadowHexColor,
                    shadowOpacity,
                    shadowHorizontalOffset,
                    shadowVerticalOffset
            );
        }

    }

}
