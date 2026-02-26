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

    record TapComboAliasMove(String aliasOrKeyName, ComboMoveDuration duration,
                              boolean optional,
                              String expandedFromAlias) implements ComboAliasMove {
        @Override
        public boolean expand() {
            return false;
        }

        @Override
        public String toString() {
            return aliasOrKeyName + (optional ? "?" : "");
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
            String durationPart = "-" + duration.min().toMillis() +
                   (duration.max() == null ? "" : "-" + duration.max().toMillis());
            if (keyAliasOrKeyNames.isEmpty() && listedKeysAreIgnored) {
                // Plain wait (no ignore).
                return (ignoredKeysEatEvents ? "+" : "") + "wait" + durationPart;
            }
            String prefix = ignoredKeysEatEvents ? "+" : "#";
            String bang;
            String content;
            if (keyAliasOrKeyNames.isEmpty()) {
                // All keys: #{*} or +{*}
                bang = "";
                content = "*";
            }
            else if (listedKeysAreIgnored) {
                // Listed keys: #{keys} or +{keys}
                bang = "";
                content = String.join(" ", keyAliasOrKeyNames);
            }
            else {
                // All except listed: #!{keys} or +!{keys}
                bang = "!";
                content = String.join(" ", keyAliasOrKeyNames);
            }
            return prefix + bang + "{" + content + "}" + durationPart;
        }
    }

}
