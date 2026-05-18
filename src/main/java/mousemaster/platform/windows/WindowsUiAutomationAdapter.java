package mousemaster.platform.windows;

import mousemaster.*;

import mousemaster.platform.UiAutomation;

import java.util.List;
import java.util.concurrent.Future;

public class WindowsUiAutomationAdapter implements UiAutomation {

    @Override
    public Future<List<UiElement>> startFindInteractiveUiElements() {
        return WindowsUiAutomation.startFindInteractiveUiElements();
    }

}
