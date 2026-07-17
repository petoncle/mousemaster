package mousemaster;

import mousemaster.HintCellSizing.HintCellSizingType;

public record HintGridLayout(int maxRowCount, int maxColumnCount,
                             HintCellSizing cellSizing,
                             int layoutRowCount, int layoutColumnCount,
                             boolean layoutRowOriented) {

    public HintGridLayoutBuilder builder() {
        return new HintGridLayoutBuilder(this);
    }

    public static class HintGridLayoutBuilder {

        private Integer maxRowCount;
        private Integer maxColumnCount;
        private HintCellSizingType cellSizingType;
        private Double cellWidth;
        private Double cellHeight;
        private Integer layoutRowCount;
        private Integer layoutColumnCount;
        private Boolean layoutRowOriented;

        public HintGridLayoutBuilder() {

        }

        public HintGridLayoutBuilder(HintGridLayout layout) {
            switch (layout.cellSizing) {
                case HintCellSizing.FixedCellSize fixedCellSize -> {
                    this.cellSizingType = HintCellSizingType.FIXED;
                    this.cellWidth = fixedCellSize.cellWidth();
                    this.cellHeight = fixedCellSize.cellHeight();
                }
                case HintCellSizing.FitToArea fitToArea ->
                        this.cellSizingType = HintCellSizingType.FIT;
            }
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

        public HintCellSizingType cellSizingType() {
            return cellSizingType;
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

        public HintGridLayoutBuilder cellSizingType(HintCellSizingType cellSizingType) {
            this.cellSizingType = cellSizingType;
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
            HintCellSizing defaultCellSizing =
                    defaultLayout == null ? null : defaultLayout.cellSizing;
            HintCellSizingType resolvedCellSizingType = cellSizingType != null ?
                    cellSizingType :
                    defaultCellSizing instanceof HintCellSizing.FitToArea ?
                            HintCellSizingType.FIT : HintCellSizingType.FIXED;
            HintCellSizing cellSizing = switch (resolvedCellSizingType) {
                case FIXED -> {
                    HintCellSizing.FixedCellSize defaultFixed =
                            defaultCellSizing instanceof HintCellSizing.FixedCellSize fixed ?
                                    fixed : null;
                    yield new HintCellSizing.FixedCellSize(
                            cellWidth != null ? cellWidth :
                                    defaultFixed != null ? defaultFixed.cellWidth() : 0,
                            cellHeight != null ? cellHeight :
                                    defaultFixed != null ? defaultFixed.cellHeight() : 0);
                }
                case FIT -> new HintCellSizing.FitToArea();
            };
            return new HintGridLayout(
                    maxRowCount == null ? defaultLayout.maxRowCount : maxRowCount,
                    maxColumnCount == null ? defaultLayout.maxColumnCount : maxColumnCount,
                    cellSizing,
                    layoutRowCount == null ? defaultLayout.layoutRowCount : layoutRowCount,
                    layoutColumnCount == null ? defaultLayout.layoutColumnCount : layoutColumnCount,
                    layoutRowOriented == null ? defaultLayout.layoutRowOriented : layoutRowOriented
            );
        }
    }

}
