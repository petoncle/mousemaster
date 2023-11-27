package jmouseable.jmouseable;

public class KeyboardState {

    private final KeyboardManager keyboardManager;


    public KeyboardState(KeyboardManager keyboardManager) {
        this.keyboardManager = keyboardManager;
    }

    public boolean pressingNonComboKey() {
        return keyboardManager.pressingNonComboKey();
    }

}
