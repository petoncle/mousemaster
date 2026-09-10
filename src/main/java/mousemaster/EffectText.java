package mousemaster;

/**
 * The settings of a text layer that hold for the layer's life: what it says, its
 * font family, weight and style, and how it aligns on the layer's position. The
 * animatable part of a text layer (font-size, color, opacity, background-color,
 * outline-color, padding, corner-radius, rotation, scale...) comes from the
 * {@link EffectProperty} table like every other layer.
 */
public record EffectText(String text, String fontName, FontWeight weight, boolean italic,
                         Align align) {

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

        public EffectTextBuilder text(String text) { this.text = text; return this; }
        public EffectTextBuilder fontName(String fontName) { this.fontName = fontName; return this; }
        public EffectTextBuilder weight(FontWeight weight) { this.weight = weight; return this; }
        public EffectTextBuilder italic(Boolean italic) { this.italic = italic; return this; }
        public EffectTextBuilder align(Align align) { this.align = align; return this; }

        public boolean isEmpty() {
            return text == null && fontName == null && weight == null && italic == null &&
                   align == null;
        }

        /** Inherits what this builder leaves unset. */
        public void extend(EffectTextBuilder parent) {
            if (text == null) text = parent.text;
            if (fontName == null) fontName = parent.fontName;
            if (weight == null) weight = parent.weight;
            if (italic == null) italic = parent.italic;
            if (align == null) align = parent.align;
        }

        public EffectText build() {
            return new EffectText(text,
                    fontName == null ? FontStyle.defaultName : fontName,
                    weight == null ? FontWeight.NORMAL : weight,
                    italic != null && italic,
                    align == null ? Align.CENTER : align);
        }
    }

}
