package mousemaster;

public record HintGridArea(HintGridAreaSize size, HintGridAreaCenter center) {

    public HintGridAreaBuilder builder() {
        return new HintGridAreaBuilder(this);
    }

    public static class HintGridAreaBuilder {
        private HintGridAreaSizeSource source;
        private Double widthPercent;
        private Double heightPercent;
        private Double topInset;
        private Double bottomInset;
        private Double leftInset;
        private Double rightInset;
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

        public HintGridAreaBuilder topInset(Double topInset) {
            this.topInset = topInset;
            return this;
        }

        public HintGridAreaBuilder bottomInset(Double bottomInset) {
            this.bottomInset = bottomInset;
            return this;
        }

        public HintGridAreaBuilder leftInset(Double leftInset) {
            this.leftInset = leftInset;
            return this;
        }

        public HintGridAreaBuilder rightInset(Double rightInset) {
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

        public Double topInset() {
            return topInset;
        }

        public Double bottomInset() {
            return bottomInset;
        }

        public Double leftInset() {
            return leftInset;
        }

        public Double rightInset() {
            return rightInset;
        }

        public HintGridAreaCenter center() {
            return center;
        }

        public void extend(HintGridAreaBuilder parent) {
            if (source == null) source = parent.source;
            if (widthPercent == null) widthPercent = parent.widthPercent;
            if (heightPercent == null) heightPercent = parent.heightPercent;
            if (topInset == null) topInset = parent.topInset;
            if (bottomInset == null) bottomInset = parent.bottomInset;
            if (leftInset == null) leftInset = parent.leftInset;
            if (rightInset == null) rightInset = parent.rightInset;
            if (center == null) center = parent.center;
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
