package mousemaster;

public sealed interface GridArea {

    double widthPercent();

    double heightPercent();

    int topInset();

    int bottomInset();

    int leftInset();

    int rightInset();

    record ActiveScreenGridArea(double widthPercent, double heightPercent, int topInset,
                                int bottomInset, int leftInset, int rightInset)
            implements GridArea {
    }

    record ActiveWindowGridArea(double widthPercent, double heightPercent, int topInset,
                                int bottomInset, int leftInset, int rightInset)
            implements GridArea {
    }

    enum GridAreaType {
        ACTIVE_SCREEN, ACTIVE_WINDOW
    }

    class GridAreaBuilder {

        private GridAreaType type;
        private Double widthPercent;
        private Double heightPercent;
        private Integer topInset;
        private Integer bottomInset;
        private Integer leftInset;
        private Integer rightInset;

        public GridAreaBuilder() {
        }

        public GridAreaBuilder(GridArea gridArea) {
            switch (gridArea) {
                case ActiveScreenGridArea activeScreenGridArea -> {
                    this.type = GridAreaType.ACTIVE_SCREEN;
                    this.widthPercent = activeScreenGridArea.widthPercent;
                    this.heightPercent = activeScreenGridArea.heightPercent;
                    this.topInset = activeScreenGridArea.topInset;
                    this.bottomInset = activeScreenGridArea.bottomInset;
                    this.leftInset = activeScreenGridArea.leftInset;
                    this.rightInset = activeScreenGridArea.rightInset;
                }
                case ActiveWindowGridArea activeWindowGridArea -> {
                    this.type = GridAreaType.ACTIVE_WINDOW;
                    this.widthPercent = activeWindowGridArea.widthPercent;
                    this.heightPercent = activeWindowGridArea.heightPercent;
                    this.topInset = activeWindowGridArea.topInset;
                    this.bottomInset = activeWindowGridArea.bottomInset;
                    this.leftInset = activeWindowGridArea.leftInset;
                    this.rightInset = activeWindowGridArea.rightInset;
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

        public GridAreaBuilder topInset(int topInset) {
            this.topInset = topInset;
            return this;
        }

        public GridAreaBuilder bottomInset(int bottomInset) {
            this.bottomInset = bottomInset;
            return this;
        }

        public GridAreaBuilder leftInset(int leftInset) {
            this.leftInset = leftInset;
            return this;
        }

        public GridAreaBuilder rightInset(int rightInset) {
            this.rightInset = rightInset;
            return this;
        }

        public GridArea build() {
            return switch (type) {
                case ACTIVE_SCREEN ->
                        new ActiveScreenGridArea(widthPercent, heightPercent, topInset,
                                bottomInset, leftInset, rightInset);
                case ACTIVE_WINDOW ->
                        new ActiveWindowGridArea(widthPercent, heightPercent, topInset,
                                bottomInset, leftInset, rightInset);
            };
        }
    }

}
