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

    // Bounding rectangles are in zoomed (physical) pixels, so the threshold is
    // multiplied by the screen's scale.
    // 13 unzoomed px = 13 physical px at 100% scale, 40 physical px at 300%.
    double MIN_DISTANCE_BETWEEN_HINTS_UNZOOMED = 13;

    static boolean isTooCloseToExistingUiElements(List<UiElement> elements, double x,
                                                  double y, double scale) {
        double threshold = MIN_DISTANCE_BETWEEN_HINTS_UNZOOMED * scale;
        double thresholdSquared = threshold * threshold;
        for (UiElement e : elements) {
            double dx = e.centerX() - x;
            double dy = e.centerY() - y;
            if (dx * dx + dy * dy < thresholdSquared)
                return true;
        }
        return false;
    }
}
