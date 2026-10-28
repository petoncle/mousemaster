package mousemaster;

public record HintGridArea(HintGridAreaSize size, HintGridAreaCenter center) {

    public HintGridAreaBuilder builder() {
        return new HintGridAreaBuilder(this);
    }

    public static class HintGridAreaBuilder {
        private HintGridAreaSizeSource source;
        private Double widthPercent;
        private Double heightPercent;
        private Integer topInset;
        private Integer bottomInset;
        private Integer leftInset;
        private Integer rightInset;
        private HintGridAreaCenter center;

        public HintGridAreaBuilder() {

        }

        public HintGridAreaBuilder(HintGridArea gridArea) {
            this.source = gridArea.size.source();
            this.widthPercent = gridArea.size.widthPercent();
            this.heightPercent = gridArea.size.heightPercent();
            this.topInset = gridArea.size.topInset();
            this.bottomInset = gridArea.size.bottomInset();
            this.leftInset = gridArea.size.leftInset();
            this.rightInset = gridArea.size.rightInset();
            this.center = gridArea.center;
        }

        public HintGridAreaBuilder source(HintGridAreaSizeSource source) {
            this.source = source;
            return this;
        }

        public HintGridAreaBuilder widthPercent(Double widthPercent) {
            this.widthPercent = widthPercent;
            return this;
        }

        public HintGridAreaBuilder heightPercent(Double heightPercent) {
            this.heightPercent = heightPercent;
            return this;
        }

        public HintGridAreaBuilder topInset(Integer topInset) {
            this.topInset = topInset;
            return this;
        }

        public HintGridAreaBuilder bottomInset(Integer bottomInset) {
            this.bottomInset = bottomInset;
            return this;
        }

        public HintGridAreaBuilder leftInset(Integer leftInset) {
            this.leftInset = leftInset;
            return this;
        }

        public HintGridAreaBuilder rightInset(Integer rightInset) {
            this.rightInset = rightInset;
            return this;
        }

        public HintGridAreaBuilder center(HintGridAreaCenter center) {
            this.center = center;
            return this;
        }

        public HintGridAreaSizeSource source() {
            return source;
        }

        public Double widthPercent() {
            return widthPercent;
        }

        public Double heightPercent() {
            return heightPercent;
        }

        public Integer topInset() {
            return topInset;
        }

        public Integer bottomInset() {
            return bottomInset;
        }

        public Integer leftInset() {
            return leftInset;
        }

        public Integer rightInset() {
            return rightInset;
        }

        public HintGridAreaCenter center() {
            return center;
        }

        public HintGridArea build() {
            HintGridAreaSize size = new HintGridAreaSize(source, widthPercent, heightPercent,
                    topInset, bottomInset, leftInset, rightInset);
            return new HintGridArea(size,
                    center != null ? center : defaultCenter(source));
        }

        private static HintGridAreaCenter defaultCenter(HintGridAreaSizeSource source) {
            return switch (source) {
                case ACTIVE_SCREEN, ALL_SCREENS -> HintGridAreaCenter.SCREEN_CENTER;
                case ACTIVE_WINDOW -> HintGridAreaCenter.ACTIVE_WINDOW_CENTER;
                case LAST_SELECTED_HINT_CELL -> HintGridAreaCenter.LAST_SELECTED_HINT;
                case LAST_OVERLAY_AREA -> HintGridAreaCenter.LAST_OVERLAY_AREA_CENTER;
            };
        }
    }
}
