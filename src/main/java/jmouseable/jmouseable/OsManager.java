package jmouseable.jmouseable;

public interface OsManager {

    void update(double delta);

    void setMouseManagerAndKeyboardManager(MouseManager mouseManager,
                                           KeyboardManager keyboardManager);

}
