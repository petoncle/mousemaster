package mousemaster;

import mousemaster.HintGridLayout.HintGridLayoutBuilder;
import mousemaster.ViewportFilterMap.ViewportFilterMapBuilder;

public sealed interface HintMeshType {

    record HintGrid(HintGridArea area, ViewportFilterMap<HintGridLayout> gridLayoutByFilter) implements HintMeshType {

        public HintGridLayout layout(ViewportFilter filter) {
            HintGridLayout layout = gridLayoutByFilter.get(filter);
            if (layout != null)
                return layout;
            return gridLayoutByFilter.get(
                    ViewportFilter.AnyViewportFilter.ANY_VIEWPORT_FILTER);
        }

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
        private final ViewportFilterMapBuilder<HintGridLayoutBuilder, HintGridLayout>
                gridLayoutByFilter;

        public HintMeshTypeBuilder() {
            this.gridLayoutByFilter = new ViewportFilterMapBuilder<>();
        }

        public HintMeshTypeBuilder(HintMeshType hintMeshType) {
            switch (hintMeshType) {
                case HintGrid hintGrid -> {
                    this.type = HintMeshTypeType.GRID;
                    this.gridArea = hintGrid.area.builder();
                    this.gridLayoutByFilter =
                            hintGrid.gridLayoutByFilter.builder(HintGridLayout::builder);
                }
                case HintPositionHistory hintPositionHistory -> {
                    this.type = HintMeshTypeType.POSITION_HISTORY;
                    this.gridLayoutByFilter = new ViewportFilterMapBuilder<>();
                }
            }
        }

        public HintMeshTypeType type() {
            return type;
        }

        public HintGridArea.HintGridAreaBuilder gridArea() {
            return gridArea;
        }

        public HintGridLayoutBuilder gridLayout(ViewportFilter filter) {
            return gridLayoutByFilter.map().computeIfAbsent(filter,
                    filter1 -> new HintGridLayoutBuilder());
        }

        public ViewportFilterMapBuilder<HintGridLayoutBuilder, HintGridLayout> gridLayoutByFilter() {
            return gridLayoutByFilter;
        }

        public HintMeshTypeBuilder type(HintMeshTypeType type) {
            this.type = type;
            return this;
        }

        public HintMeshType build() {
            return switch (type) {
                case GRID -> new HintGrid(gridArea.build(), gridLayoutByFilter.build(HintGridLayoutBuilder::build));
                case POSITION_HISTORY -> new HintPositionHistory();
            };
        }
    }

}
