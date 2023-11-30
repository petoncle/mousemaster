package jmouseable.jmouseable;

public record GridConfiguration(Area area,
                                Synchronization synchronization, int snapRowCount,
                                int snapColumnCount, boolean visible, String lineHexColor,
                                int lineThickness) {

    public static class GridConfigurationBuilder {
        private Area area;
        private Synchronization synchronization;
        private Integer snapRowCount;
        private Integer snapColumnCount;
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

        public GridConfigurationBuilder snapRowCount(int snapRowCount) {
            this.snapRowCount = snapRowCount;
            return this;
        }

        public GridConfigurationBuilder snapColumnCount(int snapColumnCount) {
            this.snapColumnCount = snapColumnCount;
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

        public Integer snapRowCount() {
            return snapRowCount;
        }

        public Area area() {
            return area;
        }

        public Synchronization synchronization() {
            return synchronization;
        }

        public Integer snapColumnCount() {
            return snapColumnCount;
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
            return new GridConfiguration(area, synchronization, snapRowCount,
                    snapColumnCount, visible, lineHexColor, lineThickness);
        }

    }

}
