package mousemaster;

public record ZoomConfiguration(double percent, ZoomAreaSize areaSize, ZoomCenter center,
                                boolean animationEnabled, Easing animationEasing,
                                double animationDurationMillis) {

    /** The effective zoom, given the last selected hint cell (null if none). PERCENT uses percent;
     *  LAST_SELECTED_HINT_CELL fits the cell scaled by width/heightPercent into the screen. */
    public double percent(Rectangle cell, Rectangle screen) {
        if (areaSize.source() != ZoomAreaSizeSource.LAST_SELECTED_HINT_CELL || cell == null)
            return percent;
        return Math.min(screen.width() / (cell.width() * areaSize.widthPercent()),
                        screen.height() / (cell.height() * areaSize.heightPercent()));
    }

    public static class ZoomConfigurationBuilder {
        private Double percent;
        private ZoomAreaSizeSource areaSizeSource;
        private Double areaWidthPercent;
        private Double areaHeightPercent;
        private ZoomCenter center;
        private Boolean animationEnabled;
        private Easing animationEasing;
        private Double animationDurationMillis;

        public ZoomConfigurationBuilder percent(double percent) {
            this.percent = percent;
            return this;
        }

        public ZoomConfigurationBuilder areaSizeSource(ZoomAreaSizeSource areaSizeSource) {
            this.areaSizeSource = areaSizeSource;
            return this;
        }

        public ZoomConfigurationBuilder areaWidthPercent(double areaWidthPercent) {
            this.areaWidthPercent = areaWidthPercent;
            return this;
        }

        public ZoomConfigurationBuilder areaHeightPercent(double areaHeightPercent) {
            this.areaHeightPercent = areaHeightPercent;
            return this;
        }

        public ZoomConfigurationBuilder center(ZoomCenter center) {
            this.center = center;
            return this;
        }

        public ZoomConfigurationBuilder animationEnabled(boolean animationEnabled) {
            this.animationEnabled = animationEnabled;
            return this;
        }

        public ZoomConfigurationBuilder animationEasing(Easing animationEasing) {
            this.animationEasing = animationEasing;
            return this;
        }

        public ZoomConfigurationBuilder animationDurationMillis(double animationDurationMillis) {
            this.animationDurationMillis = animationDurationMillis;
            return this;
        }

        public Double percent() {
            return percent;
        }

        public ZoomAreaSizeSource areaSizeSource() {
            return areaSizeSource;
        }

        public Double areaWidthPercent() {
            return areaWidthPercent;
        }

        public Double areaHeightPercent() {
            return areaHeightPercent;
        }

        public ZoomCenter center() {
            return center;
        }

        public Boolean animationEnabled() {
            return animationEnabled;
        }

        public Easing animationEasing() {
            return animationEasing;
        }

        public Double animationDurationMillis() {
            return animationDurationMillis;
        }

        public ZoomConfiguration build() {
            return new ZoomConfiguration(percent,
                    new ZoomAreaSize(areaSizeSource, areaWidthPercent, areaHeightPercent),
                    center, animationEnabled, animationEasing, animationDurationMillis);
        }

    }
}
