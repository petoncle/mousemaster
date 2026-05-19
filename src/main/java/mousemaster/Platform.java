package mousemaster;

import mousemaster.platform.ActiveAppFinder;
import mousemaster.platform.Console;
import mousemaster.platform.Keyboard;
import mousemaster.platform.KeyboardLayoutProvider;
import mousemaster.platform.KeyRegurgitator;
import mousemaster.platform.Overlay;
import mousemaster.platform.PlatformMouse;
import mousemaster.platform.Screens;
import mousemaster.platform.UiAutomation;

import java.util.List;

public interface Platform extends ModeListener {

    void update(double delta);

    void pumpEvents();

    void sleep() throws InterruptedException;

    void reset(MouseController mouseController, KeyboardManager keyboardManager,
               ModeMap modeMap,
               List<MousePositionListener> mousePositionListeners,
               KeyboardLayout activeKeyboardLayout);

    void shutdown();

    KeyRegurgitator keyRegurgitator();

    PlatformClock clock();

    KeyboardLayoutProvider keyboardLayoutProvider();

    Keyboard keyboard();

    PlatformMouse mouse();

    Screens screens();

    Overlay overlay();

    UiAutomation uiAutomation();

    ActiveAppFinder activeAppFinder();

    Console console();

}
