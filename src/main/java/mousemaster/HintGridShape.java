package mousemaster;

import mousemaster.HintCellSizing.HintCellSizingType;

public record HintGridShape(int maxRowCount, int maxColumnCount,
                            HintCellSizing cellSizing,
                            int groupRowCount, int groupColumnCount,
                            HintGridOrder order) {

    public static class HintGridShapeBuilder {

        private Integer maxRowCount;
        private Integer maxColumnCount;
        private HintCellSizingType cellSizingType;
        private Double cellWidth;
        private Double cellHeight;
        private Integer groupRowCount;
        private Integer groupColumnCount;
        private HintGridOrder order;

        public HintGridShapeBuilder() {

        }

        public Integer maxRowCount() {
            return maxRowCount;
        }

        public Integer maxColumnCount() {
            return maxColumnCount;
        }

        public HintCellSizingType cellSizingType() {
            return cellSizingType;
        }

        public Double cellWidth() {
            return cellWidth;
        }

        public Double cellHeight() {
            return cellHeight;
        }

        public Integer groupRowCount() {
            return groupRowCount;
        }

        public Integer groupColumnCount() {
            return groupColumnCount;
        }

        public HintGridOrder order() {
            return order;
        }

        public HintGridShapeBuilder maxRowCount(Integer maxRowCount) {
            this.maxRowCount = maxRowCount;
            return this;
        }

        public HintGridShapeBuilder maxColumnCount(Integer maxColumnCount) {
            this.maxColumnCount = maxColumnCount;
            return this;
        }

        public HintGridShapeBuilder cellSizingType(HintCellSizingType cellSizingType) {
            this.cellSizingType = cellSizingType;
            return this;
        }

        public HintGridShapeBuilder cellWidth(Double cellWidth) {
            this.cellWidth = cellWidth;
            return this;
        }

        public HintGridShapeBuilder cellHeight(Double cellHeight) {
            this.cellHeight = cellHeight;
            return this;
        }

        public HintGridShapeBuilder groupRowCount(Integer groupRowCount) {
            this.groupRowCount = groupRowCount;
            return this;
        }

        public HintGridShapeBuilder groupColumnCount(Integer groupColumnCount) {
            this.groupColumnCount = groupColumnCount;
            return this;
        }

        public HintGridShapeBuilder order(HintGridOrder order) {
            this.order = order;
            return this;
        }

        public HintGridShape build(HintGridShape defaultShape) {
            return new HintGridShape(
                    maxRowCount == null ? defaultShape.maxRowCount : maxRowCount,
                    maxColumnCount == null ? defaultShape.maxColumnCount : maxColumnCount,
                    new HintCellSizing(
                            cellSizingType == null ? defaultShape.cellSizing.type() :
                                    cellSizingType,
                            cellWidth == null ? defaultShape.cellSizing.cellWidth() : cellWidth,
                            cellHeight == null ? defaultShape.cellSizing.cellHeight() :
                                    cellHeight),
                    groupRowCount == null ? defaultShape.groupRowCount : groupRowCount,
                    groupColumnCount == null ? defaultShape.groupColumnCount : groupColumnCount,
                    order == null ? defaultShape.order : order
            );
        }
    }

}
