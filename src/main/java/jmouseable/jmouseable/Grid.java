package jmouseable.jmouseable;

public record Grid(int rowCount, int columnCount, boolean visible,
                   String lineHexColor, int lineThickness) {

    public static class GridBuilder {
        private Integer rowCount;
        private Integer columnCount;
        private Boolean visible;
        private String lineHexColor;
        private Integer lineThickness;

        public GridBuilder rowCount(int rowCount) {
            this.rowCount = rowCount;
            return this;
        }

        public GridBuilder columnCount(int columnCount) {
            this.columnCount = columnCount;
            return this;
        }

        public GridBuilder visible(boolean visible) {
            this.visible = visible;
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

        public Integer rowCount() {
            return rowCount;
        }

        public Integer columnCount() {
            return columnCount;
        }

        public Boolean visible() {
            return visible;
        }

        public String lineHexColor() {
            return lineHexColor;
        }

        public Integer lineThickness() {
            return lineThickness;
        }

        public Grid build() {
            return new Grid(rowCount, columnCount, visible, lineHexColor, lineThickness);
        }

    }

}
