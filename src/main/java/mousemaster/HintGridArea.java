package mousemaster;

public record HintGridArea(HintGridAreaSize size, HintGridAreaCenter center) {

    public HintGridAreaBuilder builder() {
        return new HintGridAreaBuilder(this);
    }

    public static class HintGridAreaBuilder {
        private HintGridAreaSize size;
        private HintGridAreaCenter center;

        public HintGridAreaBuilder() {

        }

        public HintGridAreaBuilder(HintGridArea gridArea) {
            this.size = gridArea.size;
            this.center = gridArea.center;
        }

        public HintGridAreaBuilder size(HintGridAreaSize size) {
            this.size = size;
            return this;
        }

        public HintGridAreaBuilder center(HintGridAreaCenter center) {
            this.center = center;
            return this;
        }

        public HintGridAreaSize size() {
            return size;
        }

        public HintGridAreaCenter center() {
            return center;
        }

        public HintGridArea build() {
            return new HintGridArea(size,
                    center != null ? center : defaultCenter(size));
        }

        private static HintGridAreaCenter defaultCenter(HintGridAreaSize size) {
            return switch (size) {
                case ACTIVE_SCREEN, ALL_SCREENS -> HintGridAreaCenter.SCREEN_CENTER;
                case ACTIVE_WINDOW -> HintGridAreaCenter.ACTIVE_WINDOW_CENTER;
                case LAST_SELECTED_HINT_CELL -> HintGridAreaCenter.LAST_SELECTED_HINT;
            };
        }
    }
}
