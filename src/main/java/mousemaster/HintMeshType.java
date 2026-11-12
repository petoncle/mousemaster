package mousemaster;

import mousemaster.HintGridShape.HintGridShapeBuilder;
import mousemaster.ScreenFilterMap.ScreenFilterMapBuilder;

import java.util.function.Function;

public record HintMeshType(HintMeshTypeType type, Area area,
                           ScreenFilterMap<HintGridShape> gridShapeByFilter,
                           UiHintOrder order, LabelLength labelLength, int density,
                           double minSize, String positionHistoryName) {

    public HintGridShape shape(ScreenFilter filter) {
        HintGridShape shape = gridShapeByFilter.get(filter);
        if (shape != null)
            return shape;
        return gridShapeByFilter.get(
                ScreenFilter.AnyScreenFilter.ANY_SCREEN_FILTER);
    }

    public enum HintMeshTypeType {

        GRID, POSITION_HISTORY, UI_ACCESSIBILITY, UI_VISION

    }

    public record TypeChange(HintMeshTypeType type, String positionHistoryName)
            implements Function<HintMeshType, HintMeshType> {

        @Override
        public HintMeshType apply(HintMeshType hintMeshType) {
            return new HintMeshType(type, hintMeshType.area, hintMeshType.gridShapeByFilter,
                    hintMeshType.order, hintMeshType.labelLength, hintMeshType.density,
                    hintMeshType.minSize,
                    positionHistoryName == null ? hintMeshType.positionHistoryName :
                            positionHistoryName);
        }

    }

    public static class HintMeshTypeBuilder {

        private HintMeshTypeType type;
        private Area.AreaBuilder
                area = new Area.AreaBuilder();
        private UiHintOrder uiOrder;
        private LabelLength labelLength;
        private Integer visionDensity;
        private Double visionMinSize;
        private String positionHistoryName;
        private final ScreenFilterMapBuilder<HintGridShapeBuilder, HintGridShape>
                gridShapeByFilter;

        public HintMeshTypeBuilder() {
            this.gridShapeByFilter = new ScreenFilterMapBuilder<>();
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

        public LabelLength labelLength() {
            return labelLength;
        }

        public HintMeshTypeBuilder labelLength(LabelLength labelLength) {
            this.labelLength = labelLength;
            return this;
        }

        public String positionHistoryName() {
            return positionHistoryName;
        }

        public HintMeshTypeBuilder positionHistoryName(String positionHistoryName) {
            this.positionHistoryName = positionHistoryName;
            return this;
        }

        public HintGridShapeBuilder gridShape(ScreenFilter filter) {
            return gridShapeByFilter.map().computeIfAbsent(filter,
                    filter1 -> new HintGridShapeBuilder());
        }

        public ScreenFilterMapBuilder<HintGridShapeBuilder, HintGridShape> gridShapeByFilter() {
            return gridShapeByFilter;
        }

        public HintMeshTypeBuilder type(HintMeshTypeType type) {
            this.type = type;
            return this;
        }

        public HintMeshType build() {
            return new HintMeshType(type, area.build(),
                    gridShapeByFilter.build(HintGridShapeBuilder::build), uiOrder,
                    labelLength, visionDensity, visionMinSize, positionHistoryName);
        }
    }

}
