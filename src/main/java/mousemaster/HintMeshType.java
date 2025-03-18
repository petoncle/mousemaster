package mousemaster;

import mousemaster.HintGridLayout.HintGridLayoutBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public sealed interface HintMeshType {

    record HintGrid(HintGridArea area, Map<ViewportFilter, HintGridLayout> gridLayoutByFilter) implements HintMeshType {

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
        private Map<ViewportFilter, HintGridLayoutBuilder> gridLayoutByFilter = new HashMap<>();

        public HintMeshTypeBuilder() {

        }

        public HintMeshTypeBuilder(HintMeshType hintMeshType) {
            switch (hintMeshType) {
                case HintGrid hintGrid -> {
                    this.type = HintMeshTypeType.GRID;
                    this.gridArea = hintGrid.area.builder();
                    this.gridLayoutByFilter = hintGrid.gridLayoutByFilter
                            .entrySet()
                            .stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> entry.getValue().builder()
                            ));
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

        public HintGridLayoutBuilder gridLayout(ViewportFilter filter) {
            return gridLayoutByFilter.computeIfAbsent(filter,
                    filter1 -> new HintGridLayoutBuilder());
        }

        public Map<ViewportFilter, HintGridLayoutBuilder> gridLayoutByFilter() {
            return gridLayoutByFilter;
        }

        public HintMeshTypeBuilder type(HintMeshTypeType type) {
            this.type = type;
            return this;
        }

        public HintMeshType build() {
            return switch (type) {
                case GRID -> new HintGrid(gridArea.build(), buildGridLayoutByFilter());
                case POSITION_HISTORY -> new HintPositionHistory();
            };
        }

        public Map<ViewportFilter, HintGridLayout> buildGridLayoutByFilter() {
            // Assumes that the default layout is not missing any property.
            HintGridLayout defaultLayout = gridLayoutByFilter.get(
                    ViewportFilter.AnyViewportFilter.ANY_VIEWPORT_FILTER).build(null);
            return gridLayoutByFilter.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().build(defaultLayout)
            ));
        }
    }

}
