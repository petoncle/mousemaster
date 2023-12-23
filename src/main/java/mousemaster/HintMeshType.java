package mousemaster;

public sealed interface HintMeshType {

    record HintGrid(HintGridArea area, int maxRowCount, int maxColumnCount, int cellWidth,
                    int cellHeight) implements HintMeshType {
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
        private Integer gridCellWidth;
        private Integer gridCellHeight;

        public HintMeshTypeBuilder() {

        }

        public HintMeshTypeBuilder(HintMeshType hintMeshType) {
            switch (hintMeshType) {
                case HintGrid hintGrid -> {
                    this.type = HintMeshTypeType.GRID;
                    this.gridArea = hintGrid.area.builder();
                    this.gridCellWidth = hintGrid.cellWidth;
                    this.gridCellHeight = hintGrid.cellHeight;
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

        public Integer gridCellWidth() {
            return gridCellWidth;
        }

        public Integer gridCellHeight() {
            return gridCellHeight;
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

        public HintMeshTypeBuilder gridCellWidth(Integer gridCellWidth) {
            this.gridCellWidth = gridCellWidth;
            return this;
        }

        public HintMeshTypeBuilder gridCellHeight(Integer gridCellHeight) {
            this.gridCellHeight = gridCellHeight;
            return this;
        }

        public HintMeshType build() {
            return switch (type) {
                case GRID -> new HintGrid(gridArea.build(), gridMaxRowCount,
                        gridMaxColumnCount, gridCellWidth, gridCellHeight);
                case POSITION_HISTORY -> new HintPositionHistory();
            };
        }
    }

}
