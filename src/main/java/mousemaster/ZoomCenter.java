package mousemaster;

public enum ZoomCenter {

    SCREEN_CENTER,
    MOUSE,
    LAST_SELECTED_HINT;

    public Point centerPoint(Rectangle screenRectangle, int mouseX,
                      int mouseY, Point lastSelectedHintPoint) {
        return switch (this) {
            case SCREEN_CENTER -> screenRectangle.center();
            case MOUSE -> new Point(mouseX, mouseY);
            case LAST_SELECTED_HINT -> lastSelectedHintPoint;
        };
    }
}
