package mousemaster;

public record HintMeshStyle(String fontName,
                            double fontSize, double fontSpacingPercent, String fontHexColor, double fontOpacity,
                            double fontOutlineThickness, String fontOutlineHexColor, double fontOutlineOpacity,
                            double fontShadowThickness, double fontShadowStep, String fontShadowHexColor, double fontShadowOpacity, double fontShadowHorizontalOffset, double fontShadowVerticalOffset,
                            String prefixFontHexColor,
                            double highlightFontScale,
                            String boxHexColor,
                            double boxOpacity,
                            double boxBorderThickness,
                            double boxBorderLength,
                            String boxBorderHexColor,
                            double boxBorderOpacity,
                            boolean expandBoxes,
                            int subgridRowCount,
                            int subgridColumnCount,
                            double subgridBorderThickness,
                            double subgridBorderLength,
                            String subgridBorderHexColor,
                            double subgridBorderOpacity) {

    public HintMeshStyleBuilder builder() {
        return new HintMeshStyleBuilder(this);
    }

    public static class HintMeshStyleBuilder {
        private String fontName;
        private Double fontSize;
        private Double fontSpacingPercent;
        private String fontHexColor;
        private Double fontOpacity;
        private Double fontOutlineThickness;
        private String fontOutlineHexColor;
        private Double fontOutlineOpacity;
        private Double fontShadowThickness;
        private Double fontShadowStep;
        private String fontShadowHexColor;
        private Double fontShadowOpacity;
        private Double fontShadowHorizontalOffset;
        private Double fontShadowVerticalOffset;
        private String prefixFontHexColor;
        private Double highlightFontScale;
        private String boxHexColor;
        private Double boxOpacity;
        private Double boxBorderThickness;
        private Double boxBorderLength;
        private String boxBorderHexColor;
        private Double boxBorderOpacity;
        private Boolean expandBoxes;
        private Integer subgridRowCount;
        private Integer subgridColumnCount;
        private Double subgridBorderThickness;
        private Double subgridBorderLength;
        private String subgridBorderHexColor;
        private Double subgridBorderOpacity;

        public HintMeshStyleBuilder() {

        }

        public HintMeshStyleBuilder(HintMeshStyle style) {
            this.fontName = style.fontName;
            this.fontSize = style.fontSize;
            this.fontSpacingPercent = style.fontSpacingPercent;
            this.fontHexColor = style.fontHexColor;
            this.fontOpacity = style.fontOpacity;
            this.fontOutlineThickness = style.fontOutlineThickness;
            this.fontOutlineHexColor = style.fontOutlineHexColor;
            this.fontOutlineOpacity = style.fontOutlineOpacity;
            this.fontShadowThickness = style.fontShadowThickness;
            this.fontShadowStep = style.fontShadowStep;
            this.fontShadowHexColor = style.fontShadowHexColor;
            this.fontShadowOpacity = style.fontShadowOpacity;
            this.fontShadowHorizontalOffset = style.fontShadowHorizontalOffset;
            this.fontShadowVerticalOffset = style.fontShadowVerticalOffset;
            this.prefixFontHexColor = style.prefixFontHexColor;
            this.highlightFontScale = style.highlightFontScale;
            this.boxHexColor = style.boxHexColor;
            this.boxOpacity = style.boxOpacity;
            this.boxBorderThickness = style.boxBorderThickness;
            this.boxBorderLength = style.boxBorderLength;
            this.boxBorderHexColor = style.boxBorderHexColor;
            this.boxBorderOpacity = style.boxBorderOpacity;
            this.expandBoxes = style.expandBoxes;
            this.subgridRowCount = style.subgridRowCount;
            this.subgridColumnCount = style.subgridColumnCount;
            this.subgridBorderThickness = style.subgridBorderThickness;
            this.subgridBorderLength = style.subgridBorderLength;
            this.subgridBorderHexColor = style.subgridBorderHexColor;
            this.subgridBorderOpacity = style.subgridBorderOpacity;
        }

        public HintMeshStyleBuilder fontName(String fontName) {
            this.fontName = fontName;
            return this;
        }

        public HintMeshStyleBuilder fontSize(double fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        public HintMeshStyleBuilder fontSpacingPercent(double fontSpacingPercent) {
            this.fontSpacingPercent = fontSpacingPercent;
            return this;
        }

        public HintMeshStyleBuilder fontHexColor(String fontHexColor) {
            this.fontHexColor = fontHexColor;
            return this;
        }

        public HintMeshStyleBuilder fontOpacity(double fontOpacity) {
            this.fontOpacity = fontOpacity;
            return this;
        }

        public HintMeshStyleBuilder fontOutlineThickness(double fontOutlineThickness) {
            this.fontOutlineThickness = fontOutlineThickness;
            return this;
        }

        public HintMeshStyleBuilder fontOutlineHexColor(String fontOutlineHexColor) {
            this.fontOutlineHexColor = fontOutlineHexColor;
            return this;
        }

        public HintMeshStyleBuilder fontOutlineOpacity(double fontOutlineOpacity) {
            this.fontOutlineOpacity = fontOutlineOpacity;
            return this;
        }

        public HintMeshStyleBuilder fontShadowThickness(double fontShadowThickness) {
            this.fontShadowThickness = fontShadowThickness;
            return this;
        }

        public HintMeshStyleBuilder fontShadowStep(double fontShadowStep) {
            this.fontShadowStep = fontShadowStep;
            return this;
        }

        public HintMeshStyleBuilder fontShadowHexColor(String fontShadowHexColor) {
            this.fontShadowHexColor = fontShadowHexColor;
            return this;
        }

        public HintMeshStyleBuilder fontShadowOpacity(double fontShadowOpacity) {
            this.fontShadowOpacity = fontShadowOpacity;
            return this;
        }

        public HintMeshStyleBuilder fontShadowHorizontalOffset(double fontShadowHorizontalOffset) {
            this.fontShadowHorizontalOffset = fontShadowHorizontalOffset;
            return this;
        }

        public HintMeshStyleBuilder fontShadowVerticalOffset(double fontShadowVerticalOffset) {
            this.fontShadowVerticalOffset = fontShadowVerticalOffset;
            return this;
        }

        public HintMeshStyleBuilder prefixFontHexColor(
                String prefixFontHexColor) {
            this.prefixFontHexColor = prefixFontHexColor;
            return this;
        }

        public HintMeshStyleBuilder highlightFontScale(
                Double highlightFontScale) {
            this.highlightFontScale = highlightFontScale;
            return this;
        }

        public HintMeshStyleBuilder boxHexColor(String boxHexColor) {
            this.boxHexColor = boxHexColor;
            return this;
        }

        public HintMeshStyleBuilder boxOpacity(double boxOpacity) {
            this.boxOpacity = boxOpacity;
            return this;
        }

        public HintMeshStyleBuilder boxBorderThickness(double boxBorderThickness) {
            this.boxBorderThickness = boxBorderThickness;
            return this;
        }

        public HintMeshStyleBuilder boxBorderLength(double boxBorderLength) {
            this.boxBorderLength = boxBorderLength;
            return this;
        }

        public HintMeshStyleBuilder boxBorderHexColor(String boxBorderHexColor) {
            this.boxBorderHexColor = boxBorderHexColor;
            return this;
        }

        public HintMeshStyleBuilder boxBorderOpacity(double boxBorderOpacity) {
            this.boxBorderOpacity = boxBorderOpacity;
            return this;
        }

        public HintMeshStyleBuilder expandBoxes(boolean expandBoxes) {
            this.expandBoxes = expandBoxes;
            return this;
        }

        public HintMeshStyleBuilder subgridRowCount(int subgridRowCount) {
            this.subgridRowCount = subgridRowCount;
            return this;
        }

        public HintMeshStyleBuilder subgridColumnCount(int subgridColumnCount) {
            this.subgridColumnCount = subgridColumnCount;
            return this;
        }

        public HintMeshStyleBuilder subgridBorderThickness(double subgridBorderThickness) {
            this.subgridBorderThickness = subgridBorderThickness;
            return this;
        }

        public HintMeshStyleBuilder subgridBorderLength(double subgridBorderLength) {
            this.subgridBorderLength = subgridBorderLength;
            return this;
        }

        public HintMeshStyleBuilder subgridBorderHexColor(String subgridBorderHexColor) {
            this.subgridBorderHexColor = subgridBorderHexColor;
            return this;
        }

        public HintMeshStyleBuilder subgridBorderOpacity(double subgridBorderOpacity) {
            this.subgridBorderOpacity = subgridBorderOpacity;
            return this;
        }

        public String fontName() {
            return fontName;
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

        public Double fontShadowThickness() {
            return fontShadowThickness;
        }

        public Double fontShadowStep() {
            return fontShadowStep;
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

        public String prefixFontHexColor() {
            return prefixFontHexColor;
        }

        public Double highlightFontScale() {
            return highlightFontScale;
        }

        public String boxHexColor() {
            return boxHexColor;
        }

        public Double boxOpacity() {
            return boxOpacity;
        }

        public Double boxBorderThickness() {
            return boxBorderThickness;
        }

        public Double boxBorderLength() {
            return boxBorderLength;
        }

        public String boxBorderHexColor() {
            return boxBorderHexColor;
        }

        public Double boxBorderOpacity() {
            return boxBorderOpacity;
        }

        public Boolean expandBoxes() {
            return expandBoxes;
        }

        public Integer subgridRowCount() {
            return subgridRowCount;
        }

        public Integer subgridColumnCount() {
            return subgridColumnCount;
        }

        public Double subgridBorderThickness() {
            return subgridBorderThickness;
        }

        public Double subgridBorderLength() {
            return subgridBorderLength;
        }

        public String subgridBorderHexColor() {
            return subgridBorderHexColor;
        }

        public Double subgridBorderOpacity() {
            return subgridBorderOpacity;
        }

        public HintMeshStyle build() {
            return new HintMeshStyle(
                    fontName,
                    fontSize, fontSpacingPercent, fontHexColor, fontOpacity,
                    fontOutlineThickness, fontOutlineHexColor, fontOutlineOpacity,
                    fontShadowThickness, fontShadowStep, fontShadowHexColor, fontShadowOpacity, fontShadowHorizontalOffset, fontShadowVerticalOffset,
                    prefixFontHexColor,
                    highlightFontScale,
                    boxHexColor, boxOpacity, boxBorderThickness,
                    boxBorderLength,
                    boxBorderHexColor, boxBorderOpacity,
                    expandBoxes,
                    subgridRowCount,
                    subgridColumnCount,
                    subgridBorderThickness,
                    subgridBorderLength,
                    subgridBorderHexColor,
                    subgridBorderOpacity
            );
        }

    }

}
