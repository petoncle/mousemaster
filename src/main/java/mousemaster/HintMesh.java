package mousemaster;

import java.util.List;

/**
 * Unlike a grid, it does not necessarily have fixed-size cells.
 */
public record HintMesh(boolean visible, HintMeshType type, List<Hint> hints, List<Key> focusedKeySequence,
                       String fontName, int fontSize, String fontHexColor,
                       String selectedPrefixFontHexColor, String boxHexColor,
                       double boxOpacity) {

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
        private String selectedPrefixFontHexColor;
        private String boxHexColor;
        private double boxOpacity;

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
            this.selectedPrefixFontHexColor = hintMesh.selectedPrefixFontHexColor;
            this.boxHexColor = hintMesh.boxHexColor;
            this.boxOpacity = hintMesh.boxOpacity;
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

        public String selectedPrefixFontHexColor() {
            return selectedPrefixFontHexColor;
        }

        public String boxHexColor() {
            return boxHexColor;
        }

        public double boxOpacity() {
            return boxOpacity;
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

        public HintMeshBuilder selectedPrefixFontHexColor(
                String selectedPrefixFontHexColor) {
            this.selectedPrefixFontHexColor = selectedPrefixFontHexColor;
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

        public HintMesh build() {
            return new HintMesh(visible, type, hints, focusedKeySequence, fontName,
                    fontSize,
                    fontHexColor, selectedPrefixFontHexColor, boxHexColor, boxOpacity);
        }
    }

}
