package jmouseable.jmouseable;

import jmouseable.jmouseable.GridArea.GridAreaBuilder;

public record GridConfiguration(GridArea area, Synchronization synchronization, int rowCount,
                                int columnCount, boolean lineVisible,
                                String lineHexColor, int lineThickness) {

    public static class GridConfigurationBuilder {
        private GridAreaBuilder area = new GridAreaBuilder();
        private Synchronization synchronization;
        private Integer rowCount;
        private Integer columnCount;
        private Boolean lineVisible;
        private String lineHexColor;
        private Integer lineThickness;

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

        public GridAreaBuilder area() {
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
            return new GridConfiguration(area.build(), synchronization, rowCount,
                    columnCount, lineVisible, lineHexColor, lineThickness);
        }

    }

}
