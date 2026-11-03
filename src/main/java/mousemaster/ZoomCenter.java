package mousemaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum ZoomCenter {

    ACTIVE_SCREEN_CENTER,
    MOUSE,
    LAST_SELECTED_HINT;

    private static final Logger logger = LoggerFactory.getLogger(ZoomCenter.class);

    public Point centerPoint(Rectangle screenRectangle, int mouseX,
                             int mouseY, Point lastSelectedHintPoint) {
        return switch (this) {
            case ACTIVE_SCREEN_CENTER -> screenRectangle.center();
            case MOUSE -> new Point(mouseX, mouseY);
            case LAST_SELECTED_HINT -> {
                if (lastSelectedHintPoint == null) {
                    logger.warn(
                            "Cannot use last selected hint as zoom center because " +
                            "no hint was selected, this is likely due to " +
                            "a misconfiguration of <previous-mode>.select, " +
                            "using screen center as fallback");
                    yield screenRectangle.center();
                }
                yield lastSelectedHintPoint;
            }
        };
    }
}
