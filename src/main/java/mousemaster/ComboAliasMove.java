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
     * @param keysAreExempt true for wait-ignore{keys} (listed keys don't break the wait),
     *                      false for wait-ignore-all-except{keys} (only listed keys break the wait).
     *                      Empty keyAliasOrKeyNames + keysAreExempt=true means plain wait (all keys break).
     *                      Empty keyAliasOrKeyNames + keysAreExempt=false means wait-ignore-all (no keys break).
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
            String ignorePart;
            if (keyAliasOrKeyNames.isEmpty()) {
                ignorePart = keysAreExempt ? "" : "-ignore-all";
            }
            else if (keysAreExempt) {
                ignorePart = "-ignore{" + String.join(" ", keyAliasOrKeyNames) + "}";
            }
            else {
                ignorePart = "-ignore-all-except{" + String.join(" ", keyAliasOrKeyNames) + "}";
            }
            return "wait" + ignorePart + "-" + duration.min().toMillis() +
                   (duration.max() == null ? "" : "-" + duration.max().toMillis());
        }
    }

}
