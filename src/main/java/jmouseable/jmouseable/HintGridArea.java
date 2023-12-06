package jmouseable.jmouseable;

public sealed interface HintGridArea {

    double widthPercent();

    double heightPercent();

    record ActiveScreenHintGridArea(double widthPercent, double heightPercent,
                                    ActiveScreenHintGridAreaCenter center)
            implements HintGridArea {
    }

    record ActiveWindowHintGridArea(double widthPercent, double heightPercent)
            implements HintGridArea {

    }

    record AllScreensHintGridArea(double widthPercent, double heightPercent)
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
        private Double widthPercent;
        private Double heightPercent;
        private ActiveScreenHintGridAreaCenter activeScreenHintGridAreaCenter;

        public HintGridAreaBuilder() {

        }

        public HintGridAreaBuilder(HintGridArea gridArea) {
            switch (gridArea) {
                case ActiveScreenHintGridArea activeScreenHintGridArea -> {
                    this.type = HintGridAreaType.ACTIVE_SCREEN;
                    this.widthPercent = activeScreenHintGridArea.widthPercent;
                    this.heightPercent = activeScreenHintGridArea.heightPercent;
                    this.activeScreenHintGridAreaCenter = activeScreenHintGridArea.center;
                }
                case ActiveWindowHintGridArea activeWindowHintGridArea -> {
                    this.type = HintGridAreaType.ACTIVE_WINDOW;
                    this.widthPercent = activeWindowHintGridArea.widthPercent;
                    this.heightPercent = activeWindowHintGridArea.heightPercent;
                }
                case AllScreensHintGridArea allScreensHintGridArea -> {
                    this.type = HintGridAreaType.ALL_SCREENS;
                    this.widthPercent = allScreensHintGridArea.widthPercent;
                    this.heightPercent = allScreensHintGridArea.heightPercent;
                }
            }
        }

        public HintGridAreaBuilder type(HintGridAreaType type) {
            this.type = type;
            return this;
        }

        public HintGridAreaBuilder widthPercent(double widthPercent) {
            this.widthPercent = widthPercent;
            return this;
        }

        public HintGridAreaBuilder heightPercent(double heightPercent) {
            this.heightPercent = heightPercent;
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

        public Double widthPercent() {
            return widthPercent;
        }

        public Double heightPercent() {
            return heightPercent;
        }

        public ActiveScreenHintGridAreaCenter activeScreenHintGridAreaCenter() {
            return activeScreenHintGridAreaCenter;
        }

        public HintGridArea build() {
            return switch (type) {
                case ACTIVE_SCREEN ->
                        new ActiveScreenHintGridArea(widthPercent, heightPercent,
                                activeScreenHintGridAreaCenter);
                case ACTIVE_WINDOW ->
                        new ActiveWindowHintGridArea(widthPercent, heightPercent);
                case ALL_SCREENS ->
                        new AllScreensHintGridArea(widthPercent, heightPercent);
            };
        }
    }
}
