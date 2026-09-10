package mousemaster;

/**
 * The settings of a text layer that hold for the layer's life: what it says, its
 * font family, weight and style, and how it aligns on the layer's position. The
 * animatable part of a text layer (font-size, color, opacity, background-color,
 * outline-color, padding, corner-radius, rotation, scale...) comes from the
 * {@link EffectProperty} table like every other layer.
 */
public record EffectText(String text, String fontName, FontWeight weight, boolean italic,
                         Align align, double maxWidth, boolean keepOnScreen) {

    /** The same text settings saying something else (a placeholder filled in). */
    public EffectText withText(String text) {
        return new EffectText(text, fontName, weight, italic, align, maxWidth, keepOnScreen);
    }

    /** Whether the text wraps: a max-width of 0 means a single line. */
    public boolean wraps() {
        return maxWidth > 0;
    }

    /** Where the layer's x sits on the text: its left edge, its center, or its right edge. */
    public enum Align {
        LEFT, CENTER, RIGHT;

        public static Align parse(String value) {
            return switch (value) {
                case "left" -> LEFT;
                case "center" -> CENTER;
                case "right" -> RIGHT;
                default -> throw new IllegalArgumentException(
                        "Invalid text-align value " + value + ": text-align is which point of the" +
                        " text sits on the layer's x (its left edge, its center, or its right" +
                        " edge); expected left, center or right, for example layer1-text-align=left");
            };
        }
    }

    public static class EffectTextBuilder {
        private String text;
        private String fontName;
        private FontWeight weight;
        private Boolean italic;
        private Align align;
        private Double maxWidth;
        private Boolean keepOnScreen;

        public EffectTextBuilder maxWidth(Double maxWidth) { this.maxWidth = maxWidth; return this; }
        public EffectTextBuilder keepOnScreen(Boolean keepOnScreen) { this.keepOnScreen = keepOnScreen; return this; }
        public EffectTextBuilder text(String text) { this.text = text; return this; }
        public EffectTextBuilder fontName(String fontName) { this.fontName = fontName; return this; }
        public EffectTextBuilder weight(FontWeight weight) { this.weight = weight; return this; }
        public EffectTextBuilder italic(Boolean italic) { this.italic = italic; return this; }
        public EffectTextBuilder align(Align align) { this.align = align; return this; }

        public boolean isEmpty() {
            return text == null && fontName == null && weight == null && italic == null &&
                   align == null && maxWidth == null && keepOnScreen == null;
        }

        /** Inherits what this builder leaves unset. */
        public void extend(EffectTextBuilder parent) {
            if (text == null) text = parent.text;
            if (fontName == null) fontName = parent.fontName;
            if (weight == null) weight = parent.weight;
            if (italic == null) italic = parent.italic;
            if (align == null) align = parent.align;
            if (maxWidth == null) maxWidth = parent.maxWidth;
            if (keepOnScreen == null) keepOnScreen = parent.keepOnScreen;
        }

        public EffectText build() {
            return new EffectText(text,
                    fontName == null ? FontStyle.defaultName : fontName,
                    weight == null ? FontWeight.NORMAL : weight,
                    italic != null && italic,
                    align == null ? Align.CENTER : align,
                    maxWidth == null ? 0 : maxWidth,
                    keepOnScreen == null || keepOnScreen);
        }
    }

}
