package mousemaster;

import java.time.Duration;

public record Grid(int x, int y, int width, int height, int rowCount, int columnCount,
                   boolean lineVisible, String lineHexColor, double lineThickness,
                   double lineOpacity, String backgroundHexColor, double backgroundOpacity,
                   boolean transitionAnimationEnabled, Duration transitionAnimationDuration,
                   boolean fadeAnimationEnabled, Duration fadeAnimationDuration) {

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
        private boolean lineVisible;
        private String lineHexColor;
        private double lineThickness;
        private double lineOpacity;
        private String backgroundHexColor;
        private double backgroundOpacity;
        private boolean transitionAnimationEnabled;
        private Duration transitionAnimationDuration;
        private boolean fadeAnimationEnabled;
        private Duration fadeAnimationDuration;

        public GridBuilder() {
        }

        public GridBuilder(Grid grid) {
            this.x = grid.x;
            this.y = grid.y;
            this.width = grid.width;
            this.height = grid.height;
            this.rowCount = grid.rowCount;
            this.columnCount = grid.columnCount;
            this.lineVisible = grid.lineVisible;
            this.lineHexColor = grid.lineHexColor;
            this.lineThickness = grid.lineThickness;
            this.lineOpacity = grid.lineOpacity;
            this.backgroundHexColor = grid.backgroundHexColor;
            this.backgroundOpacity = grid.backgroundOpacity;
            this.transitionAnimationEnabled = grid.transitionAnimationEnabled;
            this.transitionAnimationDuration = grid.transitionAnimationDuration;
            this.fadeAnimationEnabled = grid.fadeAnimationEnabled;
            this.fadeAnimationDuration = grid.fadeAnimationDuration;
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

        public boolean lineVisible() {
            return lineVisible;
        }

        public String lineHexColor() {
            return lineHexColor;
        }

        public double lineThickness() {
            return lineThickness;
        }

        public double lineOpacity() {
            return lineOpacity;
        }

        public String backgroundHexColor() {
            return backgroundHexColor;
        }

        public double backgroundOpacity() {
            return backgroundOpacity;
        }

        public boolean transitionAnimationEnabled() {
            return transitionAnimationEnabled;
        }

        public Duration transitionAnimationDuration() {
            return transitionAnimationDuration;
        }

        public boolean fadeAnimationEnabled() {
            return fadeAnimationEnabled;
        }

        public Duration fadeAnimationDuration() {
            return fadeAnimationDuration;
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

        public GridBuilder lineVisible(boolean lineVisible) {
            this.lineVisible = lineVisible;
            return this;
        }

        public GridBuilder lineHexColor(String lineHexColor) {
            this.lineHexColor = lineHexColor;
            return this;
        }

        public GridBuilder lineThickness(double lineThickness) {
            this.lineThickness = lineThickness;
            return this;
        }

        public GridBuilder lineOpacity(double lineOpacity) {
            this.lineOpacity = lineOpacity;
            return this;
        }

        public GridBuilder backgroundHexColor(String backgroundHexColor) {
            this.backgroundHexColor = backgroundHexColor;
            return this;
        }

        public GridBuilder backgroundOpacity(double backgroundOpacity) {
            this.backgroundOpacity = backgroundOpacity;
            return this;
        }

        public GridBuilder transitionAnimationEnabled(boolean transitionAnimationEnabled) {
            this.transitionAnimationEnabled = transitionAnimationEnabled;
            return this;
        }

        public GridBuilder transitionAnimationDuration(Duration transitionAnimationDuration) {
            this.transitionAnimationDuration = transitionAnimationDuration;
            return this;
        }

        public GridBuilder fadeAnimationEnabled(boolean fadeAnimationEnabled) {
            this.fadeAnimationEnabled = fadeAnimationEnabled;
            return this;
        }

        public GridBuilder fadeAnimationDuration(Duration fadeAnimationDuration) {
            this.fadeAnimationDuration = fadeAnimationDuration;
            return this;
        }

        public Grid build() {
            return new Grid(x, y, width, height, rowCount, columnCount, lineVisible,
                    lineHexColor, lineThickness, lineOpacity, backgroundHexColor,
                    backgroundOpacity, transitionAnimationEnabled, transitionAnimationDuration,
                    fadeAnimationEnabled, fadeAnimationDuration);
        }
    }

}
