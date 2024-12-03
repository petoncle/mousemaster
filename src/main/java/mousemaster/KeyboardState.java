package mousemaster;

public class KeyboardState {

    private final KeyboardManager keyboardManager;


    public KeyboardState(KeyboardManager keyboardManager) {
        this.keyboardManager = keyboardManager;
    }

    public boolean pressingUnhandledKey() {
        return keyboardManager.pressingUnhandledKey();
    }
}
