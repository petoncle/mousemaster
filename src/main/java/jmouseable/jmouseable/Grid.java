package jmouseable.jmouseable;

import java.util.List;

public record Grid(int x, int y, int width, int height, int rowCount, int columnCount,
                   boolean hintEnabled, Hint[][] hints, List<Key> focusedHintKeySequence,
                   String hintFontName, int hintFontSize, String hintFontHexColor,
                   String hintBoxHexColor, boolean lineVisible, String lineHexColor,
                   int lineThickness) {

    public GridBuilder builder() {
        return new GridBuilder(this);
    }

    public static class GridBuilder {
        private int x;
        private int y;
        private int width;
        private int height;
        private int rowCount;
        private int columnCount;
        private boolean hintEnabled;
        private Hint[][] hints;
        private List<Key> focusedHintKeySequence = List.of();
        private String hintFontName;
        private int hintFontSize;
        private String hintFontHexColor;
        private String hintBoxHexColor;
        private boolean lineVisible;
        private String lineHexColor;
        private int lineThickness;

        public GridBuilder() {
        }

        public GridBuilder(Grid grid) {
            this.x = grid.x;
            this.y = grid.y;
            this.width = grid.width;
            this.height = grid.height;
            this.rowCount = grid.rowCount;
            this.columnCount = grid.columnCount;
            this.hintEnabled = grid.hintEnabled;
            this.hints = grid.hints;
            this.focusedHintKeySequence = grid.focusedHintKeySequence;
            this.hintFontName = grid.hintFontName;
            this.hintFontSize = grid.hintFontSize;
            this.hintFontHexColor = grid.hintFontHexColor;
            this.hintBoxHexColor = grid.hintBoxHexColor;
            this.lineVisible = grid.lineVisible;
            this.lineHexColor = grid.lineHexColor;
            this.lineThickness = grid.lineThickness;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public int rowCount() {
            return rowCount;
        }

        public int columnCount() {
            return columnCount;
        }

        public boolean hintEnabled() {
            return hintEnabled;
        }

        public Hint[][] hints() {
            return hints;
        }

        public List<Key> focusedHintKeySequence() {
            return focusedHintKeySequence;
        }

        public String hintFontName() {
            return hintFontName;
        }

        public int hintFontSize() {
            return hintFontSize;
        }

        public String hintFontHexColor() {
            return hintFontHexColor;
        }

        public String hintBoxHexColor() {
            return hintBoxHexColor;
        }

        public boolean lineVisible() {
            return lineVisible;
        }

        public String lineHexColor() {
            return lineHexColor;
        }

        public int lineThickness() {
            return lineThickness;
        }

        public GridBuilder x(int x) {
            this.x = x;
            return this;
        }

        public GridBuilder y(int y) {
            this.y = y;
            return this;
        }

        public GridBuilder width(int width) {
            this.width = width;
            return this;
        }

        public GridBuilder height(int height) {
            this.height = height;
            return this;
        }

        public GridBuilder rowCount(int rowCount) {
            this.rowCount = rowCount;
            return this;
        }

        public GridBuilder columnCount(int columnCount) {
            this.columnCount = columnCount;
            return this;
        }

        public GridBuilder hintEnabled(boolean hintEnabled) {
            this.hintEnabled = hintEnabled;
            return this;
        }

        public GridBuilder hints(Hint[][] hints) {
            this.hints = hints;
            return this;
        }

        public GridBuilder focusedHintKeySequence(List<Key> focusedHintKeySequence) {
            this.focusedHintKeySequence = focusedHintKeySequence;
            return this;
        }

        public GridBuilder hintFontName(String hintFontName) {
            this.hintFontName = hintFontName;
            return this;
        }

        public GridBuilder hintFontSize(int hintFontSize) {
            this.hintFontSize = hintFontSize;
            return this;
        }

        public GridBuilder hintFontHexColor(String hintFontHexColor) {
            this.hintFontHexColor = hintFontHexColor;
            return this;
        }

        public GridBuilder hintBoxHexColor(String hintBoxHexColor) {
            this.hintBoxHexColor = hintBoxHexColor;
            return this;
        }

        public GridBuilder lineVisible(boolean lineVisible) {
            this.lineVisible = lineVisible;
            return this;
        }

        public GridBuilder lineHexColor(String lineHexColor) {
            this.lineHexColor = lineHexColor;
            return this;
        }

        public GridBuilder lineThickness(int lineThickness) {
            this.lineThickness = lineThickness;
            return this;
        }

        public Grid build() {
            return new Grid(x, y, width, height, rowCount, columnCount, hintEnabled,
                    hints, focusedHintKeySequence, hintFontName, hintFontSize,
                    hintFontHexColor, hintBoxHexColor, lineVisible, lineHexColor,
                    lineThickness);
        }
    }

}
