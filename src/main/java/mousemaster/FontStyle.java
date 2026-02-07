package mousemaster;

import mousemaster.Shadow.ShadowBuilder;

public record FontStyle(String name, FontWeight weight,
                        double size, String hexColor,
                        double opacity,
                        double outlineThickness, String outlineHexColor,
                        double outlineOpacity,
                        Shadow shadow) {

    public static class FontStyleBuilder {

        private String name;
        private FontWeight weight;
        private Double size;
        private String hexColor;
        private Double opacity;
        private Double outlineThickness;
        private String outlineHexColor;
        private Double outlineOpacity;
        private ShadowBuilder shadow = new ShadowBuilder();

        public FontStyleBuilder() {

        }

        public FontStyleBuilder(FontStyle fontStyle) {
            this.name = fontStyle.name;
            this.weight = fontStyle.weight;
            this.size = fontStyle.size;
            this.hexColor = fontStyle.hexColor;
            this.opacity = fontStyle.opacity;
            this.outlineThickness = fontStyle.outlineThickness;
            this.outlineHexColor = fontStyle.outlineHexColor;
            this.outlineOpacity = fontStyle.outlineOpacity;
            this.shadow = new ShadowBuilder(fontStyle.shadow);
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

        public String hexColor() {
            return hexColor;
        }

        public Double opacity() {
            return opacity;
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

        public ShadowBuilder shadow() {
            return shadow;
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

        public FontStyleBuilder hexColor(String fontHexColor) {
            this.hexColor = fontHexColor;
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

        public FontStyleBuilder outlineHexColor(String fontOutlineHexColor) {
            this.outlineHexColor = fontOutlineHexColor;
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
            if (hexColor == null) hexColor = defaultStyle.hexColor;
            if (opacity == null) opacity = defaultStyle.opacity;
            if (outlineThickness == null) outlineThickness = defaultStyle.outlineThickness;
            if (outlineHexColor == null) outlineHexColor = defaultStyle.outlineHexColor;
            if (outlineOpacity == null) outlineOpacity = defaultStyle.outlineOpacity;
            shadow.extend(defaultStyle.shadow);
        }

        public FontStyle build() {
            return new FontStyle(
                    name,
                    weight,
                    size,
                    hexColor,
                    opacity,
                    outlineThickness,
                    outlineHexColor,
                    outlineOpacity,
                    shadow.build()
            );
        }

    }

}
