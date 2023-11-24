package jmouseable.jmouseable;

public interface OsManager {

    void update(double delta);

    void reset(MouseManager mouseManager,
               KeyboardManager keyboardManager, KeyboardLayout keyboardLayout,
               ModeMap modeMap);

}
