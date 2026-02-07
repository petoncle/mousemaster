package mousemaster;

import mousemaster.FontStyle.FontStyleBuilder;

public record HintFontStyle(FontStyle fontStyle, double spacingPercent,
                            String selectedFontHexColor,
                            double selectedFontOpacity,
                            String focusedFontHexColor,
                            double focusedFontOpacity) {

    public HintFontStyleBuilder builder() {
        return new HintFontStyleBuilder(this);
    }

    public static class HintFontStyleBuilder {

        private FontStyleBuilder fontStyle = new FontStyleBuilder();
        private Double spacingPercent;
        private String selectedFontHexColor;
        private Double selectedFontOpacity;
        private String focusedFontHexColor;
        private Double focusedFontOpacity;

        public HintFontStyleBuilder() {

        }

        public HintFontStyleBuilder(HintFontStyle hintFontStyle) {
            this.fontStyle = new FontStyleBuilder(hintFontStyle.fontStyle);
            this.spacingPercent = hintFontStyle.spacingPercent;
            this.selectedFontHexColor = hintFontStyle.selectedFontHexColor;
            this.selectedFontOpacity = hintFontStyle.selectedFontOpacity;
            this.focusedFontHexColor = hintFontStyle.focusedFontHexColor;
            this.focusedFontOpacity = hintFontStyle.focusedFontOpacity;
        }

        public FontStyleBuilder fontStyle() {
            return fontStyle;
        }

        public Double spacingPercent() {
            return spacingPercent;
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

        public HintFontStyleBuilder spacingPercent(Double fontSpacingPercent) {
            this.spacingPercent = fontSpacingPercent;
            return this;
        }

        public HintFontStyleBuilder selectedFontHexColor(String selectedFontHexColor) {
            this.selectedFontHexColor = selectedFontHexColor;
            return this;
        }

        public HintFontStyleBuilder selectedFontOpacity(Double selectedFontOpacity) {
            this.selectedFontOpacity = selectedFontOpacity;
            return this;
        }

        public HintFontStyleBuilder focusedFontHexColor(String focusedFontHexColor) {
            this.focusedFontHexColor = focusedFontHexColor;
            return this;
        }

        public HintFontStyleBuilder focusedFontOpacity(Double focusedFontOpacity) {
            this.focusedFontOpacity = focusedFontOpacity;
            return this;
        }

        void extend(HintFontStyleBuilder defaultStyle) {
            fontStyle.extend(defaultStyle.fontStyle);
            if (spacingPercent == null) spacingPercent = defaultStyle.spacingPercent;
            if (selectedFontHexColor == null) selectedFontHexColor = defaultStyle.selectedFontHexColor;
            if (selectedFontOpacity == null) selectedFontOpacity = defaultStyle.selectedFontOpacity;
            if (focusedFontHexColor == null) focusedFontHexColor = defaultStyle.focusedFontHexColor;
            if (focusedFontOpacity == null) focusedFontOpacity = defaultStyle.focusedFontOpacity;
        }

        void extendSelectedAndFocusedFromMain() {
            if (selectedFontHexColor == null) selectedFontHexColor = fontStyle.hexColor();
            if (selectedFontOpacity == null) selectedFontOpacity = fontStyle.opacity();
            if (focusedFontHexColor == null) focusedFontHexColor = fontStyle.hexColor();
            if (focusedFontOpacity == null) focusedFontOpacity = fontStyle.opacity();
        }

        public HintFontStyle build() {
            return new HintFontStyle(
                    fontStyle.build(),
                    spacingPercent,
                    selectedFontHexColor,
                    selectedFontOpacity,
                    focusedFontHexColor,
                    focusedFontOpacity
            );
        }

    }

}
