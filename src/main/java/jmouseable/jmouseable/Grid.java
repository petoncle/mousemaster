package jmouseable.jmouseable;

public record Grid(int x, int y, int width, int height, int snapRowCount,
                   int snapColumnCount, String lineHexColor, int lineThickness) {

    public GridBuilder builder() {
        return new GridBuilder(this);
    }

    public static class GridBuilder {
        private int x;
        private int y;
        private int width;
        private int height;
        private int snapRowCount;
        private int snapColumnCount;
        private String lineHexColor;
        private int lineThickness;

        public GridBuilder() {
        }

        public GridBuilder(Grid grid) {
            this.x = grid.x;
            this.y = grid.y;
            this.width = grid.width;
            this.height = grid.height;
            this.snapRowCount = grid.snapRowCount;
            this.snapColumnCount = grid.snapColumnCount;
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

        public int snapRowCount() {
            return snapRowCount;
        }

        public int snapColumnCount() {
            return snapColumnCount;
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

        public GridBuilder snapRowCount(int snapRowCount) {
            this.snapRowCount = snapRowCount;
            return this;
        }

        public GridBuilder snapColumnCount(int snapColumnCount) {
            this.snapColumnCount = snapColumnCount;
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
            return new Grid(x, y, width, height, snapRowCount, snapColumnCount,
                    lineHexColor, lineThickness);
        }
    }

}
