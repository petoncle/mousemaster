package mousemaster;

import mousemaster.platform.ActiveAppFinder;
import mousemaster.platform.Console;
import mousemaster.platform.KeyboardController;
import mousemaster.platform.Overlay;
import mousemaster.platform.Screens;
import mousemaster.platform.UiAutomation;

import java.util.List;

public interface Platform extends ModeListener {

    void update(double delta);

    void pumpEvents();

    void sleep() throws InterruptedException;

    void reset(MouseManager mouseManager, KeyboardManager keyboardManager,
               ModeMap modeMap,
               ZoomManager zoomManager,
               List<MousePositionListener> mousePositionListeners,
               KeyboardLayout activeKeyboardLayout);

    void shutdown();

    /**
     * Shuts down, then kills the process instead of exiting it: Qt's and the CRT's teardown
     * warns and can hang for seconds when it runs off the main thread.
     */
    void killProcess(int exitCode);

    KeyRegurgitator keyRegurgitator();

    Clock clock();

    KeyboardLayout activeKeyboardLayout();

    KeyboardController keyboard();

    mousemaster.platform.MouseController mouse();

    Screens screens();

    Overlay overlay();

    UiAutomation uiAutomation();

    ActiveAppFinder activeAppFinder();

    Console console();

}
