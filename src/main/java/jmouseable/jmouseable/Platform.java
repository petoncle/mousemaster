package jmouseable.jmouseable;

public interface Platform {

    void update(double delta);

    void reset(MouseManager mouseManager,
               KeyboardManager keyboardManager, KeyboardLayout keyboardLayout,
               ModeMap modeMap);

}
