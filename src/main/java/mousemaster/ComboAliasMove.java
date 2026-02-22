package mousemaster;

import java.util.Set;

public sealed interface ComboAliasMove {

    String aliasOrKeyName();
    ComboMoveDuration duration();
    boolean optional();
    boolean expand();

    default boolean isPress() {
        return this instanceof PressComboAliasMove;
    }

    default boolean isRelease() {
        return this instanceof ReleaseComboAliasMove;
    }

    record PressComboAliasMove(String aliasOrKeyName, boolean negated,
                               boolean eventMustBeEaten,
                               ComboMoveDuration duration, boolean optional,
                               boolean expand)
            implements ComboAliasMove {
        @Override
        public String toString() {
            return (eventMustBeEaten ? "+" : "#") + (negated ? "!" : "") +
                   (expand ? "*" : "") + aliasOrKeyName;
        }

    }

    record ReleaseComboAliasMove(String aliasOrKeyName, boolean negated,
                                 ComboMoveDuration duration,
                                 boolean optional, boolean expand)
            implements ComboAliasMove {
        @Override
        public String toString() {
            return "-" + (negated ? "!" : "") + (expand ? "*" : "") +
                   aliasOrKeyName;
        }

    }

    record WaitComboAliasMove(Set<String> keyAliasOrKeyNames, boolean listedKeysAreIgnored,
                              boolean ignoredKeysEatEvents, ComboMoveDuration duration) implements ComboAliasMove {
        @Override
        public String aliasOrKeyName() {
            return null;
        }

        @Override
        public boolean optional() {
            return false;
        }

        @Override
        public boolean expand() {
            return false;
        }

        @Override
        public String toString() {
            String prefix = ignoredKeysEatEvents ? "+" : "";
            if (keyAliasOrKeyNames.isEmpty() && listedKeysAreIgnored) {
                // Plain wait (no ignore).
                return prefix + "wait-" + duration.min().toMillis() +
                       (duration.max() == null ? "" : "-" + duration.max().toMillis());
            }
            String ignorePart;
            if (keyAliasOrKeyNames.isEmpty()) {
                ignorePart = "ignore-all";
            }
            else if (listedKeysAreIgnored) {
                ignorePart = "ignore-{" + String.join(" ", keyAliasOrKeyNames) + "}";
            }
            else {
                ignorePart = "ignore-all-except-{" + String.join(" ", keyAliasOrKeyNames) + "}";
            }
            return prefix + ignorePart + "-" + duration.min().toMillis() +
                   (duration.max() == null ? "" : "-" + duration.max().toMillis());
        }
    }

}
