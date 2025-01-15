package mousemaster;

public sealed interface HintMeshType {

    record HintGrid(HintGridArea area, int maxRowCount, int maxColumnCount, double cellWidth,
                    double cellHeight,
                    int subgridRowCount, int subgridColumnCount, boolean rowOriented) implements HintMeshType {
    }

    record HintPositionHistory() implements HintMeshType {

    }

    enum HintMeshTypeType {

        GRID, POSITION_HISTORY

    }

    class HintMeshTypeBuilder {

        private HintMeshTypeType type;
        private HintGridArea.HintGridAreaBuilder
                gridArea = new HintGridArea.HintGridAreaBuilder();
        private Integer gridMaxRowCount;
        private Integer gridMaxColumnCount;
        private Double gridCellWidth;
        private Double gridCellHeight;
        private Integer subgridRowCount;
        private Integer subgridColumnCount;
        private Boolean rowOriented;

        public HintMeshTypeBuilder() {

        }

        public HintMeshTypeBuilder(HintMeshType hintMeshType) {
            switch (hintMeshType) {
                case HintGrid hintGrid -> {
                    this.type = HintMeshTypeType.GRID;
                    this.gridArea = hintGrid.area.builder();
                    this.gridCellWidth = hintGrid.cellWidth;
                    this.gridCellHeight = hintGrid.cellHeight;
                    this.subgridRowCount = hintGrid.subgridRowCount;
                    this.subgridColumnCount = hintGrid.subgridColumnCount;
                    this.rowOriented = hintGrid.rowOriented;
                }
                case HintPositionHistory hintPositionHistory -> {
                    this.type = HintMeshTypeType.POSITION_HISTORY;
                }
            }
        }

        public HintMeshTypeType type() {
            return type;
        }

        public HintGridArea.HintGridAreaBuilder gridArea() {
            return gridArea;
        }

        public Integer gridMaxRowCount() {
            return gridMaxRowCount;
        }

        public Integer gridMaxColumnCount() {
            return gridMaxColumnCount;
        }

        public Double gridCellWidth() {
            return gridCellWidth;
        }

        public Double gridCellHeight() {
            return gridCellHeight;
        }

        public Integer subgridRowCount() {
            return subgridRowCount;
        }

        public Integer subgridColumnCount() {
            return subgridColumnCount;
        }

        public Boolean rowOriented() {
            return rowOriented;
        }

        public HintMeshTypeBuilder type(HintMeshTypeType type) {
            this.type = type;
            return this;
        }

        public HintMeshTypeBuilder gridMaxRowCount(Integer gridMaxRowCount) {
            this.gridMaxRowCount = gridMaxRowCount;
            return this;
        }

        public HintMeshTypeBuilder gridMaxColumnCount(Integer gridMaxColumnCount) {
            this.gridMaxColumnCount = gridMaxColumnCount;
            return this;
        }

        public HintMeshTypeBuilder gridCellWidth(Double gridCellWidth) {
            this.gridCellWidth = gridCellWidth;
            return this;
        }

        public HintMeshTypeBuilder gridCellHeight(Double gridCellHeight) {
            this.gridCellHeight = gridCellHeight;
            return this;
        }

        public HintMeshTypeBuilder subgridRowCount(Integer subgridRowCount) {
            this.subgridRowCount = subgridRowCount;
            return this;
        }

        public HintMeshTypeBuilder subgridColumnCount(Integer subgridColumnCount) {
            this.subgridColumnCount = subgridColumnCount;
            return this;
        }

        public HintMeshTypeBuilder rowOriented(Boolean rowOriented) {
            this.rowOriented = rowOriented;
            return this;
        }

        public HintMeshType build() {
            return switch (type) {
                case GRID -> new HintGrid(gridArea.build(), gridMaxRowCount,
                        gridMaxColumnCount, gridCellWidth, gridCellHeight,
                        subgridRowCount, subgridColumnCount, rowOriented);
                case POSITION_HISTORY -> new HintPositionHistory();
            };
        }
    }

}
