package mousemaster;

public enum AreaCenter {

    ACTIVE_SCREEN_CENTER,
    MOUSE,
    TARGET,
    LAST_SELECTED_HINT,
    ACTIVE_WINDOW_CENTER,
    LAST_OVERLAY_AREA_CENTER;

    public boolean isMouseOrTarget() {
        return this == MOUSE || this == TARGET;
    }

}
