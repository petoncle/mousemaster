package mousemaster;

public record HintGridArea(HintGridAreaSize size, HintGridAreaCenter center) {

    public HintGridAreaBuilder builder() {
        return new HintGridAreaBuilder(this);
    }

    public static class HintGridAreaBuilder {
        private HintGridAreaSizeSource source;
        private Double widthPercent;
        private Double heightPercent;
        private HintGridAreaCenter center;

        public HintGridAreaBuilder() {

        }

        public HintGridAreaBuilder(HintGridArea gridArea) {
            this.source = gridArea.size.source();
            this.widthPercent = gridArea.size.widthPercent();
            this.heightPercent = gridArea.size.heightPercent();
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

        public HintGridAreaCenter center() {
            return center;
        }

        public HintGridArea build() {
            HintGridAreaSize size =
                    new HintGridAreaSize(source, widthPercent, heightPercent);
            return new HintGridArea(size,
                    center != null ? center : defaultCenter(source));
        }

        private static HintGridAreaCenter defaultCenter(HintGridAreaSizeSource source) {
            return switch (source) {
                case ACTIVE_SCREEN, ALL_SCREENS -> HintGridAreaCenter.SCREEN_CENTER;
                case ACTIVE_WINDOW -> HintGridAreaCenter.ACTIVE_WINDOW_CENTER;
                case LAST_SELECTED_HINT_CELL -> HintGridAreaCenter.LAST_SELECTED_HINT;
            };
        }
    }
}
