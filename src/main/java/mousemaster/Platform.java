package mousemaster;

import java.util.List;

public interface Platform extends ModeListener {

    void update(double delta);

    void windowsMessagePump();

    void sleep() throws InterruptedException;

    void reset(MouseController mouseController, KeyboardManager keyboardManager,
               ModeMap modeMap,
               List<MousePositionListener> mousePositionListeners,
               KeyboardLayout activeKeyboardLayout);

    void shutdown();

    KeyRegurgitator keyRegurgitator();

    PlatformClock clock();

    KeyboardLayout activeKeyboardLayout();

}
