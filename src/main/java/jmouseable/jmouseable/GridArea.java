package jmouseable.jmouseable;

public sealed interface GridArea {

    double widthPercent();

    double heightPercent();

    record ActiveScreenGridArea(double widthPercent, double heightPercent) implements GridArea {
    }

    record ActiveWindowGridArea(double widthPercent, double heightPercent) implements GridArea {
    }

    enum GridAreaType {
        ACTIVE_SCREEN, ACTIVE_WINDOW
    }

    class GridAreaBuilder {

        private GridAreaType type;
        private Double widthPercent;
        private Double heightPercent;

        public GridAreaBuilder() {
        }

        public GridAreaBuilder(GridArea gridArea) {
            switch (gridArea) {
                case ActiveScreenGridArea activeScreenGridArea -> {
                    this.type = GridAreaType.ACTIVE_SCREEN;
                    this.widthPercent = activeScreenGridArea.widthPercent;
                    this.heightPercent = activeScreenGridArea.heightPercent;
                }
                case ActiveWindowGridArea activeWindowGridArea -> {
                    this.type = GridAreaType.ACTIVE_WINDOW;
                    this.widthPercent = activeWindowGridArea.widthPercent;
                    this.heightPercent = activeWindowGridArea.heightPercent;
                }
            }
        }

        public GridAreaType type() {
            return type;
        }

        public Double widthPercent() {
            return widthPercent;
        }

        public Double heightPercent() {
            return heightPercent;
        }

        public GridAreaBuilder type(GridAreaType type) {
            this.type = type;
            return this;
        }

        public GridAreaBuilder widthPercent(double widthPercent) {
            this.widthPercent = widthPercent;
            return this;
        }

        public GridAreaBuilder heightPercent(double heightPercent) {
            this.heightPercent = heightPercent;
            return this;
        }

        public GridArea build() {
            return switch (type) {
                case ACTIVE_SCREEN -> new ActiveScreenGridArea(widthPercent, heightPercent);
                case ACTIVE_WINDOW -> new ActiveWindowGridArea(widthPercent, heightPercent);
            };
        }
    }

}
