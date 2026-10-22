package mousemaster;

import java.time.Instant;

public sealed interface KeyEvent {

    Instant time();

    Key key();

    /**
     * What the key typed because of the modifier keys held at its press: ? for / with a shift
     * on us-qwerty, null for / alone.
     */
    Key modifiedTypedKey();

    default boolean isPress() {
        return this instanceof PressKeyEvent;
    }

    default boolean isRelease() {
        return !isPress();
    }

    record PressKeyEvent(Instant time, Key key, Key modifiedTypedKey) implements KeyEvent {

        public PressKeyEvent(Instant time, Key key) {
            this(time, key, null);
        }

        @Override
        public String toString() {
            // Does not include time.
            return "+" + key.name();
        }
    }

    record ReleaseKeyEvent(Instant time, Key key, Key modifiedTypedKey) implements KeyEvent {

        public ReleaseKeyEvent(Instant time, Key key) {
            this(time, key, null);
        }

        @Override
        public String toString() {
            // Does not include time.
            return "-" + key.name();
        }
    }

}
