package mousemaster;

import java.time.Duration;

public record HintMeshStyle(FontStyle fontStyle,
                            String focusedFontHexColor,
                            boolean prefixInBackground,
                            FontStyle prefixFontStyle,
                            String boxHexColor,
                            double boxOpacity,
                            double boxBorderThickness,
                            double boxBorderLength,
                            String boxBorderHexColor,
                            double boxBorderOpacity,
                            boolean prefixBoxEnabled,
                            double prefixBoxBorderThickness,
                            double prefixBoxBorderLength,
                            String prefixBoxBorderHexColor,
                            double prefixBoxBorderOpacity,
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
        private FontStyle.FontStyleBuilder fontStyle = new FontStyle.FontStyleBuilder();
        private String focusedFontHexColor;
        private Boolean prefixInBackground;
        private FontStyle.FontStyleBuilder prefixFontStyle = new FontStyle.FontStyleBuilder();
        private String boxHexColor;
        private Double boxOpacity;
        private Double boxBorderThickness;
        private Double boxBorderLength;
        private String boxBorderHexColor;
        private Double boxBorderOpacity;
        private Boolean prefixBoxEnabled;
        private Double prefixBoxBorderThickness;
        private Double prefixBoxBorderLength;
        private String prefixBoxBorderHexColor;
        private Double prefixBoxBorderOpacity;
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
            this.fontStyle = style.fontStyle.builder();
            this.focusedFontHexColor = style.focusedFontHexColor;
            this.prefixInBackground = style.prefixInBackground;
            this.prefixFontStyle = style.prefixFontStyle.builder();
            this.boxHexColor = style.boxHexColor;
            this.boxOpacity = style.boxOpacity;
            this.boxBorderThickness = style.boxBorderThickness;
            this.boxBorderLength = style.boxBorderLength;
            this.boxBorderHexColor = style.boxBorderHexColor;
            this.boxBorderOpacity = style.boxBorderOpacity;
            this.prefixBoxEnabled = style.prefixBoxEnabled;
            this.prefixBoxBorderThickness = style.prefixBoxBorderThickness;
            this.prefixBoxBorderLength = style.prefixBoxBorderLength;
            this.prefixBoxBorderHexColor = style.prefixBoxBorderHexColor;
            this.prefixBoxBorderOpacity = style.prefixBoxBorderOpacity;
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

        public HintMeshStyleBuilder focusedFontHexColor(
                String focusedFontHexColor) {
            this.focusedFontHexColor = focusedFontHexColor;
            return this;
        }

        public HintMeshStyleBuilder prefixInBackground(
                Boolean prefixInBackground) {
            this.prefixInBackground = prefixInBackground;
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

        public HintMeshStyleBuilder prefixBoxEnabled(Boolean prefixBoxEnabled) {
            this.prefixBoxEnabled = prefixBoxEnabled;
            return this;
        }

        public HintMeshStyleBuilder prefixBoxBorderThickness(Double prefixBoxBorderThickness) {
            this.prefixBoxBorderThickness = prefixBoxBorderThickness;
            return this;
        }

        public HintMeshStyleBuilder prefixBoxBorderLength(Double prefixBoxBorderLength) {
            this.prefixBoxBorderLength = prefixBoxBorderLength;
            return this;
        }

        public HintMeshStyleBuilder prefixBoxBorderHexColor(String prefixBoxBorderHexColor) {
            this.prefixBoxBorderHexColor = prefixBoxBorderHexColor;
            return this;
        }

        public HintMeshStyleBuilder prefixBoxBorderOpacity(Double prefixBoxBorderOpacity) {
            this.prefixBoxBorderOpacity = prefixBoxBorderOpacity;
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

        public FontStyle.FontStyleBuilder fontStyle() {
            return fontStyle;
        }

        public String focusedFontHexColor() {
            return focusedFontHexColor;
        }

        public Boolean prefixInBackground() {
            return prefixInBackground;
        }

        public FontStyle.FontStyleBuilder prefixFontStyle() {
            return prefixFontStyle;
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

        public Boolean prefixBoxEnabled() {
            return prefixBoxEnabled;
        }

        public Double prefixBoxBorderThickness() {
            return prefixBoxBorderThickness;
        }

        public Double prefixBoxBorderLength() {
            return prefixBoxBorderLength;
        }

        public String prefixBoxBorderHexColor() {
            return prefixBoxBorderHexColor;
        }

        public Double prefixBoxBorderOpacity() {
            return prefixBoxBorderOpacity;
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
            FontStyle fontStyle1 =
                    fontStyle.build(defaultStyle == null ? null : defaultStyle.fontStyle);
            return new HintMeshStyle(
                    fontStyle1,
                    focusedFontHexColor == null ? defaultStyle.focusedFontHexColor : focusedFontHexColor,
                    prefixInBackground == null ? defaultStyle.prefixInBackground : prefixInBackground,
                    prefixFontStyle.build(fontStyle1),
                    boxHexColor == null ? defaultStyle.boxHexColor : boxHexColor,
                    boxOpacity == null ? defaultStyle.boxOpacity : boxOpacity,
                    boxBorderThickness == null ? defaultStyle.boxBorderThickness : boxBorderThickness,
                    boxBorderLength == null ? defaultStyle.boxBorderLength : boxBorderLength,
                    boxBorderHexColor == null ? defaultStyle.boxBorderHexColor : boxBorderHexColor,
                    boxBorderOpacity == null ? defaultStyle.boxBorderOpacity : boxBorderOpacity,
                    prefixBoxEnabled == null ? defaultStyle.prefixBoxEnabled : prefixBoxEnabled,
                    prefixBoxBorderThickness == null ? defaultStyle.prefixBoxBorderThickness : prefixBoxBorderThickness,
                    prefixBoxBorderLength == null ? defaultStyle.prefixBoxBorderLength : prefixBoxBorderLength,
                    prefixBoxBorderHexColor == null ? defaultStyle.prefixBoxBorderHexColor : prefixBoxBorderHexColor,
                    prefixBoxBorderOpacity == null ? defaultStyle.prefixBoxBorderOpacity : prefixBoxBorderOpacity,
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
