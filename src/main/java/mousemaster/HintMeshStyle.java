package mousemaster;

import java.time.Duration;

public record HintMeshStyle(String fontName, FontWeight fontWeight,
                            double fontSize, double fontSpacingPercent, String fontHexColor, double fontOpacity,
                            double fontOutlineThickness, String fontOutlineHexColor, double fontOutlineOpacity,
                            double fontShadowBlurRadius, String fontShadowHexColor, double fontShadowOpacity, double fontShadowHorizontalOffset, double fontShadowVerticalOffset,
                            String prefixFontHexColor,
                            String boxHexColor,
                            double boxOpacity,
                            double boxBorderThickness,
                            double boxBorderLength,
                            String boxBorderHexColor,
                            double boxBorderOpacity,
                            double boxWidthPercent,
                            double boxHeightPercent,
                            int subgridRowCount,
                            int subgridColumnCount,
                            double subgridBorderThickness,
                            double subgridBorderLength,
                            String subgridBorderHexColor,
                            double subgridBorderOpacity,
                            boolean transitionAnimationEnabled,
                            Duration transitionAnimationDuration) {

    public HintMeshStyleBuilder builder() {
        return new HintMeshStyleBuilder(this);
    }

    public static class HintMeshStyleBuilder {
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
        private String prefixFontHexColor;
        private String boxHexColor;
        private Double boxOpacity;
        private Double boxBorderThickness;
        private Double boxBorderLength;
        private String boxBorderHexColor;
        private Double boxBorderOpacity;
        private Double boxWidthPercent;
        private Double boxHeightPercent;
        private Integer subgridRowCount;
        private Integer subgridColumnCount;
        private Double subgridBorderThickness;
        private Double subgridBorderLength;
        private String subgridBorderHexColor;
        private Double subgridBorderOpacity;
        private Boolean transitionAnimationEnabled;
        private Duration transitionAnimationDuration;

        public HintMeshStyleBuilder() {

        }

        public HintMeshStyleBuilder(HintMeshStyle style) {
            this.fontName = style.fontName;
            this.fontWeight = style.fontWeight;
            this.fontSize = style.fontSize;
            this.fontSpacingPercent = style.fontSpacingPercent;
            this.fontHexColor = style.fontHexColor;
            this.fontOpacity = style.fontOpacity;
            this.fontOutlineThickness = style.fontOutlineThickness;
            this.fontOutlineHexColor = style.fontOutlineHexColor;
            this.fontOutlineOpacity = style.fontOutlineOpacity;
            this.fontShadowBlurRadius = style.fontShadowBlurRadius;
            this.fontShadowHexColor = style.fontShadowHexColor;
            this.fontShadowOpacity = style.fontShadowOpacity;
            this.fontShadowHorizontalOffset = style.fontShadowHorizontalOffset;
            this.fontShadowVerticalOffset = style.fontShadowVerticalOffset;
            this.prefixFontHexColor = style.prefixFontHexColor;
            this.boxHexColor = style.boxHexColor;
            this.boxOpacity = style.boxOpacity;
            this.boxBorderThickness = style.boxBorderThickness;
            this.boxBorderLength = style.boxBorderLength;
            this.boxBorderHexColor = style.boxBorderHexColor;
            this.boxBorderOpacity = style.boxBorderOpacity;
            this.boxWidthPercent = style.boxWidthPercent;
            this.boxHeightPercent = style.boxHeightPercent;
            this.subgridRowCount = style.subgridRowCount;
            this.subgridColumnCount = style.subgridColumnCount;
            this.subgridBorderThickness = style.subgridBorderThickness;
            this.subgridBorderLength = style.subgridBorderLength;
            this.subgridBorderHexColor = style.subgridBorderHexColor;
            this.subgridBorderOpacity = style.subgridBorderOpacity;
            this.transitionAnimationEnabled = style.transitionAnimationEnabled;
            this.transitionAnimationDuration = style.transitionAnimationDuration;
        }

        public HintMeshStyleBuilder fontName(String fontName) {
            this.fontName = fontName;
            return this;
        }

        public HintMeshStyleBuilder fontWeight(FontWeight fontWeight) {
            this.fontWeight = fontWeight;
            return this;
        }

        public HintMeshStyleBuilder fontSize(Double fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        public HintMeshStyleBuilder fontSpacingPercent(Double fontSpacingPercent) {
            this.fontSpacingPercent = fontSpacingPercent;
            return this;
        }

        public HintMeshStyleBuilder fontHexColor(String fontHexColor) {
            this.fontHexColor = fontHexColor;
            return this;
        }

        public HintMeshStyleBuilder fontOpacity(Double fontOpacity) {
            this.fontOpacity = fontOpacity;
            return this;
        }

        public HintMeshStyleBuilder fontOutlineThickness(Double fontOutlineThickness) {
            this.fontOutlineThickness = fontOutlineThickness;
            return this;
        }

        public HintMeshStyleBuilder fontOutlineHexColor(String fontOutlineHexColor) {
            this.fontOutlineHexColor = fontOutlineHexColor;
            return this;
        }

        public HintMeshStyleBuilder fontOutlineOpacity(Double fontOutlineOpacity) {
            this.fontOutlineOpacity = fontOutlineOpacity;
            return this;
        }

        public HintMeshStyleBuilder fontShadowBlurRadius(Double fontShadowBlurRadius) {
            this.fontShadowBlurRadius = fontShadowBlurRadius;
            return this;
        }

        public HintMeshStyleBuilder fontShadowHexColor(String fontShadowHexColor) {
            this.fontShadowHexColor = fontShadowHexColor;
            return this;
        }

        public HintMeshStyleBuilder fontShadowOpacity(Double fontShadowOpacity) {
            this.fontShadowOpacity = fontShadowOpacity;
            return this;
        }

        public HintMeshStyleBuilder fontShadowHorizontalOffset(Double fontShadowHorizontalOffset) {
            this.fontShadowHorizontalOffset = fontShadowHorizontalOffset;
            return this;
        }

        public HintMeshStyleBuilder fontShadowVerticalOffset(Double fontShadowVerticalOffset) {
            this.fontShadowVerticalOffset = fontShadowVerticalOffset;
            return this;
        }

        public HintMeshStyleBuilder prefixFontHexColor(
                String prefixFontHexColor) {
            this.prefixFontHexColor = prefixFontHexColor;
            return this;
        }

        public HintMeshStyleBuilder boxHexColor(String boxHexColor) {
            this.boxHexColor = boxHexColor;
            return this;
        }

        public HintMeshStyleBuilder boxOpacity(Double boxOpacity) {
            this.boxOpacity = boxOpacity;
            return this;
        }

        public HintMeshStyleBuilder boxBorderThickness(Double boxBorderThickness) {
            this.boxBorderThickness = boxBorderThickness;
            return this;
        }

        public HintMeshStyleBuilder boxBorderLength(Double boxBorderLength) {
            this.boxBorderLength = boxBorderLength;
            return this;
        }

        public HintMeshStyleBuilder boxBorderHexColor(String boxBorderHexColor) {
            this.boxBorderHexColor = boxBorderHexColor;
            return this;
        }

        public HintMeshStyleBuilder boxBorderOpacity(Double boxBorderOpacity) {
            this.boxBorderOpacity = boxBorderOpacity;
            return this;
        }

        public HintMeshStyleBuilder boxWidthPercent(Double boxWidthPercent) {
            this.boxWidthPercent = boxWidthPercent;
            return this;
        }

        public HintMeshStyleBuilder boxHeightPercent(Double boxHeightPercent) {
            this.boxHeightPercent = boxHeightPercent;
            return this;
        }

        public HintMeshStyleBuilder subgridRowCount(Integer subgridRowCount) {
            this.subgridRowCount = subgridRowCount;
            return this;
        }

        public HintMeshStyleBuilder subgridColumnCount(Integer subgridColumnCount) {
            this.subgridColumnCount = subgridColumnCount;
            return this;
        }

        public HintMeshStyleBuilder subgridBorderThickness(Double subgridBorderThickness) {
            this.subgridBorderThickness = subgridBorderThickness;
            return this;
        }

        public HintMeshStyleBuilder subgridBorderLength(Double subgridBorderLength) {
            this.subgridBorderLength = subgridBorderLength;
            return this;
        }

        public HintMeshStyleBuilder subgridBorderHexColor(String subgridBorderHexColor) {
            this.subgridBorderHexColor = subgridBorderHexColor;
            return this;
        }

        public HintMeshStyleBuilder subgridBorderOpacity(Double subgridBorderOpacity) {
            this.subgridBorderOpacity = subgridBorderOpacity;
            return this;
        }

        public HintMeshStyleBuilder transitionAnimationEnabled(Boolean transitionAnimationEnabled) {
            this.transitionAnimationEnabled = transitionAnimationEnabled;
            return this;
        }

        public HintMeshStyleBuilder transitionAnimationDuration(Duration transitionAnimationDuration) {
            this.transitionAnimationDuration = transitionAnimationDuration;
            return this;
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

        public String prefixFontHexColor() {
            return prefixFontHexColor;
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

        public Double boxWidthPercent() {
            return boxWidthPercent;
        }

        public Double boxHeightPercent() {
            return boxHeightPercent;
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

        public Boolean transitionAnimationEnabled() {
            return transitionAnimationEnabled;
        }

        public Duration transitionAnimationDuration() {
            return transitionAnimationDuration;
        }

        public HintMeshStyle build(HintMeshStyle defaultStyle) {
            return new HintMeshStyle(
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
                    fontShadowVerticalOffset == null ? defaultStyle.fontShadowVerticalOffset : fontShadowVerticalOffset,
                    prefixFontHexColor == null ? defaultStyle.prefixFontHexColor : prefixFontHexColor,
                    boxHexColor == null ? defaultStyle.boxHexColor : boxHexColor,
                    boxOpacity == null ? defaultStyle.boxOpacity : boxOpacity,
                    boxBorderThickness == null ? defaultStyle.boxBorderThickness : boxBorderThickness,
                    boxBorderLength == null ? defaultStyle.boxBorderLength : boxBorderLength,
                    boxBorderHexColor == null ? defaultStyle.boxBorderHexColor : boxBorderHexColor,
                    boxBorderOpacity == null ? defaultStyle.boxBorderOpacity : boxBorderOpacity,
                    boxWidthPercent == null ? defaultStyle.boxWidthPercent : boxWidthPercent,
                    boxHeightPercent == null ? defaultStyle.boxHeightPercent : boxHeightPercent,
                    subgridRowCount == null ? defaultStyle.subgridRowCount : subgridRowCount,
                    subgridColumnCount == null ? defaultStyle.subgridColumnCount : subgridColumnCount,
                    subgridBorderThickness == null ? defaultStyle.subgridBorderThickness : subgridBorderThickness,
                    subgridBorderLength == null ? defaultStyle.subgridBorderLength : subgridBorderLength,
                    subgridBorderHexColor == null ? defaultStyle.subgridBorderHexColor : subgridBorderHexColor,
                    subgridBorderOpacity == null ? defaultStyle.subgridBorderOpacity : subgridBorderOpacity,
                    transitionAnimationEnabled == null ? defaultStyle.transitionAnimationEnabled : transitionAnimationEnabled,
                    transitionAnimationDuration == null ? defaultStyle.transitionAnimationDuration : transitionAnimationDuration
            );
        }

    }

}
