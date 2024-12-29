package mousemaster;

import java.util.List;

public interface Platform {

    void update(double delta);

    void windowsMessagePump();

    void sleep() throws InterruptedException;

    void reset(MouseController mouseController, KeyboardManager keyboardManager,
               KeyboardLayout keyboardLayout, ModeMap modeMap,
               List<MousePositionListener> mousePositionListeners);

    void shutdown();

    KeyRegurgitator keyRegurgitator();

    PlatformClock clock();

}
