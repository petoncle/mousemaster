package jmouseable.jmouseable;

import java.util.List;

public interface Platform {

    void update(double delta);

    void reset(MouseManager mouseManager, KeyboardManager keyboardManager,
               KeyboardLayout keyboardLayout, ModeMap modeMap,
               List<MousePositionListener> mousePositionListeners);

}
