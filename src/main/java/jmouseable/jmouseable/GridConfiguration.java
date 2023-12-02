package jmouseable.jmouseable;

import java.util.List;

public record GridConfiguration(Area area, Synchronization synchronization, int rowCount,
                                int columnCount, boolean hintEnabled, List<Key> hintKeys,
                                Key hintUndoKey, String hintFontName, int hintFontSize,
                                String hintFontHexColor,
                                String hintSelectedPrefixFontHexColor,
                                String hintBoxHexColor,
                                String modeAfterHintSelection,
                                Button clickButtonAfterHintSelection, boolean lineVisible,
                                String lineHexColor, int lineThickness) {

    public static class GridConfigurationBuilder {
        private Area area;
        private Synchronization synchronization;
        private Integer rowCount;
        private Integer columnCount;
        private Boolean hintEnabled;
        private List<Key> hintKeys;
        private Key hintUndoKey;
        private String hintFontName;
        private Integer hintFontSize;
        private String hintFontHexColor;
        private String hintSelectedPrefixFontHexColor;
        private String hintBoxHexColor;
        private String modeAfterHintSelection;
        private Button clickButtonAfterHintSelection;
        private Boolean lineVisible;
        private String lineHexColor;
        private Integer lineThickness;

        public GridConfigurationBuilder area(Area area) {
            this.area = area;
            return this;
        }

        public GridConfigurationBuilder synchronization(Synchronization synchronization) {
            this.synchronization = synchronization;
            return this;
        }

        public GridConfigurationBuilder rowCount(int rowCount) {
            this.rowCount = rowCount;
            return this;
        }

        public GridConfigurationBuilder columnCount(int columnCount) {
            this.columnCount = columnCount;
            return this;
        }

        public GridConfigurationBuilder hintEnabled(boolean hintEnabled) {
            this.hintEnabled = hintEnabled;
            return this;
        }

        public GridConfigurationBuilder hintKeys(List<Key> hintKeys) {
            this.hintKeys = hintKeys;
            return this;
        }

        public GridConfigurationBuilder hintUndoKey(Key hintUndoKey) {
            this.hintUndoKey = hintUndoKey;
            return this;
        }

        public GridConfigurationBuilder hintFontName(String hintFontName) {
            this.hintFontName = hintFontName;
            return this;
        }

        public GridConfigurationBuilder hintFontSize(int hintFontSize) {
            this.hintFontSize = hintFontSize;
            return this;
        }

        public GridConfigurationBuilder hintFontHexColor(String hintFontHexColor) {
            this.hintFontHexColor = hintFontHexColor;
            return this;
        }

        public GridConfigurationBuilder hintSelectedPrefixFontHexColor(
                String hintSelectedPrefixFontHexColor) {
            this.hintSelectedPrefixFontHexColor = hintSelectedPrefixFontHexColor;
            return this;
        }

        public GridConfigurationBuilder hintBoxHexColor(String hintBoxHexColor) {
            this.hintBoxHexColor = hintBoxHexColor;
            return this;
        }

        public GridConfigurationBuilder modeAfterHintSelection(
                String modeAfterHintSelection) {
            this.modeAfterHintSelection = modeAfterHintSelection;
            return this;
        }

        public GridConfigurationBuilder clickButtonAfterHintSelection(
                Button clickButtonAfterHintSelection) {
            this.clickButtonAfterHintSelection = clickButtonAfterHintSelection;
            return this;
        }

        public GridConfigurationBuilder lineVisible(boolean visible) {
            this.lineVisible = visible;
            return this;
        }

        public GridConfigurationBuilder lineHexColor(String lineHexColor) {
            this.lineHexColor = lineHexColor;
            return this;
        }

        public GridConfigurationBuilder lineThickness(int lineThickness) {
            this.lineThickness = lineThickness;
            return this;
        }

        public Area area() {
            return area;
        }

        public Synchronization synchronization() {
            return synchronization;
        }

        public Integer rowCount() {
            return rowCount;
        }

        public Integer columnCount() {
            return columnCount;
        }

        public Boolean hintEnabled() {
            return hintEnabled;
        }

        public List<Key> hintKeys() {
            return hintKeys;
        }

        public Key hintUndoKey() {
            return hintUndoKey;
        }

        public String hintFontName() {
            return hintFontName;
        }

        public Integer hintFontSize() {
            return hintFontSize;
        }

        public String hintFontHexColor() {
            return hintFontHexColor;
        }

        public String hintSelectedPrefixFontHexColor() {
            return hintSelectedPrefixFontHexColor;
        }

        public String hintBoxHexColor() {
            return hintBoxHexColor;
        }

        public String modeAfterHintSelection() {
            return modeAfterHintSelection;
        }

        public Button clickButtonAfterHintSelection() {
            return clickButtonAfterHintSelection;
        }

        public Boolean lineVisible() {
            return lineVisible;
        }

        public String lineHexColor() {
            return lineHexColor;
        }

        public Integer lineThickness() {
            return lineThickness;
        }

        public GridConfiguration build() {
            return new GridConfiguration(area, synchronization, rowCount, columnCount,
                    hintEnabled, hintKeys, hintUndoKey, hintFontName, hintFontSize,
                    hintFontHexColor, hintSelectedPrefixFontHexColor, hintBoxHexColor,
                    modeAfterHintSelection, clickButtonAfterHintSelection, lineVisible,
                    lineHexColor, lineThickness);
        }

    }

}
