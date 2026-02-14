package mousemaster;

public sealed interface ResolvedComboMove {

    Key key();
    ComboMoveDuration duration();

    default boolean isPress() {
        return this instanceof ResolvedPressComboMove;
    }

    default boolean isRelease() {
        return !isPress();
    }

    record ResolvedPressComboMove(Key key, boolean eventMustBeEaten, ComboMoveDuration duration)
            implements ResolvedComboMove {
    }

    record ResolvedReleaseComboMove(Key key, ComboMoveDuration duration)
            implements ResolvedComboMove {
    }

}
