package mousemaster;

public sealed interface ComboAliasMove {

    String aliasOrKeyName();
    ComboMoveDuration duration();

    default boolean isPress() {
        return this instanceof PressComboAliasMove;
    }

    default boolean isRelease() {
        return !isPress();
    }

    record PressComboAliasMove(String aliasOrKeyName, boolean eventMustBeEaten, ComboMoveDuration duration)
            implements ComboAliasMove {
        @Override
        public String toString() {
            return (eventMustBeEaten ? "+" : "#") + aliasOrKeyName;
        }

    }

    record ReleaseComboAliasMove(String aliasOrKeyName, ComboMoveDuration duration) implements
            ComboAliasMove {
        @Override
        public String toString() {
            return "-" + aliasOrKeyName;
        }

    }

}
