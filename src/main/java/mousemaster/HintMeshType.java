package mousemaster;

import mousemaster.HintGridLayout.HintGridLayoutBuilder;
import mousemaster.ScreenFilterMap.ScreenFilterMapBuilder;

public sealed interface HintMeshType {

    record GridHintMesh(HintGridArea area, ScreenFilterMap<HintGridLayout> gridLayoutByFilter) implements HintMeshType {

        public HintGridLayout layout(ScreenFilter filter) {
            HintGridLayout layout = gridLayoutByFilter.get(filter);
            if (layout != null)
                return layout;
            return gridLayoutByFilter.get(
                    ScreenFilter.AnyScreenFilter.ANY_SCREEN_FILTER);
        }

    }

    record PositionHistoryHintMesh(String positionHistoryName) implements HintMeshType {

    }

    /** The area selects the windows the UI elements are looked for in. */
    record UiAccessibilityHintMesh(UiHintArea area) implements HintMeshType {

    }

    /** The area the elements are looked for in, as for {@link UiAccessibilityHintMesh}. */
    record UiVisionHintMesh(UiHintArea area) implements HintMeshType {

    }

    enum HintMeshTypeType {

        GRID, POSITION_HISTORY, UI_ACCESSIBILITY, UI_VISION

    }

    class HintMeshTypeBuilder {

        private HintMeshTypeType type;
        private HintGridArea.HintGridAreaBuilder
                gridArea = new HintGridArea.HintGridAreaBuilder();
        private UiHintArea uiArea;
        private String positionHistoryName;
        private final ScreenFilterMapBuilder<HintGridLayoutBuilder, HintGridLayout>
                gridLayoutByFilter;

        public HintMeshTypeBuilder() {
            this.gridLayoutByFilter = new ScreenFilterMapBuilder<>();
        }

        public HintMeshTypeBuilder(HintMeshType hintMeshType) {
            switch (hintMeshType) {
                case GridHintMesh gridHintMesh -> {
                    this.type = HintMeshTypeType.GRID;
                    this.gridArea = gridHintMesh.area.builder();
                    this.gridLayoutByFilter =
                            gridHintMesh.gridLayoutByFilter.builder(HintGridLayout::builder);
                }
                case PositionHistoryHintMesh positionHistoryHintMesh -> {
                    this.type = HintMeshTypeType.POSITION_HISTORY;
                    this.positionHistoryName = positionHistoryHintMesh.positionHistoryName;
                    this.gridLayoutByFilter = new ScreenFilterMapBuilder<>();
                }
                case UiAccessibilityHintMesh uiAccessibilityHintMesh -> {
                    this.type = HintMeshTypeType.UI_ACCESSIBILITY;
                    this.uiArea = uiAccessibilityHintMesh.area;
                    this.gridLayoutByFilter = new ScreenFilterMapBuilder<>();
                }
                case UiVisionHintMesh uiVisionHintMesh -> {
                    this.type = HintMeshTypeType.UI_VISION;
                    this.uiArea = uiVisionHintMesh.area;
                    this.gridLayoutByFilter = new ScreenFilterMapBuilder<>();
                }
            }
        }

        public HintMeshTypeType type() {
            return type;
        }

        public HintGridArea.HintGridAreaBuilder gridArea() {
            return gridArea;
        }

        public UiHintArea uiArea() {
            return uiArea;
        }

        public HintMeshTypeBuilder uiArea(UiHintArea uiArea) {
            this.uiArea = uiArea;
            return this;
        }

        public String positionHistoryName() {
            return positionHistoryName;
        }

        public HintMeshTypeBuilder positionHistoryName(String positionHistoryName) {
            this.positionHistoryName = positionHistoryName;
            return this;
        }

        public HintGridLayoutBuilder gridLayout(ScreenFilter filter) {
            return gridLayoutByFilter.map().computeIfAbsent(filter,
                    filter1 -> new HintGridLayoutBuilder());
        }

        public ScreenFilterMapBuilder<HintGridLayoutBuilder, HintGridLayout> gridLayoutByFilter() {
            return gridLayoutByFilter;
        }

        public HintMeshTypeBuilder type(HintMeshTypeType type) {
            this.type = type;
            return this;
        }

        public HintMeshType build() {
            return switch (type) {
                case GRID -> new GridHintMesh(gridArea.build(), gridLayoutByFilter.build(HintGridLayoutBuilder::build));
                case POSITION_HISTORY -> new PositionHistoryHintMesh(positionHistoryName);
                case UI_ACCESSIBILITY -> new UiAccessibilityHintMesh(uiArea);
                case UI_VISION -> new UiVisionHintMesh(uiArea);
            };
        }
    }

}
