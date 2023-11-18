package jmouseable.jmouseable;

public record KeyAction(Key key, KeyState state) {
    @Override
    public String toString() {
        return state.toString() + key;
    }
}
