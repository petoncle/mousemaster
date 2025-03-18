package mousemaster;

public record HintGridLayout(int maxRowCount, int maxColumnCount, double cellWidth,
                             double cellHeight,
                             int layoutRowCount, int layoutColumnCount,
                             boolean layoutRowOriented) {

    public HintGridLayoutBuilder builder() {
        return new HintGridLayoutBuilder(this);
    }

    public static class HintGridLayoutBuilder {

        private Integer maxRowCount;
        private Integer maxColumnCount;
        private Double cellWidth;
        private Double cellHeight;
        private Integer layoutRowCount;
        private Integer layoutColumnCount;
        private Boolean layoutRowOriented;

        public HintGridLayoutBuilder() {

        }

        public HintGridLayoutBuilder(HintGridLayout layout) {
            this.cellWidth = layout.cellWidth;
            this.cellHeight = layout.cellHeight;
            this.layoutRowCount = layout.layoutRowCount;
            this.layoutColumnCount = layout.layoutColumnCount;
            this.layoutRowOriented = layout.layoutRowOriented;
        }

        public Integer maxRowCount() {
            return maxRowCount;
        }

        public Integer maxColumnCount() {
            return maxColumnCount;
        }

        public Double cellWidth() {
            return cellWidth;
        }

        public Double cellHeight() {
            return cellHeight;
        }

        public Integer layoutRowCount() {
            return layoutRowCount;
        }

        public Integer layoutColumnCount() {
            return layoutColumnCount;
        }

        public Boolean layoutRowOriented() {
            return layoutRowOriented;
        }

        public HintGridLayoutBuilder maxRowCount(Integer maxRowCount) {
            this.maxRowCount = maxRowCount;
            return this;
        }

        public HintGridLayoutBuilder maxColumnCount(Integer maxColumnCount) {
            this.maxColumnCount = maxColumnCount;
            return this;
        }

        public HintGridLayoutBuilder cellWidth(Double cellWidth) {
            this.cellWidth = cellWidth;
            return this;
        }

        public HintGridLayoutBuilder cellHeight(Double cellHeight) {
            this.cellHeight = cellHeight;
            return this;
        }

        public HintGridLayoutBuilder layoutRowCount(Integer layoutRowCount) {
            this.layoutRowCount = layoutRowCount;
            return this;
        }

        public HintGridLayoutBuilder layoutColumnCount(Integer layoutColumnCount) {
            this.layoutColumnCount = layoutColumnCount;
            return this;
        }

        public HintGridLayoutBuilder layoutRowOriented(Boolean layoutRowOriented) {
            this.layoutRowOriented = layoutRowOriented;
            return this;
        }

        public HintGridLayout build(HintGridLayout defaultLayout) {
            return new HintGridLayout(
                    maxRowCount == null ? defaultLayout.maxRowCount : maxRowCount,
                    maxColumnCount == null ? defaultLayout.maxColumnCount : maxColumnCount,
                    cellWidth == null ? defaultLayout.cellWidth : cellWidth,
                    cellHeight == null ? defaultLayout.cellHeight : cellHeight,
                    layoutRowCount == null ? defaultLayout.layoutRowCount : layoutRowCount,
                    layoutColumnCount == null ? defaultLayout.layoutColumnCount : layoutColumnCount,
                    layoutRowOriented == null ? defaultLayout.layoutRowOriented : layoutRowOriented
            );
        }
    }

}
