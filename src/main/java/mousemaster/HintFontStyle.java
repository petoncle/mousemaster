package mousemaster;

import mousemaster.FontStyle.FontStyleBuilder;

public record HintFontStyle(FontStyle defaultFontStyle, double spacingPercent,
                            FontStyle selectedFontStyle,
                            FontStyle focusedFontStyle,
                            FontStyle savedFontStyle) {

    public HintFontStyleBuilder builder() {
        return new HintFontStyleBuilder(this);
    }

    public HintFontStyle saved() {
        return new HintFontStyle(savedFontStyle, spacingPercent, savedFontStyle,
                savedFontStyle, savedFontStyle);
    }

    public static class HintFontStyleBuilder {

        private FontStyleBuilder defaultFontStyle = new FontStyleBuilder();
        private Double spacingPercent;
        private FontStyleBuilder selectedFontStyle = new FontStyleBuilder();
        private FontStyleBuilder focusedFontStyle = new FontStyleBuilder();
        private FontStyleBuilder savedFontStyle = new FontStyleBuilder();

        public HintFontStyleBuilder() {

        }

        public HintFontStyleBuilder(HintFontStyle hintFontStyle) {
            this.defaultFontStyle = new FontStyleBuilder(hintFontStyle.defaultFontStyle);
            this.spacingPercent = hintFontStyle.spacingPercent;
            this.selectedFontStyle = new FontStyleBuilder(hintFontStyle.selectedFontStyle);
            this.focusedFontStyle = new FontStyleBuilder(hintFontStyle.focusedFontStyle);
            this.savedFontStyle = new FontStyleBuilder(hintFontStyle.savedFontStyle);
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

        public FontStyleBuilder savedFontStyle() {
            return savedFontStyle;
        }

        public HintFontStyleBuilder spacingPercent(Double fontSpacingPercent) {
            this.spacingPercent = fontSpacingPercent;
            return this;
        }

        public HintFontStyle build() {
            return new HintFontStyle(
                    defaultFontStyle.build(),
                    spacingPercent,
                    selectedFontStyle.build(),
                    focusedFontStyle.build(),
                    savedFontStyle.build()
            );
        }

    }

}
