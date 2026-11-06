package mousemaster;

import mousemaster.HintGridLayout.HintGridLayoutBuilder;
import mousemaster.ScreenFilterMap.ScreenFilterMapBuilder;

import java.util.function.Function;

public record HintMeshType(HintMeshTypeType type, Area area,
                           ScreenFilterMap<HintGridLayout> gridLayoutByFilter,
                           UiHintOrder order, int density, double minSize,
                           String positionHistoryName) {

    public HintGridLayout layout(ScreenFilter filter) {
        HintGridLayout layout = gridLayoutByFilter.get(filter);
        if (layout != null)
            return layout;
        return gridLayoutByFilter.get(
                ScreenFilter.AnyScreenFilter.ANY_SCREEN_FILTER);
    }

    public enum HintMeshTypeType {

        GRID, POSITION_HISTORY, UI_ACCESSIBILITY, UI_VISION

    }

    public record TypeChange(HintMeshTypeType type, String positionHistoryName)
            implements Function<HintMeshType, HintMeshType> {

        @Override
        public HintMeshType apply(HintMeshType hintMeshType) {
            return new HintMeshType(type, hintMeshType.area, hintMeshType.gridLayoutByFilter,
                    hintMeshType.order, hintMeshType.density, hintMeshType.minSize,
                    positionHistoryName == null ? hintMeshType.positionHistoryName :
                            positionHistoryName);
        }

    }

    public static class HintMeshTypeBuilder {

        private HintMeshTypeType type;
        private Area.AreaBuilder
                area = new Area.AreaBuilder();
        private UiHintOrder uiOrder;
        private Integer visionDensity;
        private Double visionMinSize;
        private String positionHistoryName;
        private final ScreenFilterMapBuilder<HintGridLayoutBuilder, HintGridLayout>
                gridLayoutByFilter;

        public HintMeshTypeBuilder() {
            this.gridLayoutByFilter = new ScreenFilterMapBuilder<>();
        }

        public HintMeshTypeType type() {
            return type;
        }

        public Area.AreaBuilder area() {
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
            return new HintMeshType(type, area.build(),
                    gridLayoutByFilter.build(HintGridLayoutBuilder::build), uiOrder,
                    visionDensity, visionMinSize, positionHistoryName);
        }
    }

}
