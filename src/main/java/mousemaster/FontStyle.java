package mousemaster;

import mousemaster.Shadow.ShadowBuilder;

public record FontStyle(String name, FontWeight weight,
                        double size, Color color,
                        double opacity,
                        double outlineThickness, Color outlineColor,
                        double outlineOpacity,
                        Shadow shadow, FontVerticalAlignment verticalAlignment) {

    public static final String defaultName = Os.macos ? "Menlo" : "Consolas";

    public static class FontStyleBuilder {

        private String name;
        private FontWeight weight;
        private Double size;
        private Color color;
        private Double opacity;
        private Double outlineThickness;
        private Color outlineColor;
        private Double outlineOpacity;
        private ShadowBuilder shadow = new ShadowBuilder();
        private FontVerticalAlignment verticalAlignment;

        public FontStyleBuilder() {

        }

        public FontStyleBuilder(FontStyle fontStyle) {
            this.name = fontStyle.name;
            this.weight = fontStyle.weight;
            this.size = fontStyle.size;
            this.color = fontStyle.color;
            this.opacity = fontStyle.opacity;
            this.outlineThickness = fontStyle.outlineThickness;
            this.outlineColor = fontStyle.outlineColor;
            this.outlineOpacity = fontStyle.outlineOpacity;
            this.shadow = new ShadowBuilder(fontStyle.shadow);
            this.verticalAlignment = fontStyle.verticalAlignment;
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

        public Color color() {
            return color;
        }

        public Double opacity() {
            return opacity;
        }

        public Double outlineThickness() {
            return outlineThickness;
        }

        public Color outlineColor() {
            return outlineColor;
        }

        public Double outlineOpacity() {
            return outlineOpacity;
        }

        public ShadowBuilder shadow() {
            return shadow;
        }

        public FontVerticalAlignment verticalAlignment() {
            return verticalAlignment;
        }

        public FontStyleBuilder verticalAlignment(FontVerticalAlignment verticalAlignment) {
            this.verticalAlignment = verticalAlignment;
            return this;
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

        public FontStyleBuilder color(Color fontColor) {
            this.color = fontColor;
            return this;
        }

        public FontStyleBuilder opacity(Double fontOpacity) {
            this.opacity = fontOpacity;
            return this;
        }

        public FontStyleBuilder outlineThickness(Double fontOutlineThickness) {
            this.outlineThickness = fontOutlineThickness;
            return this;
        }

        public FontStyleBuilder outlineColor(Color fontOutlineColor) {
            this.outlineColor = fontOutlineColor;
            return this;
        }

        public FontStyleBuilder outlineOpacity(Double fontOutlineOpacity) {
            this.outlineOpacity = fontOutlineOpacity;
            return this;
        }

        void extend(FontStyleBuilder defaultStyle) {
            if (name == null) name = defaultStyle.name;
            if (weight == null) weight = defaultStyle.weight;
            if (size == null) size = defaultStyle.size;
            if (color == null) color = defaultStyle.color;
            if (opacity == null) opacity = defaultStyle.opacity;
            if (outlineThickness == null) outlineThickness = defaultStyle.outlineThickness;
            if (outlineColor == null) outlineColor = defaultStyle.outlineColor;
            if (outlineOpacity == null) outlineOpacity = defaultStyle.outlineOpacity;
            if (verticalAlignment == null) verticalAlignment = defaultStyle.verticalAlignment;
            shadow.extend(defaultStyle.shadow);
        }

        public FontStyle build() {
            return new FontStyle(
                    name,
                    weight,
                    size,
                    color,
                    opacity,
                    outlineThickness,
                    outlineColor,
                    outlineOpacity,
                    shadow.build(),
                    verticalAlignment
            );
        }

    }

}
