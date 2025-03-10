package mousemaster;

import java.util.List;

/**
 * Unlike a grid, it does not necessarily have fixed-size cells.
 */
public record HintMesh(boolean visible, HintMeshType type, List<Hint> hints, List<Key> focusedKeySequence,
                       String fontName, FontWeight fontWeight,
                       double fontSize, double fontSpacingPercent, String fontHexColor, double fontOpacity,
                       double fontOutlineThickness, String fontOutlineHexColor, double fontOutlineOpacity,
                       double fontShadowBlurRadius, String fontShadowHexColor, double fontShadowOpacity, double fontShadowHorizontalOffset, double fontShadowVerticalOffset,
                       String prefixFontHexColor,
                       String boxHexColor, double boxOpacity,
                       double boxBorderThickness,
                       double boxBorderLength,
                       String boxBorderHexColor, double boxBorderOpacity,
                       double boxWidthPercent,
                       double boxHeightPercent,
                       int subgridRowCount,
                       int subgridColumnCount,
                       double subgridBorderThickness,
                       double subgridBorderLength,
                       String subgridBorderHexColor,
                       double subgridBorderOpacity) {

    public HintMeshBuilder builder() {
        return new HintMeshBuilder(this);
    }

    public static class HintMeshBuilder {
        private boolean visible;
        private HintMeshType type;
        private List<Hint> hints;
        private List<Key> focusedKeySequence = List.of();
        private String fontName;
        private FontWeight fontWeight;
        private double fontSize;
        private double fontSpacingPercent;
        private String fontHexColor;
        private double fontOpacity;
        private double fontOutlineThickness;
        private String fontOutlineHexColor;
        private double fontOutlineOpacity;
        private double fontShadowBlurRadius;
        private String fontShadowHexColor;
        private double fontShadowOpacity;
        private double fontShadowHorizontalOffset;
        private double fontShadowVerticalOffset;
        private String prefixFontHexColor;
        private String boxHexColor;
        private double boxOpacity;
        private double boxBorderThickness;
        private double boxBorderLength;
        private String boxBorderHexColor;
        private double boxBorderOpacity;
        private double boxWidthPercent;
        private double boxHeightPercent;
        private int subgridRowCount;
        private int subgridColumnCount;
        private double subgridBorderThickness;
        private double subgridBorderLength;
        private String subgridBorderHexColor;
        private double subgridBorderOpacity;

        public HintMeshBuilder() {
        }

        public HintMeshBuilder(HintMesh hintMesh) {
            this.visible = hintMesh.visible;
            this.type = hintMesh.type;
            this.hints = hintMesh.hints;
            this.focusedKeySequence = hintMesh.focusedKeySequence;
            this.fontName = hintMesh.fontName;
            this.fontWeight = hintMesh.fontWeight;
            this.fontSize = hintMesh.fontSize;
            this.fontSpacingPercent = hintMesh.fontSpacingPercent;
            this.fontHexColor = hintMesh.fontHexColor;
            this.fontOpacity = hintMesh.fontOpacity;
            this.fontOutlineThickness = hintMesh.fontOutlineThickness;
            this.fontOutlineHexColor = hintMesh.fontOutlineHexColor;
            this.fontOutlineOpacity = hintMesh.fontOutlineOpacity;
            this.fontShadowBlurRadius = hintMesh.fontShadowBlurRadius;
            this.fontShadowHexColor = hintMesh.fontShadowHexColor;
            this.fontShadowOpacity = hintMesh.fontShadowOpacity;
            this.fontShadowHorizontalOffset = hintMesh.fontShadowHorizontalOffset;
            this.fontShadowVerticalOffset = hintMesh.fontShadowVerticalOffset;
            this.prefixFontHexColor = hintMesh.prefixFontHexColor;
            this.boxHexColor = hintMesh.boxHexColor;
            this.boxOpacity = hintMesh.boxOpacity;
            this.boxBorderThickness = hintMesh.boxBorderThickness;
            this.boxBorderLength = hintMesh.boxBorderLength;
            this.boxBorderHexColor = hintMesh.boxBorderHexColor;
            this.boxBorderOpacity = hintMesh.boxBorderOpacity;
            this.boxWidthPercent = hintMesh.boxWidthPercent;
            this.boxHeightPercent = hintMesh.boxHeightPercent;
            this.subgridRowCount = hintMesh.subgridRowCount;
            this.subgridColumnCount = hintMesh.subgridColumnCount;
            this.subgridBorderThickness = hintMesh.subgridBorderThickness;
            this.subgridBorderLength = hintMesh.subgridBorderLength;
            this.subgridBorderHexColor = hintMesh.subgridBorderHexColor;
            this.subgridBorderOpacity = hintMesh.subgridBorderOpacity;
        }


        public HintMeshType type() {
            return type;
        }

        public boolean visible() {
            return visible;
        }

        public List<Hint> hints() {
            return hints;
        }

        public List<Key> focusedKeySequence() {
            return focusedKeySequence;
        }

        public String fontName() {
            return fontName;
        }

        public FontWeight fontWeight() {
            return fontWeight;
        }

        public double fontSize() {
            return fontSize;
        }

        public double fontSpacingPercent() {
            return fontSpacingPercent;
        }

        public String fontHexColor() {
            return fontHexColor;
        }

        public double fontOpacity() {
            return fontOpacity;
        }

        public double fontOutlineThickness() {
            return fontOutlineThickness;
        }

        public String fontOutlineHexColor() {
            return fontOutlineHexColor;
        }

        public double fontOutlineOpacity() {
            return fontOutlineOpacity;
        }

        public double fontShadowBlurRadius() {
            return fontShadowBlurRadius;
        }

        public String fontShadowHexColor() {
            return fontShadowHexColor;
        }

        public double fontShadowOpacity() {
            return fontShadowOpacity;
        }

        public double fontShadowHorizontalOffset() {
            return fontShadowHorizontalOffset;
        }

        public double fontShadowVerticalOffset() {
            return fontShadowVerticalOffset;
        }

        public String prefixFontHexColor() {
            return prefixFontHexColor;
        }

        public String boxHexColor() {
            return boxHexColor;
        }

        public double boxOpacity() {
            return boxOpacity;
        }

        public double boxBorderThickness() {
            return boxBorderThickness;
        }

        public double boxBorderLength() {
            return boxBorderLength;
        }

        public String boxBorderHexColor() {
            return boxBorderHexColor;
        }

        public double boxBorderOpacity() {
            return boxBorderOpacity;
        }

        public double boxWidthPercent() {
            return boxWidthPercent;
        }

        public double boxHeightPercent() {
            return boxHeightPercent;
        }

        public int subgridRowCount() {
            return subgridRowCount;
        }

        public int subgridColumnCount() {
            return subgridColumnCount;
        }

        public double subgridBorderThickness() {
            return subgridBorderThickness;
        }

        public double subgridBorderLength() {
            return subgridBorderLength;
        }

        public String subgridBorderHexColor() {
            return subgridBorderHexColor;
        }

        public double subgridBorderOpacity() {
            return subgridBorderOpacity;
        }

        public HintMeshBuilder type(HintMeshType type) {
            this.type = type;
            return this;
        }

        public HintMeshBuilder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public HintMeshBuilder hints(List<Hint> hints) {
            this.hints = hints;
            return this;
        }

        public HintMeshBuilder focusedKeySequence(List<Key> focusedKeySequence) {
            this.focusedKeySequence = focusedKeySequence;
            return this;
        }

        public HintMeshBuilder fontName(String fontName) {
            this.fontName = fontName;
            return this;
        }

        public HintMeshBuilder fontWeight(FontWeight fontWeight) {
            this.fontWeight = fontWeight;
            return this;
        }

        public HintMeshBuilder fontSize(double fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        public HintMeshBuilder fontSpacingPercent(double fontSpacingPercent) {
            this.fontSpacingPercent = fontSpacingPercent;
            return this;
        }

        public HintMeshBuilder fontHexColor(String fontHexColor) {
            this.fontHexColor = fontHexColor;
            return this;
        }

        public HintMeshBuilder fontOpacity(double fontOpacity) {
            this.fontOpacity = fontOpacity;
            return this;
        }

        public HintMeshBuilder fontOutlineThickness(double fontOutlineThickness) {
            this.fontOutlineThickness = fontOutlineThickness;
            return this;
        }

        public HintMeshBuilder fontOutlineHexColor(String fontOutlineHexColor) {
            this.fontOutlineHexColor = fontOutlineHexColor;
            return this;
        }

        public HintMeshBuilder fontOutlineOpacity(double fontOutlineOpacity) {
            this.fontOutlineOpacity = fontOutlineOpacity;
            return this;
        }

        public HintMeshBuilder fontShadowBlurRadius(double fontShadowBlurRadius) {
            this.fontShadowBlurRadius = fontShadowBlurRadius;
            return this;
        }

        public HintMeshBuilder fontShadowHexColor(String fontShadowHexColor) {
            this.fontShadowHexColor = fontShadowHexColor;
            return this;
        }

        public HintMeshBuilder fontShadowOpacity(double fontShadowOpacity) {
            this.fontShadowOpacity = fontShadowOpacity;
            return this;
        }

        public HintMeshBuilder fontShadowHorizontalOffset(double fontShadowHorizontalOffset) {
            this.fontShadowHorizontalOffset = fontShadowHorizontalOffset;
            return this;
        }

        public HintMeshBuilder fontShadowVerticalOffset(double fontShadowVerticalOffset) {
            this.fontShadowVerticalOffset = fontShadowVerticalOffset;
            return this;
        }

        public HintMeshBuilder prefixFontHexColor(
                String prefixFontHexColor) {
            this.prefixFontHexColor = prefixFontHexColor;
            return this;
        }

        public HintMeshBuilder boxHexColor(String boxHexColor) {
            this.boxHexColor = boxHexColor;
            return this;
        }

        public HintMeshBuilder boxOpacity(double boxOpacity) {
            this.boxOpacity = boxOpacity;
            return this;
        }

        public HintMeshBuilder boxBorderThickness(double boxBorderThickness) {
            this.boxBorderThickness = boxBorderThickness;
            return this;
        }

        public HintMeshBuilder boxBorderLength(double boxBorderLength) {
            this.boxBorderLength = boxBorderLength;
            return this;
        }

        public HintMeshBuilder boxBorderHexColor(String boxBorderHexColor) {
            this.boxBorderHexColor = boxBorderHexColor;
            return this;
        }

        public HintMeshBuilder boxBorderOpacity(double boxBorderOpacity) {
            this.boxBorderOpacity = boxBorderOpacity;
            return this;
        }

        public HintMeshBuilder boxWidthPercent(double boxWidthPercent) {
            this.boxWidthPercent = boxWidthPercent;
            return this;
        }

        public HintMeshBuilder boxHeightPercent(double boxHeightPercent) {
            this.boxHeightPercent = boxHeightPercent;
            return this;
        }

        public HintMeshBuilder subgridRowCount(int subgridRowCount) {
            this.subgridRowCount = subgridRowCount;
            return this;
        }

        public HintMeshBuilder subgridColumnCount(int subgridColumnCount) {
            this.subgridColumnCount = subgridColumnCount;
            return this;
        }

        public HintMeshBuilder subgridBorderThickness(double subgridBorderThickness) {
            this.subgridBorderThickness = subgridBorderThickness;
            return this;
        }

        public HintMeshBuilder subgridBorderLength(double subgridBorderLength) {
            this.subgridBorderLength = subgridBorderLength;
            return this;
        }

        public HintMeshBuilder subgridBorderHexColor(String subgridBorderHexColor) {
            this.subgridBorderHexColor = subgridBorderHexColor;
            return this;
        }

        public HintMeshBuilder subgridBorderOpacity(double subgridBorderOpacity) {
            this.subgridBorderOpacity = subgridBorderOpacity;
            return this;
        }

        public HintMesh build() {
            return new HintMesh(visible, type, hints, focusedKeySequence, fontName, fontWeight,
                    fontSize, fontSpacingPercent, fontHexColor, fontOpacity,
                    fontOutlineThickness, fontOutlineHexColor, fontOutlineOpacity,
                    fontShadowBlurRadius, fontShadowHexColor, fontShadowOpacity, fontShadowHorizontalOffset, fontShadowVerticalOffset,
                    prefixFontHexColor,
                    boxHexColor, boxOpacity,
                    boxBorderThickness,
                    boxBorderLength,
                    boxBorderHexColor, boxBorderOpacity,
                    boxWidthPercent,
                    boxHeightPercent,
                    subgridRowCount,
                    subgridColumnCount,
                    subgridBorderThickness,
                    subgridBorderLength,
                    subgridBorderHexColor,
                    subgridBorderOpacity);
        }
    }

}
