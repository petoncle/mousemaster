package mousemaster;

import java.time.Instant;

public sealed interface KeyEvent {

    Instant time();

    Key key();

    default boolean isPress() {
        return this instanceof PressKeyEvent;
    }

    default boolean isRelease() {
        return !isPress();
    }

    record PressKeyEvent(Instant time, Key key) implements KeyEvent {
        @Override
        public String toString() {
            // Does not include time.
            return "+" + key.name();
        }
    }

    record ReleaseKeyEvent(Instant time, Key key) implements KeyEvent {
        @Override
        public String toString() {
            // Does not include time.
            return "-" + key.name();
        }
    }

}
