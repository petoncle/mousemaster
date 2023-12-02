package jmouseable.jmouseable;

import java.util.List;

public record HintMeshConfiguration(boolean enabled, HintMeshType type,
                                    List<Key> selectionKeys, Key undoKey, String fontName,
                                    int fontSize, String fontHexColor,
                                    String selectedPrefixFontHexColor, String boxHexColor,
                                    String modeAfterSelection,
                                    Button clickButtonAfterSelection) {

    public static class HintMeshConfigurationBuilder {
        private Boolean enabled;
        private HintMeshType type;
        private List<Key> selectionKeys;
        private Key undoKey;
        private String fontName;
        private Integer fontSize;
        private String fontHexColor;
        private String selectedPrefixFontHexColor;
        private String boxHexColor;
        private String modeAfterSelection;
        private Button clickButtonAfterSelection;

        public HintMeshConfigurationBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public HintMeshConfigurationBuilder type(HintMeshType type) {
            this.type = type;
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

        public HintMeshConfigurationBuilder modeAfterSelection(
                String modeAfterSelection) {
            this.modeAfterSelection = modeAfterSelection;
            return this;
        }

        public HintMeshConfigurationBuilder clickButtonAfterSelection(
                Button clickButtonAfterSelection) {
            this.clickButtonAfterSelection = clickButtonAfterSelection;
            return this;
        }

        public HintMeshType type() {
            return type;
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

        public String modeAfterSelection() {
            return modeAfterSelection;
        }

        public Button clickButtonAfterSelection() {
            return clickButtonAfterSelection;
        }

        public HintMeshConfiguration build() {
            return new HintMeshConfiguration(enabled, type, selectionKeys, undoKey,
                    fontName, fontSize, fontHexColor, selectedPrefixFontHexColor,
                    boxHexColor, modeAfterSelection, clickButtonAfterSelection);
        }

    }

}
