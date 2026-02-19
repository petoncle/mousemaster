package mousemaster;

import java.util.Set;

public sealed interface ComboAliasMove {

    String aliasOrKeyName();
    ComboMoveDuration duration();
    boolean optional();

    default boolean isPress() {
        return this instanceof PressComboAliasMove;
    }

    default boolean isRelease() {
        return this instanceof ReleaseComboAliasMove;
    }

    record PressComboAliasMove(String aliasOrKeyName, boolean eventMustBeEaten,
                               ComboMoveDuration duration, boolean optional)
            implements ComboAliasMove {
        @Override
        public String toString() {
            return (eventMustBeEaten ? "+" : "#") + aliasOrKeyName;
        }

    }

    record ReleaseComboAliasMove(String aliasOrKeyName, ComboMoveDuration duration,
                                 boolean optional) implements
            ComboAliasMove {
        @Override
        public String toString() {
            return "-" + aliasOrKeyName;
        }

    }

    /**
     * @param keysAreExempt true for wait^{keys} (listed keys don't break the wait),
     *                      false for wait{keys} (only listed keys break the wait).
     *                      Empty keyAliasOrKeyNames + keysAreExempt=true means plain wait (all keys break).
     */
    record WaitComboAliasMove(Set<String> keyAliasOrKeyNames, boolean keysAreExempt,
                              ComboMoveDuration duration) implements ComboAliasMove {
        @Override
        public String aliasOrKeyName() {
            return null;
        }

        @Override
        public boolean optional() {
            return false;
        }

        @Override
        public String toString() {
            String keyPart = keyAliasOrKeyNames.isEmpty() ? "" :
                    (keysAreExempt ? "^" : "") + "{" + String.join(" ", keyAliasOrKeyNames) + "}";
            return "wait" + keyPart + "-" + duration.min().toMillis() +
                   (duration.max() == null ? "" : "-" + duration.max().toMillis());
        }
    }

}
