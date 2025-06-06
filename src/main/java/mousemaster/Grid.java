package mousemaster;

public record Grid(int x, int y, int width, int height, int rowCount, int columnCount,
                   boolean lineVisible, String lineHexColor, double lineThickness) {

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

        public Grid build() {
            return new Grid(x, y, width, height, rowCount, columnCount, lineVisible,
                    lineHexColor, lineThickness);
        }
    }

}
