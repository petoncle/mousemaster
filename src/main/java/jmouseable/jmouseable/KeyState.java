package jmouseable.jmouseable;

public enum KeyState {

    RELEASED, PRESSED;

    public boolean pressed() {
        return this == PRESSED;
    }

}
