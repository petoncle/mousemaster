package mousemaster;

import mousemaster.platform.ActiveAppFinder;
import mousemaster.platform.Console;
import mousemaster.platform.KeyboardController;
import mousemaster.platform.Overlay;
import mousemaster.platform.UiAutomation;

import java.util.List;
import java.util.Set;

public interface Platform extends ModeListener {

    void update(double delta);

    void pumpEvents();

    void sleep() throws InterruptedException;

    void reset(MouseManager mouseManager, KeyboardManager keyboardManager,
               KeyRegurgitator keyRegurgitator, KeyRedactor keyRedactor,
               boolean logLastKeyEventsOnExit,
               ModeMap modeMap,
               ZoomManager zoomManager,
               IndicatorManager indicatorManager,
               List<MousePositionListener> mousePositionListeners,
               KeyboardLayout activeKeyboardLayout);

    void shutdown();

    /**
     * Shuts down, then kills the process instead of exiting it: Qt's and the CRT's teardown
     * warns and can hang for seconds when it runs off the main thread.
     */
    void killProcess(int exitCode);

    Clock clock();

    KeyboardLayout activeKeyboardLayout();

    KeyboardController keyboard();

    mousemaster.platform.MouseController mouse();

    Set<Screen> screens();

    Overlay overlay();

    UiAutomation uiAutomation();

    ActiveAppFinder activeAppFinder();

    Console console();

}
