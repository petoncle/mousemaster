package mousemaster;

import mousemaster.HintMeshType.HintMeshTypeBuilder;

import java.util.List;

public record HintMeshConfiguration(boolean enabled,
                                    boolean visible,
                                    boolean moveMouse,
                                    HintMeshTypeAndSelectionKeys typeAndSelectionKeys, Key undoKey, String fontName,
                                    double fontSize, double fontSpacingPercent, String fontHexColor, double fontOpacity,
                                    double fontOutlineThickness, String fontOutlineHexColor, double fontOutlineOpacity,
                                    String prefixFontHexColor,
                                    double highlightFontScale,
                                    String boxHexColor,
                                    double boxOpacity,
                                    double boxBorderThickness,
                                    double boxBorderLength,
                                    String boxBorderHexColor,
                                    double boxBorderOpacity,
                                    boolean expandBoxes,
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
        private Double fontSize;
        private Double fontSpacingPercent;
        private String fontHexColor;
        private Double fontOpacity;
        private Double fontOutlineThickness;
        private String fontOutlineHexColor;
        private Double fontOutlineOpacity;
        private String prefixFontHexColor;
        private Double highlightFontScale;
        private String boxHexColor;
        private Double boxOpacity;
        private Double boxBorderThickness;
        private Double boxBorderLength;
        private String boxBorderHexColor;
        private Double boxBorderOpacity;
        private Boolean expandBoxes;
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

        public HintMeshConfigurationBuilder fontSize(double fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        public HintMeshConfigurationBuilder fontSpacingPercent(double fontSpacingPercent) {
            this.fontSpacingPercent = fontSpacingPercent;
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

        public HintMeshConfigurationBuilder fontOutlineThickness(double fontOutlineThickness) {
            this.fontOutlineThickness = fontOutlineThickness;
            return this;
        }

        public HintMeshConfigurationBuilder fontOutlineHexColor(String fontOutlineHexColor) {
            this.fontOutlineHexColor = fontOutlineHexColor;
            return this;
        }

        public HintMeshConfigurationBuilder fontOutlineOpacity(double fontOutlineOpacity) {
            this.fontOutlineOpacity = fontOutlineOpacity;
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

        public HintMeshConfigurationBuilder boxBorderThickness(double boxBorderThickness) {
            this.boxBorderThickness = boxBorderThickness;
            return this;
        }

        public HintMeshConfigurationBuilder boxBorderLength(double boxBorderLength) {
            this.boxBorderLength = boxBorderLength;
            return this;
        }

        public HintMeshConfigurationBuilder boxBorderHexColor(String boxBorderHexColor) {
            this.boxBorderHexColor = boxBorderHexColor;
            return this;
        }

        public HintMeshConfigurationBuilder boxBorderOpacity(double boxBorderOpacity) {
            this.boxBorderOpacity = boxBorderOpacity;
            return this;
        }

        public HintMeshConfigurationBuilder expandBoxes(boolean expandBoxes) {
            this.expandBoxes = expandBoxes;
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

        public Double fontSize() {
            return fontSize;
        }

        public Double fontSpacingPercent() {
            return fontSpacingPercent;
        }

        public String fontHexColor() {
            return fontHexColor;
        }

        public Double fontOpacity() {
            return fontOpacity;
        }

        public Double fontOutlineThickness() {
            return fontOutlineThickness;
        }

        public String fontOutlineHexColor() {
            return fontOutlineHexColor;
        }

        public Double fontOutlineOpacity() {
            return fontOutlineOpacity;
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

        public Boolean expandBoxes() {
            return expandBoxes;
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
                    undoKey, fontName,
                    fontSize, fontSpacingPercent, fontHexColor, fontOpacity,
                    fontOutlineThickness, fontOutlineHexColor, fontOutlineOpacity,
                    prefixFontHexColor,
                    highlightFontScale,
                    boxHexColor, boxOpacity, boxBorderThickness,
                    boxBorderLength,
                    boxBorderHexColor, boxBorderOpacity,
                    expandBoxes,
                    modeAfterSelection, swallowHintEndKeyPress,
                    savePositionAfterSelection);
        }

    }

}
