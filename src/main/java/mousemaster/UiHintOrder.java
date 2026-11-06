package mousemaster;

public enum UiHintOrder {

    TOP_TO_BOTTOM(null),
    BOTTOM_TO_TOP(null),
    LEFT_TO_RIGHT(null),
    RIGHT_TO_LEFT(null),
    ACTIVE_SCREEN_CENTER(AreaCenter.ACTIVE_SCREEN_CENTER),
    MOUSE(AreaCenter.MOUSE),
    TARGET(AreaCenter.TARGET),
    LAST_SELECTED_HINT(AreaCenter.LAST_SELECTED_HINT),
    ACTIVE_WINDOW_CENTER(AreaCenter.ACTIVE_WINDOW_CENTER),
    LAST_OVERLAY_AREA_CENTER(AreaCenter.LAST_OVERLAY_AREA_CENTER);

    /** The point the nearest elements come first from, null for an edge. */
    private final AreaCenter center;

    UiHintOrder(AreaCenter center) {
        this.center = center;
    }

    public AreaCenter center() {
        return center;
    }

}
