package mousemaster;

import mousemaster.FontStyle.FontStyleBuilder;

public record HintFontStyle(FontStyle defaultFontStyle, double spacingPercent,
                            FontStyle selectedFontStyle,
                            FontStyle focusedFontStyle) {

    public HintFontStyleBuilder builder() {
        return new HintFontStyleBuilder(this);
    }

    public static class HintFontStyleBuilder {

        private FontStyleBuilder defaultFontStyle = new FontStyleBuilder();
        private Double spacingPercent;
        private FontStyleBuilder selectedFontStyle = new FontStyleBuilder();
        private FontStyleBuilder focusedFontStyle = new FontStyleBuilder();

        public HintFontStyleBuilder() {

        }

        public HintFontStyleBuilder(HintFontStyle hintFontStyle) {
            this.defaultFontStyle = new FontStyleBuilder(hintFontStyle.defaultFontStyle);
            this.spacingPercent = hintFontStyle.spacingPercent;
            this.selectedFontStyle = new FontStyleBuilder(hintFontStyle.selectedFontStyle);
            this.focusedFontStyle = new FontStyleBuilder(hintFontStyle.focusedFontStyle);
        }

        public FontStyleBuilder defaultFontStyle() {
            return defaultFontStyle;
        }

        public Double spacingPercent() {
            return spacingPercent;
        }

        public FontStyleBuilder selectedFontStyle() {
            return selectedFontStyle;
        }

        public FontStyleBuilder focusedFontStyle() {
            return focusedFontStyle;
        }

        public HintFontStyleBuilder spacingPercent(Double fontSpacingPercent) {
            this.spacingPercent = fontSpacingPercent;
            return this;
        }

        void extend(HintFontStyleBuilder defaultStyle) {
            defaultFontStyle.extend(defaultStyle.defaultFontStyle);
            if (spacingPercent == null) spacingPercent = defaultStyle.spacingPercent;
            selectedFontStyle.extend(defaultStyle.selectedFontStyle);
            focusedFontStyle.extend(defaultStyle.focusedFontStyle);
        }

        void extendSelectedAndFocusedFromMain() {
            selectedFontStyle.extend(defaultFontStyle);
            focusedFontStyle.extend(defaultFontStyle);
        }

        public HintFontStyle build() {
            return new HintFontStyle(
                    defaultFontStyle.build(),
                    spacingPercent,
                    selectedFontStyle.build(),
                    focusedFontStyle.build()
            );
        }

    }

}
