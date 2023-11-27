package jmouseable.jmouseable;

public record GridConfiguration(GridType type,
                                boolean autoMoveToGridCenter, int snapRowCount,
                                int snapColumnCount, boolean visible, String lineHexColor,
                                int lineThickness) {

    public enum GridType {

        FULL_SCREEN, ACTIVE_WINDOW, AROUND_CURSOR

    }

    public static class GridConfigurationBuilder {
        private GridType type;
        private Boolean autoMoveToGridCenter;
        private Integer snapRowCount;
        private Integer snapColumnCount;
        private Boolean visible;
        private String lineHexColor;
        private Integer lineThickness;

        public GridConfigurationBuilder type(GridType type) {
            this.type = type;
            return this;
        }

        public GridConfigurationBuilder autoMoveToGridCenter(boolean autoMoveToGridCenter) {
            this.autoMoveToGridCenter = autoMoveToGridCenter;
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

        public GridType type() {
            return type;
        }

        public Boolean autoMoveToGridCenter() {
            return autoMoveToGridCenter;
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
            return new GridConfiguration(type, autoMoveToGridCenter, snapRowCount,
                    snapColumnCount, visible, lineHexColor, lineThickness);
        }

    }

}
