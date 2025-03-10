package mousemaster;

import mousemaster.HintMeshType.HintMeshTypeBuilder;

import java.util.List;
import java.util.Set;

public record HintMeshConfiguration(boolean enabled,
                                    boolean visible,
                                    boolean moveMouse,
                                    HintMeshTypeAndSelectionKeys typeAndSelectionKeys, Set<Key> undoKeys,
                                    String fontName, FontWeight fontWeight,
                                    double fontSize, double fontSpacingPercent, String fontHexColor, double fontOpacity,
                                    double fontOutlineThickness, String fontOutlineHexColor, double fontOutlineOpacity,
                                    double fontShadowBlurRadius, String fontShadowHexColor, double fontShadowOpacity, double fontShadowHorizontalOffset, double fontShadowVerticalOffset,
                                    String prefixFontHexColor,
                                    String boxHexColor,
                                    double boxOpacity,
                                    double boxBorderThickness,
                                    double boxBorderLength,
                                    String boxBorderHexColor,
                                    double boxBorderOpacity,
                                    double boxWidthPercent,
                                    double boxHeightPercent,
                                    int subgridRowCount,
                                    int subgridColumnCount,
                                    double subgridBorderThickness,
                                    double subgridBorderLength,
                                    String subgridBorderHexColor,
                                    double subgridBorderOpacity,
                                    String modeAfterSelection,
                                    boolean swallowHintEndKeyPress,
                                    boolean savePositionAfterSelection) {

    public static class HintMeshConfigurationBuilder {
        private Boolean enabled;
        private Boolean visible;
        private Boolean moveMouse;
        private HintMeshTypeBuilder type = new HintMeshTypeBuilder();
        private List<Key> selectionKeys;
        private Set<Key> undoKeys;
        private String fontName;
        private FontWeight fontWeight;
        private Double fontSize;
        private Double fontSpacingPercent;
        private String fontHexColor;
        private Double fontOpacity;
        private Double fontOutlineThickness;
        private String fontOutlineHexColor;
        private Double fontOutlineOpacity;
        private Double fontShadowBlurRadius;
        private String fontShadowHexColor;
        private Double fontShadowOpacity;
        private Double fontShadowHorizontalOffset;
        private Double fontShadowVerticalOffset;
        private String prefixFontHexColor;
        private String boxHexColor;
        private Double boxOpacity;
        private Double boxBorderThickness;
        private Double boxBorderLength;
        private String boxBorderHexColor;
        private Double boxBorderOpacity;
        private Double boxWidthPercent;
        private Double boxHeightPercent;
        private Integer subgridRowCount;
        private Integer subgridColumnCount;
        private Double subgridBorderThickness;
        private Double subgridBorderLength;
        private String subgridBorderHexColor;
        private Double subgridBorderOpacity;
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

        public HintMeshConfigurationBuilder undoKeys(Set<Key> undoKeys) {
            this.undoKeys = undoKeys;
            return this;
        }

        public HintMeshConfigurationBuilder fontName(String fontName) {
            this.fontName = fontName;
            return this;
        }

        public HintMeshConfigurationBuilder fontWeight(FontWeight fontWeight) {
            this.fontWeight = fontWeight;
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

        public HintMeshConfigurationBuilder fontShadowBlurRadius(double fontShadowBlurRadius) {
            this.fontShadowBlurRadius = fontShadowBlurRadius;
            return this;
        }

        public HintMeshConfigurationBuilder fontShadowHexColor(String fontShadowHexColor) {
            this.fontShadowHexColor = fontShadowHexColor;
            return this;
        }

        public HintMeshConfigurationBuilder fontShadowOpacity(double fontShadowOpacity) {
            this.fontShadowOpacity = fontShadowOpacity;
            return this;
        }

        public HintMeshConfigurationBuilder fontShadowHorizontalOffset(double fontShadowHorizontalOffset) {
            this.fontShadowHorizontalOffset = fontShadowHorizontalOffset;
            return this;
        }

        public HintMeshConfigurationBuilder fontShadowVerticalOffset(double fontShadowVerticalOffset) {
            this.fontShadowVerticalOffset = fontShadowVerticalOffset;
            return this;
        }

        public HintMeshConfigurationBuilder prefixFontHexColor(
                String prefixFontHexColor) {
            this.prefixFontHexColor = prefixFontHexColor;
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

        public HintMeshConfigurationBuilder boxWidthPercent(double boxWidthPercent) {
            this.boxWidthPercent = boxWidthPercent;
            return this;
        }

        public HintMeshConfigurationBuilder boxHeightPercent(double boxHeightPercent) {
            this.boxHeightPercent = boxHeightPercent;
            return this;
        }

        public HintMeshConfigurationBuilder subgridRowCount(int subgridRowCount) {
            this.subgridRowCount = subgridRowCount;
            return this;
        }

        public HintMeshConfigurationBuilder subgridColumnCount(int subgridColumnCount) {
            this.subgridColumnCount = subgridColumnCount;
            return this;
        }

        public HintMeshConfigurationBuilder subgridBorderThickness(double subgridBorderThickness) {
            this.subgridBorderThickness = subgridBorderThickness;
            return this;
        }

        public HintMeshConfigurationBuilder subgridBorderLength(double subgridBorderLength) {
            this.subgridBorderLength = subgridBorderLength;
            return this;
        }

        public HintMeshConfigurationBuilder subgridBorderHexColor(String subgridBorderHexColor) {
            this.subgridBorderHexColor = subgridBorderHexColor;
            return this;
        }

        public HintMeshConfigurationBuilder subgridBorderOpacity(double subgridBorderOpacity) {
            this.subgridBorderOpacity = subgridBorderOpacity;
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

        public Set<Key> undoKeys() {
            return undoKeys;
        }

        public String fontName() {
            return fontName;
        }

        public FontWeight fontWeight() {
            return fontWeight;
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

        public Double fontShadowBlurRadius() {
            return fontShadowBlurRadius;
        }

        public String fontShadowHexColor() {
            return fontShadowHexColor;
        }

        public Double fontShadowOpacity() {
            return fontShadowOpacity;
        }

        public Double fontShadowHorizontalOffset() {
            return fontShadowHorizontalOffset;
        }

        public Double fontShadowVerticalOffset() {
            return fontShadowVerticalOffset;
        }

        public String prefixFontHexColor() {
            return prefixFontHexColor;
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

        public Double boxWidthPercent() {
            return boxWidthPercent;
        }

        public Double boxHeightPercent() {
            return boxHeightPercent;
        }

        public Integer subgridRowCount() {
            return subgridRowCount;
        }

        public Integer subgridColumnCount() {
            return subgridColumnCount;
        }

        public Double subgridBorderThickness() {
            return subgridBorderThickness;
        }

        public Double subgridBorderLength() {
            return subgridBorderLength;
        }

        public String subgridBorderHexColor() {
            return subgridBorderHexColor;
        }

        public Double subgridBorderOpacity() {
            return subgridBorderOpacity;
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
                    undoKeys, fontName, fontWeight,
                    fontSize, fontSpacingPercent, fontHexColor, fontOpacity,
                    fontOutlineThickness, fontOutlineHexColor, fontOutlineOpacity,
                    fontShadowBlurRadius, fontShadowHexColor, fontShadowOpacity, fontShadowHorizontalOffset, fontShadowVerticalOffset,
                    prefixFontHexColor,
                    boxHexColor, boxOpacity, boxBorderThickness,
                    boxBorderLength,
                    boxBorderHexColor, boxBorderOpacity,
                    boxWidthPercent,
                    boxHeightPercent,
                    subgridRowCount,
                    subgridColumnCount,
                    subgridBorderThickness,
                    subgridBorderLength,
                    subgridBorderHexColor,
                    subgridBorderOpacity,
                    modeAfterSelection, swallowHintEndKeyPress,
                    savePositionAfterSelection);
        }

    }

}
