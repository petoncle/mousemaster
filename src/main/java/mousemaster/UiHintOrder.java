package mousemaster;

public enum UiHintOrder {

    TOP_TO_BOTTOM(null),
    BOTTOM_TO_TOP(null),
    LEFT_TO_RIGHT(null),
    RIGHT_TO_LEFT(null),
    ACTIVE_SCREEN_CENTER(HintGridAreaCenter.ACTIVE_SCREEN_CENTER),
    MOUSE(HintGridAreaCenter.MOUSE),
    TARGET(HintGridAreaCenter.TARGET),
    LAST_SELECTED_HINT(HintGridAreaCenter.LAST_SELECTED_HINT),
    ACTIVE_WINDOW_CENTER(HintGridAreaCenter.ACTIVE_WINDOW_CENTER),
    LAST_OVERLAY_AREA_CENTER(HintGridAreaCenter.LAST_OVERLAY_AREA_CENTER);

    /** The point the nearest elements come first from, null for an edge. */
    private final HintGridAreaCenter center;

    UiHintOrder(HintGridAreaCenter center) {
        this.center = center;
    }

    public HintGridAreaCenter center() {
        return center;
    }

}
