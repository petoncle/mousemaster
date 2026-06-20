package mousemaster.platform;

import java.util.List;
import java.util.concurrent.Future;

public interface PlatformUiAutomation {

    Future<List<UiElement>> startFindInteractiveUiElements();

    record UiElement(double centerX, double centerY) {
    }
}
