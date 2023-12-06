package jmouseable.jmouseable;

import jmouseable.jmouseable.HintGridArea.HintGridAreaBuilder;

public sealed interface HintMeshType {

    record HintGrid(HintGridArea area, int rowCount, int columnCount)
            implements HintMeshType {
    }

    record HintMousePositionHistory() implements HintMeshType {

    }

    enum HintMeshTypeType {

        GRID, MOUSE_POSITION_HISTORY

    }

    class HintMeshTypeBuilder {

        private HintMeshTypeType type;
        private HintGridAreaBuilder gridArea = new HintGridAreaBuilder();
        private Integer gridRowCount;
        private Integer gridColumnCount;

        public HintMeshTypeBuilder() {

        }

        public HintMeshTypeBuilder(HintMeshType hintMeshType) {
            switch (hintMeshType) {
                case HintGrid hintGrid -> {
                    this.type = HintMeshTypeType.GRID;
                    this.gridArea = hintGrid.area.builder();
                    this.gridRowCount = hintGrid.rowCount;
                    this.gridColumnCount = hintGrid.columnCount;
                }
                case HintMousePositionHistory hintMousePositionHistory -> {
                    this.type = HintMeshTypeType.MOUSE_POSITION_HISTORY;
                }
            }
        }

        public HintMeshTypeType type() {
            return type;
        }

        public HintGridAreaBuilder gridArea() {
            return gridArea;
        }

        public Integer gridRowCount() {
            return gridRowCount;
        }

        public Integer gridColumnCount() {
            return gridColumnCount;
        }

        public HintMeshTypeBuilder type(HintMeshTypeType type) {
            this.type = type;
            return this;
        }

        public HintMeshTypeBuilder gridRowCount(Integer gridRowCount) {
            this.gridRowCount = gridRowCount;
            return this;
        }

        public HintMeshTypeBuilder gridColumnCount(Integer gridColumnCount) {
            this.gridColumnCount = gridColumnCount;
            return this;
        }

        public HintMeshType build() {
            return switch (type) {
                case GRID ->
                        new HintGrid(gridArea.build(), gridRowCount, gridColumnCount);
                case MOUSE_POSITION_HISTORY -> new HintMousePositionHistory();
            };
        }
    }

}
