package mousemaster.platform;

import mousemaster.Rectangle;

import java.util.List;
import java.util.concurrent.Future;

public interface UiAutomation {

    /** Elements of the active window and of its popups. */
    Future<List<UiElement>> startFindActiveWindowUiElements();

    /**
     * Elements of every visible window that intersects the area, cropped to the area.
     * Elements a window in front covers are left out.
     */
    Future<List<UiElement>> startFindUiElementsInArea(Rectangle area);

    record UiElement(double centerX, double centerY) {
    }
}
