package jmouseable.jmouseable;

import java.util.List;

public record HintMeshConfiguration(boolean enabled, HintMeshType type,
                                    HintMeshCenter center,
                                    List<Key> selectionKeys, Key undoKey, String fontName,
                                    int fontSize, String fontHexColor,
                                    String selectedPrefixFontHexColor, String boxHexColor,
                                    String nextModeAfterSelection,
                                    Button clickButtonAfterSelection,
                                    boolean saveMousePositionAfterSelection) {

    public static class HintMeshConfigurationBuilder {
        private Boolean enabled;
        private HintMeshType type;
        private HintMeshCenter center;
        private List<Key> selectionKeys;
        private Key undoKey;
        private String fontName;
        private Integer fontSize;
        private String fontHexColor;
        private String selectedPrefixFontHexColor;
        private String boxHexColor;
        private String nextModeAfterSelection;
        private Button clickButtonAfterSelection;
        private boolean saveMousePositionAfterSelection;

        public HintMeshConfigurationBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public HintMeshConfigurationBuilder type(HintMeshType type) {
            this.type = type;
            return this;
        }

        public HintMeshConfigurationBuilder center(HintMeshCenter center) {
            this.center = center;
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

        public HintMeshConfigurationBuilder selectedPrefixFontHexColor(
                String selectedPrefixFontHexColor) {
            this.selectedPrefixFontHexColor = selectedPrefixFontHexColor;
            return this;
        }

        public HintMeshConfigurationBuilder boxHexColor(String boxHexColor) {
            this.boxHexColor = boxHexColor;
            return this;
        }

        public HintMeshConfigurationBuilder nextModeAfterSelection(
                String nextModeAfterSelection) {
            this.nextModeAfterSelection = nextModeAfterSelection;
            return this;
        }

        public HintMeshConfigurationBuilder clickButtonAfterSelection(
                Button clickButtonAfterSelection) {
            this.clickButtonAfterSelection = clickButtonAfterSelection;
            return this;
        }

        public HintMeshConfigurationBuilder saveMousePositionAfterSelection(
                boolean saveMousePositionAfterSelection) {
            this.saveMousePositionAfterSelection = saveMousePositionAfterSelection;
            return this;
        }

        public HintMeshType type() {
            return type;
        }

        public HintMeshCenter center() {
            return center;
        }

        public Boolean enabled() {
            return enabled;
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

        public String selectedPrefixFontHexColor() {
            return selectedPrefixFontHexColor;
        }

        public String boxHexColor() {
            return boxHexColor;
        }

        public String nextModeAfterSelection() {
            return nextModeAfterSelection;
        }

        public Button clickButtonAfterSelection() {
            return clickButtonAfterSelection;
        }

        public boolean saveMousePositionAfterSelection() {
            return saveMousePositionAfterSelection;
        }

        public HintMeshConfiguration build() {
            return new HintMeshConfiguration(enabled, type, center, selectionKeys,
                    undoKey, fontName, fontSize, fontHexColor, selectedPrefixFontHexColor,
                    boxHexColor, nextModeAfterSelection, clickButtonAfterSelection,
                    saveMousePositionAfterSelection);
        }

    }

}
