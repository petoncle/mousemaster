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
    record UiAccessibilityHintMesh(HintGridArea area, UiHintOrder order) implements HintMeshType {

    }

    /** The area the elements are looked for in, as for {@link UiAccessibilityHintMesh}. */
    record UiVisionHintMesh(HintGridArea area, UiHintOrder order, int density, double minSize) implements HintMeshType {

    }

    enum HintMeshTypeType {

        GRID, POSITION_HISTORY, UI_ACCESSIBILITY, UI_VISION

    }

    class HintMeshTypeBuilder {

        private HintMeshTypeType type;
        private HintGridArea.HintGridAreaBuilder
                area = new HintGridArea.HintGridAreaBuilder();
        private UiHintOrder uiOrder;
        private Integer visionDensity;
        private Double visionMinSize;
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
                    this.area = gridHintMesh.area.builder();
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
                    this.area = uiAccessibilityHintMesh.area.builder();
                    this.uiOrder = uiAccessibilityHintMesh.order;
                    this.gridLayoutByFilter = new ScreenFilterMapBuilder<>();
                }
                case UiVisionHintMesh uiVisionHintMesh -> {
                    this.type = HintMeshTypeType.UI_VISION;
                    this.area = uiVisionHintMesh.area.builder();
                    this.uiOrder = uiVisionHintMesh.order;
                    this.visionDensity = uiVisionHintMesh.density;
                    this.visionMinSize = uiVisionHintMesh.minSize;
                    this.gridLayoutByFilter = new ScreenFilterMapBuilder<>();
                }
            }
        }

        public HintMeshTypeType type() {
            return type;
        }

        public HintGridArea.HintGridAreaBuilder area() {
            return area;
        }

        public Integer visionDensity() {
            return visionDensity;
        }

        public HintMeshTypeBuilder visionDensity(Integer visionDensity) {
            this.visionDensity = visionDensity;
            return this;
        }

        public Double visionMinSize() {
            return visionMinSize;
        }

        public HintMeshTypeBuilder visionMinSize(Double visionMinSize) {
            this.visionMinSize = visionMinSize;
            return this;
        }

        public UiHintOrder uiOrder() {
            return uiOrder;
        }

        public HintMeshTypeBuilder uiOrder(UiHintOrder uiOrder) {
            this.uiOrder = uiOrder;
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
                case GRID -> new GridHintMesh(area.build(), gridLayoutByFilter.build(HintGridLayoutBuilder::build));
                case POSITION_HISTORY -> new PositionHistoryHintMesh(positionHistoryName);
                case UI_ACCESSIBILITY -> new UiAccessibilityHintMesh(area.build(), uiOrder);
                case UI_VISION -> new UiVisionHintMesh(area.build(), uiOrder, visionDensity, visionMinSize);
            };
        }
    }

}
