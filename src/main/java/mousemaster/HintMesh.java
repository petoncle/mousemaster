package mousemaster;

import java.util.List;

/**
 * Unlike a grid, it does not necessarily have fixed-size cells.
 */
public record HintMesh(boolean visible, HintMeshType type, List<Hint> hints, List<Key> focusedKeySequence,
                       String fontName, int fontSize, String fontHexColor, double fontOpacity,
                       String prefixFontHexColor, double highlightFontScale,
                       String boxHexColor, double boxOpacity,
                       int boxBorderThickness,
                       String boxOutlineHexColor, double boxOutlineOpacity) {

    public HintMeshBuilder builder() {
        return new HintMeshBuilder(this);
    }

    public static class HintMeshBuilder {
        private boolean visible;
        private HintMeshType type;
        private List<Hint> hints;
        private List<Key> focusedKeySequence = List.of();
        private String fontName;
        private int fontSize;
        private String fontHexColor;
        private double fontOpacity;
        private String prefixFontHexColor;
        private double highlightFontScale;
        private String boxHexColor;
        private double boxOpacity;
        private int boxBorderThickness;
        private String boxOutlineHexColor;
        private double boxOutlineOpacity;

        public HintMeshBuilder() {
        }

        public HintMeshBuilder(HintMesh hintMesh) {
            this.visible = hintMesh.visible;
            this.type = hintMesh.type;
            this.hints = hintMesh.hints;
            this.focusedKeySequence = hintMesh.focusedKeySequence;
            this.fontName = hintMesh.fontName;
            this.fontSize = hintMesh.fontSize;
            this.fontHexColor = hintMesh.fontHexColor;
            this.fontOpacity = hintMesh.fontOpacity;
            this.prefixFontHexColor = hintMesh.prefixFontHexColor;
            this.highlightFontScale = hintMesh.highlightFontScale;
            this.boxHexColor = hintMesh.boxHexColor;
            this.boxOpacity = hintMesh.boxOpacity;
            this.boxBorderThickness = hintMesh.boxBorderThickness;
            this.boxOutlineHexColor = hintMesh.boxOutlineHexColor;
            this.boxOutlineOpacity = hintMesh.boxOutlineOpacity;
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

        public int fontSize() {
            return fontSize;
        }

        public String fontHexColor() {
            return fontHexColor;
        }

        public double fontOpacity() {
            return fontOpacity;
        }

        public String prefixFontHexColor() {
            return prefixFontHexColor;
        }

        public double highlightFontScale() {
            return highlightFontScale;
        }

        public String boxHexColor() {
            return boxHexColor;
        }

        public double boxOpacity() {
            return boxOpacity;
        }

        public int boxBorderThickness() {
            return boxBorderThickness;
        }

        public String boxOutlineHexColor() {
            return boxOutlineHexColor;
        }

        public double boxOutlineOpacity() {
            return boxOutlineOpacity;
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

        public HintMeshBuilder fontSize(int fontSize) {
            this.fontSize = fontSize;
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

        public HintMeshBuilder prefixFontHexColor(
                String prefixFontHexColor) {
            this.prefixFontHexColor = prefixFontHexColor;
            return this;
        }

        public HintMeshBuilder highlightFontScale(
                double highlightFontScale) {
            this.highlightFontScale = highlightFontScale;
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

        public HintMeshBuilder boxBorderThickness(int boxBorderThickness) {
            this.boxBorderThickness = boxBorderThickness;
            return this;
        }

        public HintMeshBuilder boxOutlineHexColor(String boxOutlineHexColor) {
            this.boxOutlineHexColor = boxOutlineHexColor;
            return this;
        }

        public HintMeshBuilder boxOutlineOpacity(double boxOutlineOpacity) {
            this.boxOutlineOpacity = boxOutlineOpacity;
            return this;
        }

        public HintMesh build() {
            return new HintMesh(visible, type, hints, focusedKeySequence, fontName,
                    fontSize,
                    fontHexColor, fontOpacity,
                    prefixFontHexColor, highlightFontScale,
                    boxHexColor, boxOpacity,
                    boxBorderThickness,
                    boxOutlineHexColor, boxOutlineOpacity);
        }
    }

}
