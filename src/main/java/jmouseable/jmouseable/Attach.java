package jmouseable.jmouseable;

public record Attach(int gridRowCount, int gridColumnCount, boolean showGrid,
                     String gridLineHexColor, int gridLineThickness) {

    public static class AttachBuilder {
        private Integer gridRowCount;
        private Integer gridColumnCount;
        private Boolean showGrid;
        private String gridLineHexColor;
        private Integer gridLineThickness;

        public AttachBuilder gridRowCount(int gridRowCount) {
            this.gridRowCount = gridRowCount;
            return this;
        }

        public AttachBuilder gridColumnCount(int gridColumnCount) {
            this.gridColumnCount = gridColumnCount;
            return this;
        }

        public AttachBuilder showGrid(boolean showGrid) {
            this.showGrid = showGrid;
            return this;
        }

        public AttachBuilder gridLineHexColor(String gridLineHexColor) {
            this.gridLineHexColor = gridLineHexColor;
            return this;
        }

        public AttachBuilder gridLineThickness(int gridLineThickness) {
            this.gridLineThickness = gridLineThickness;
            return this;
        }

        public Integer gridRowCount() {
            return gridRowCount;
        }

        public Integer gridColumnCount() {
            return gridColumnCount;
        }

        public Boolean showGrid() {
            return showGrid;
        }

        public String gridLineHexColor() {
            return gridLineHexColor;
        }

        public Integer gridLineThickness() {
            return gridLineThickness;
        }

        public Attach build() {
            return new Attach(gridRowCount, gridColumnCount, showGrid, gridLineHexColor,
                    gridLineThickness);
        }

    }

}
