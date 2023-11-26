package jmouseable.jmouseable;

import java.util.List;

public interface Platform {

    void update(double delta);

    void reset(MouseController mouseController, KeyboardManager keyboardManager,
               KeyboardLayout keyboardLayout, ModeMap modeMap,
               List<MousePositionListener> mousePositionListeners);

}
