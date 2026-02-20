package mousemaster;

public sealed interface ResolvedKeyComboMove {

    Key key();
    ComboMoveDuration duration();

    default boolean isPress() {
        return this instanceof ResolvedPressComboMove;
    }

    default boolean isRelease() {
        return !isPress();
    }

    record ResolvedPressComboMove(Key key, boolean eventMustBeEaten, ComboMoveDuration duration)
            implements ResolvedKeyComboMove {
    }

    record ResolvedReleaseComboMove(Key key, ComboMoveDuration duration)
            implements ResolvedKeyComboMove {
    }

}
