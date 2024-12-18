package mousemaster;

import mousemaster.HintMeshType.HintMeshTypeBuilder;

import java.util.List;

public record HintMeshConfiguration(boolean enabled,
                                    boolean visible,
                                    boolean moveMouse,
                                    HintMeshTypeAndSelectionKeys typeAndSelectionKeys, Key undoKey, String fontName,
                                    int fontSize, String fontHexColor, double fontOpacity,
                                    String prefixFontHexColor,
                                    double highlightFontScale,
                                    String boxHexColor,
                                    double boxOpacity,
                                    int boxBorderThickness,
                                    String boxOutlineHexColor,
                                    double boxOutlineOpacity,
                                    String modeAfterSelection,
                                    boolean swallowHintEndKeyPress,
                                    boolean savePositionAfterSelection) {

    public static class HintMeshConfigurationBuilder {
        private Boolean enabled;
        private Boolean visible;
        private Boolean moveMouse;
        private HintMeshTypeBuilder type = new HintMeshTypeBuilder();
        private List<Key> selectionKeys;
        private Key undoKey;
        private String fontName;
        private Integer fontSize;
        private String fontHexColor;
        private Double fontOpacity;
        private String prefixFontHexColor;
        private Double highlightFontScale;
        private String boxHexColor;
        private Double boxOpacity;
        private Integer boxBorderThickness;
        private String boxOutlineHexColor;
        private Double boxOutlineOpacity;
        private String modeAfterSelection;
        private Boolean swallowHintEndKeyPress;
        private Boolean savePositionAfterSelection;

        public HintMeshConfigurationBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public HintMeshConfigurationBuilder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public HintMeshConfigurationBuilder moveMouse(boolean moveMouse) {
            this.moveMouse = moveMouse;
            return this;
        }

        public HintMeshConfigurationBuilder selectionKeys(List<Key> selectionKeys) {
            this.selectionKeys = selectionKeys;
            return this;
        }

        public HintMeshConfigurationBuilder undoKey(Key undoKey) {
            this.undoKey = undoKey;
            return this;
        }

        public HintMeshConfigurationBuilder fontName(String fontName) {
            this.fontName = fontName;
            return this;
        }

        public HintMeshConfigurationBuilder fontSize(int fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        public HintMeshConfigurationBuilder fontHexColor(String fontHexColor) {
            this.fontHexColor = fontHexColor;
            return this;
        }

        public HintMeshConfigurationBuilder fontOpacity(double fontOpacity) {
            this.fontOpacity = fontOpacity;
            return this;
        }

        public HintMeshConfigurationBuilder prefixFontHexColor(
                String prefixFontHexColor) {
            this.prefixFontHexColor = prefixFontHexColor;
            return this;
        }

        public HintMeshConfigurationBuilder highlightFontScale(
                Double highlightFontScale) {
            this.highlightFontScale = highlightFontScale;
            return this;
        }

        public HintMeshConfigurationBuilder boxHexColor(String boxHexColor) {
            this.boxHexColor = boxHexColor;
            return this;
        }

        public HintMeshConfigurationBuilder boxOpacity(double boxOpacity) {
            this.boxOpacity = boxOpacity;
            return this;
        }

        public HintMeshConfigurationBuilder boxBorderThickness(int boxBorderThickness) {
            this.boxBorderThickness = boxBorderThickness;
            return this;
        }

        public HintMeshConfigurationBuilder boxOutlineHexColor(String boxOutlineHexColor) {
            this.boxOutlineHexColor = boxOutlineHexColor;
            return this;
        }

        public HintMeshConfigurationBuilder boxOutlineOpacity(double boxOutlineOpacity) {
            this.boxOutlineOpacity = boxOutlineOpacity;
            return this;
        }

        public HintMeshConfigurationBuilder modeAfterSelection(
                String modeAfterSelection) {
            this.modeAfterSelection = modeAfterSelection;
            return this;
        }

        public HintMeshConfigurationBuilder swallowHintEndKeyPress(
                boolean swallowHintEndKeyPress) {
            this.swallowHintEndKeyPress = swallowHintEndKeyPress;
            return this;
        }

        public HintMeshConfigurationBuilder savePositionAfterSelection(
                boolean savePositionAfterSelection) {
            this.savePositionAfterSelection = savePositionAfterSelection;
            return this;
        }

        public HintMeshTypeBuilder type() {
            return type;
        }

        public Boolean enabled() {
            return enabled;
        }

        public Boolean visible() {
            return visible;
        }

        public Boolean moveMouse() {
            return moveMouse;
        }

        public List<Key> selectionKeys() {
            return selectionKeys;
        }

        public Key undoKey() {
            return undoKey;
        }

        public String fontName() {
            return fontName;
        }

        public Integer fontSize() {
            return fontSize;
        }

        public String fontHexColor() {
            return fontHexColor;
        }

        public Double fontOpacity() {
            return fontOpacity;
        }

        public String prefixFontHexColor() {
            return prefixFontHexColor;
        }

        public Double highlightFontScale() {
            return highlightFontScale;
        }

        public String boxHexColor() {
            return boxHexColor;
        }

        public Double boxOpacity() {
            return boxOpacity;
        }

        public Integer boxBorderThickness() {
            return boxBorderThickness;
        }

        public String boxOutlineHexColor() {
            return boxOutlineHexColor;
        }

        public Double boxOutlineOpacity() {
            return boxOutlineOpacity;
        }

        public String modeAfterSelection() {
            return modeAfterSelection;
        }

        public Boolean swallowHintEndKeyPress() {
            return swallowHintEndKeyPress;
        }

        public Boolean savePositionAfterSelection() {
            return savePositionAfterSelection;
        }

        public HintMeshConfiguration build() {
            return new HintMeshConfiguration(enabled, visible, moveMouse,
                    new HintMeshTypeAndSelectionKeys(type.build(), selectionKeys),
                    undoKey, fontName, fontSize, fontHexColor, fontOpacity,
                    prefixFontHexColor,
                    highlightFontScale,
                    boxHexColor, boxOpacity, boxBorderThickness,
                    boxOutlineHexColor, boxOutlineOpacity,
                    modeAfterSelection, swallowHintEndKeyPress,
                    savePositionAfterSelection);
        }

    }

}
