package mousemaster.platform;

import java.util.List;
import java.util.concurrent.Future;

public interface UiAutomation {

    Future<List<UiElement>> startFindInteractiveUiElements();

    record UiElement(double centerX, double centerY) {
    }
}
