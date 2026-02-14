package mousemaster;

public sealed interface ComboMove {

    KeyOrAlias keyOrAlias();
    ComboMoveDuration duration();

    default boolean isPress() {
        return this instanceof PressComboMove;
    }

    default boolean isRelease() {
        return !isPress();
    }

    record PressComboMove(KeyOrAlias keyOrAlias, boolean eventMustBeEaten, ComboMoveDuration duration)
            implements ComboMove {
        @Override
        public String toString() {
            return (eventMustBeEaten ? "+" : "#") + keyOrAlias;
        }

    }

    record ReleaseComboMove(KeyOrAlias keyOrAlias, ComboMoveDuration duration) implements ComboMove {
        @Override
        public String toString() {
            return "-" + keyOrAlias;
        }

    }

}
