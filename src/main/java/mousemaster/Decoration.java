package mousemaster;

import java.util.List;

/**
 * A purely visual grid drawn over the hints (never consumes input). Held by
 * {@link HintMeshStyle} as a list indexed by descent depth: index 0 = the
 * whole-area decoration (the grid treated as one big cell), index 1 =
 * subdecoration (one grid inside each top cell), index 2 = subsubdecoration, ...
 */
public record Decoration(int maxRowCount, int maxColumnCount,
                         List<Key> labelKeys, List<Key> labelOverride,
                         String boxHexColor, double boxOpacity,
                         double boxBorderThickness, double boxBorderLength,
                         String boxBorderHexColor, double boxBorderOpacity,
                         double boxBorderRadius, HintFontStyle fontStyle,
                         boolean closed) {

    public DecorationBuilder builder() {
        return new DecorationBuilder(this);
    }

    public static class DecorationBuilder {
        private Integer maxRowCount;
        private Integer maxColumnCount;
        private List<Key> labelKeys;
        private List<Key> labelOverride;
        private String boxHexColor;
        private Double boxOpacity;
        private Double boxBorderThickness;
        private Double boxBorderLength;
        private String boxBorderHexColor;
        private Double boxBorderOpacity;
        private Double boxBorderRadius;
        private HintFontStyle.HintFontStyleBuilder fontStyle =
                new HintFontStyle.HintFontStyleBuilder();
        private Boolean closed;

        public DecorationBuilder() {
        }

        public DecorationBuilder(Decoration decoration) {
            this.maxRowCount = decoration.maxRowCount;
            this.maxColumnCount = decoration.maxColumnCount;
            this.labelKeys = decoration.labelKeys;
            this.labelOverride = decoration.labelOverride;
            this.boxHexColor = decoration.boxHexColor;
            this.boxOpacity = decoration.boxOpacity;
            this.boxBorderThickness = decoration.boxBorderThickness;
            this.boxBorderLength = decoration.boxBorderLength;
            this.boxBorderHexColor = decoration.boxBorderHexColor;
            this.boxBorderOpacity = decoration.boxBorderOpacity;
            this.boxBorderRadius = decoration.boxBorderRadius;
            this.fontStyle = decoration.fontStyle.builder();
            this.closed = decoration.closed;
        }

        public Integer maxRowCount() {
            return maxRowCount;
        }

        public Integer maxColumnCount() {
            return maxColumnCount;
        }

        public List<Key> labelKeys() {
            return labelKeys;
        }

        public List<Key> labelOverride() {
            return labelOverride;
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

        public Double boxBorderRadius() {
            return boxBorderRadius;
        }

        public HintFontStyle.HintFontStyleBuilder fontStyle() {
            return fontStyle;
        }

        public Boolean closed() {
            return closed;
        }

        public DecorationBuilder maxRowCount(Integer maxRowCount) {
            this.maxRowCount = maxRowCount;
            return this;
        }

        public DecorationBuilder maxColumnCount(Integer maxColumnCount) {
            this.maxColumnCount = maxColumnCount;
            return this;
        }

        public DecorationBuilder labelKeys(List<Key> labelKeys) {
            this.labelKeys = labelKeys;
            return this;
        }

        public DecorationBuilder labelOverride(List<Key> labelOverride) {
            this.labelOverride = labelOverride;
            return this;
        }

        public DecorationBuilder boxHexColor(String boxHexColor) {
            this.boxHexColor = boxHexColor;
            return this;
        }

        public DecorationBuilder boxOpacity(Double boxOpacity) {
            this.boxOpacity = boxOpacity;
            return this;
        }

        public DecorationBuilder boxBorderThickness(Double boxBorderThickness) {
            this.boxBorderThickness = boxBorderThickness;
            return this;
        }

        public DecorationBuilder boxBorderLength(Double boxBorderLength) {
            this.boxBorderLength = boxBorderLength;
            return this;
        }

        public DecorationBuilder boxBorderHexColor(String boxBorderHexColor) {
            this.boxBorderHexColor = boxBorderHexColor;
            return this;
        }

        public DecorationBuilder boxBorderOpacity(Double boxBorderOpacity) {
            this.boxBorderOpacity = boxBorderOpacity;
            return this;
        }

        public DecorationBuilder boxBorderRadius(Double boxBorderRadius) {
            this.boxBorderRadius = boxBorderRadius;
            return this;
        }

        public DecorationBuilder closed(Boolean closed) {
            this.closed = closed;
            return this;
        }

        public Decoration build(Decoration defaultDecoration) {
            return new Decoration(
                    maxRowCount == null ? defaultDecoration.maxRowCount : maxRowCount,
                    maxColumnCount == null ? defaultDecoration.maxColumnCount : maxColumnCount,
                    labelKeys == null ? defaultDecoration.labelKeys : labelKeys,
                    labelOverride == null ? defaultDecoration.labelOverride : labelOverride,
                    boxHexColor == null ? defaultDecoration.boxHexColor : boxHexColor,
                    boxOpacity == null ? defaultDecoration.boxOpacity : boxOpacity,
                    boxBorderThickness == null ? defaultDecoration.boxBorderThickness : boxBorderThickness,
                    boxBorderLength == null ? defaultDecoration.boxBorderLength : boxBorderLength,
                    boxBorderHexColor == null ? defaultDecoration.boxBorderHexColor : boxBorderHexColor,
                    boxBorderOpacity == null ? defaultDecoration.boxBorderOpacity : boxBorderOpacity,
                    boxBorderRadius == null ? defaultDecoration.boxBorderRadius : boxBorderRadius,
                    fontStyle.build(),
                    closed == null ? defaultDecoration.closed : closed
            );
        }
    }

}
