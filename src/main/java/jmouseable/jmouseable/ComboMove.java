package jmouseable.jmouseable;

public sealed interface ComboMove {

    Key key();
    ComboMoveDuration duration();

    default boolean isPress() {
        return this instanceof PressComboMove;
    }

    default boolean isRelease() {
        return !isPress();
    }

    record PressComboMove(Key key, boolean eventMustBeEaten, ComboMoveDuration duration)
            implements ComboMove {
        @Override
        public String toString() {
            return (eventMustBeEaten ? "_" : "-") + key.keyName();
        }

    }

    record ReleaseComboMove(Key key, ComboMoveDuration duration) implements ComboMove {
        @Override
        public String toString() {
            return "^" + key.keyName();
        }

    }

}
