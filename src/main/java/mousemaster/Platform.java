package mousemaster;

import mousemaster.platform.ActiveAppFinder;
import mousemaster.platform.Console;
import mousemaster.platform.KeyRegurgitator;
import mousemaster.platform.KeyboardLayoutProvider;
import mousemaster.platform.PlatformKeyboard;
import mousemaster.platform.PlatformMouse;
import mousemaster.platform.PlatformOverlay;
import mousemaster.platform.PlatformUiAutomation;
import mousemaster.platform.Screens;

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

    PlatformKeyboard keyboard();

    PlatformMouse mouse();

    Screens screens();

    PlatformOverlay overlay();

    PlatformUiAutomation uiAutomation();

    ActiveAppFinder activeAppFinder();

    Console console();

}
