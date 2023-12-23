package mousemaster;

public sealed interface HintGridArea {

    record ActiveScreenHintGridArea(ActiveScreenHintGridAreaCenter center)
            implements HintGridArea {
    }

    record ActiveWindowHintGridArea()
            implements HintGridArea {

    }

    record AllScreensHintGridArea()
            implements HintGridArea {
    }

    default HintGridAreaBuilder builder() {
        return new HintGridAreaBuilder(this);
    }

    enum HintGridAreaType {
        ACTIVE_SCREEN, ACTIVE_WINDOW, ALL_SCREENS
    }

    class HintGridAreaBuilder {
        private HintGridAreaType type;
        private ActiveScreenHintGridAreaCenter activeScreenHintGridAreaCenter;

        public HintGridAreaBuilder() {

        }

        public HintGridAreaBuilder(HintGridArea gridArea) {
            switch (gridArea) {
                case ActiveScreenHintGridArea activeScreenHintGridArea -> {
                    this.type = HintGridAreaType.ACTIVE_SCREEN;
                    this.activeScreenHintGridAreaCenter = activeScreenHintGridArea.center;
                }
                case ActiveWindowHintGridArea activeWindowHintGridArea -> {
                    this.type = HintGridAreaType.ACTIVE_WINDOW;
                }
                case AllScreensHintGridArea allScreensHintGridArea -> {
                    this.type = HintGridAreaType.ALL_SCREENS;
                }
            }
        }

        public HintGridAreaBuilder type(HintGridAreaType type) {
            this.type = type;
            return this;
        }

        public HintGridAreaBuilder activeScreenHintGridAreaCenter(
                ActiveScreenHintGridAreaCenter activeScreenHintGridAreaCenter) {
            this.activeScreenHintGridAreaCenter = activeScreenHintGridAreaCenter;
            return this;
        }

        public HintGridAreaType type() {
            return type;
        }

        public ActiveScreenHintGridAreaCenter activeScreenHintGridAreaCenter() {
            return activeScreenHintGridAreaCenter;
        }

        public HintGridArea build() {
            return switch (type) {
                case ACTIVE_SCREEN ->
                        new ActiveScreenHintGridArea(activeScreenHintGridAreaCenter);
                case ACTIVE_WINDOW ->
                        new ActiveWindowHintGridArea();
                case ALL_SCREENS ->
                        new AllScreensHintGridArea();
            };
        }
    }
}
