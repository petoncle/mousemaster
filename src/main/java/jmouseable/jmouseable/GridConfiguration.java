package jmouseable.jmouseable;

public record GridConfiguration(Area area, Synchronization synchronization, int rowCount,
                                int columnCount, boolean visible, String lineHexColor,
                                int lineThickness) {

    public static class GridConfigurationBuilder {
        private Area area;
        private Synchronization synchronization;
        private Integer rowCount;
        private Integer columnCount;
        private Boolean visible;
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

        public GridConfigurationBuilder visible(boolean visible) {
            this.visible = visible;
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

        public Integer rowCount() {
            return rowCount;
        }

        public Area area() {
            return area;
        }

        public Synchronization synchronization() {
            return synchronization;
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

        public GridConfiguration build() {
            return new GridConfiguration(area, synchronization, rowCount,
                    columnCount, visible, lineHexColor, lineThickness);
        }

    }

}
