package mousemaster;

public record Area(AreaSize size, AreaCenter center) {

    /** The center area-center sets, else the one of the area, which a mutation can change. */
    public AreaCenter centerOrDefault() {
        return center != null || size.source() == null ? center : defaultCenter(size.source());
    }

    private static AreaCenter defaultCenter(AreaSizeSource source) {
        return switch (source) {
            case ACTIVE_SCREEN, ALL_SCREENS -> AreaCenter.ACTIVE_SCREEN_CENTER;
            case ACTIVE_WINDOW -> AreaCenter.ACTIVE_WINDOW_CENTER;
            case LAST_SELECTED_HINT_CELL -> AreaCenter.LAST_SELECTED_HINT;
            case LAST_OVERLAY_AREA -> AreaCenter.LAST_OVERLAY_AREA_CENTER;
        };
    }

    public AreaBuilder builder() {
        return new AreaBuilder(this);
    }

    public static class AreaBuilder {
        private AreaSizeSource source;
        private Double widthPercent;
        private Double heightPercent;
        private Double topInset;
        private Double bottomInset;
        private Double leftInset;
        private Double rightInset;
        private AreaCenter center;

        public AreaBuilder() {

        }

        public AreaBuilder(Area area) {
            this.source = area.size.source();
            this.widthPercent = area.size.widthPercent();
            this.heightPercent = area.size.heightPercent();
            this.topInset = area.size.topInset();
            this.bottomInset = area.size.bottomInset();
            this.leftInset = area.size.leftInset();
            this.rightInset = area.size.rightInset();
            this.center = area.center;
        }

        public AreaBuilder source(AreaSizeSource source) {
            this.source = source;
            return this;
        }

        public AreaBuilder widthPercent(Double widthPercent) {
            this.widthPercent = widthPercent;
            return this;
        }

        public AreaBuilder heightPercent(Double heightPercent) {
            this.heightPercent = heightPercent;
            return this;
        }

        public AreaBuilder topInset(Double topInset) {
            this.topInset = topInset;
            return this;
        }

        public AreaBuilder bottomInset(Double bottomInset) {
            this.bottomInset = bottomInset;
            return this;
        }

        public AreaBuilder leftInset(Double leftInset) {
            this.leftInset = leftInset;
            return this;
        }

        public AreaBuilder rightInset(Double rightInset) {
            this.rightInset = rightInset;
            return this;
        }

        public AreaBuilder center(AreaCenter center) {
            this.center = center;
            return this;
        }

        public AreaSizeSource source() {
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

        public AreaCenter center() {
            return center;
        }

        public void extend(AreaBuilder parent) {
            if (source == null) source = parent.source;
            if (widthPercent == null) widthPercent = parent.widthPercent;
            if (heightPercent == null) heightPercent = parent.heightPercent;
            if (topInset == null) topInset = parent.topInset;
            if (bottomInset == null) bottomInset = parent.bottomInset;
            if (leftInset == null) leftInset = parent.leftInset;
            if (rightInset == null) rightInset = parent.rightInset;
            if (center == null) center = parent.center;
        }

        public Area build() {
            AreaSize size = new AreaSize(source, widthPercent, heightPercent,
                    topInset, bottomInset, leftInset, rightInset);
            return new Area(size, center);
        }
    }
}
