package mousemaster;

import java.util.Set;

public sealed interface ComboAliasMove {

    enum Optionality {
        REQUIRED, OPTIONAL, AT_LEAST_ONE;

        public boolean isOptional() {
            return this != REQUIRED;
        }

        public String suffix() {
            return switch (this) {
                case REQUIRED -> "";
                case OPTIONAL -> "?";
                case AT_LEAST_ONE -> "+";
            };
        }
    }

    String aliasOrKeyName();
    ComboMoveDuration duration();
    Optionality optionality();
    boolean expand();
    default String expandedFromAlias() { return null; }

    default boolean isPress() {
        return this instanceof PressComboAliasMove;
    }

    default boolean isRelease() {
        return this instanceof ReleaseComboAliasMove;
    }

    record PressComboAliasMove(String aliasOrKeyName, boolean negated,
                               boolean eventMustBeEaten,
                               ComboMoveDuration duration,
                               Optionality optionality,
                               boolean expand,
                               String expandedFromAlias)
            implements ComboAliasMove {

        @Override
        public String toString() {
            return (eventMustBeEaten ? "+" : "#") + (negated ? "!" : "") +
                   (expand ? "*" : "") + aliasOrKeyName +
                   optionality.suffix();
        }

    }

    record ReleaseComboAliasMove(String aliasOrKeyName, boolean negated,
                                 ComboMoveDuration duration,
                                 Optionality optionality, boolean expand,
                                 String expandedFromAlias)
            implements ComboAliasMove {

        @Override
        public String toString() {
            return "-" + (negated ? "!" : "") + (expand ? "*" : "") +
                   aliasOrKeyName + optionality.suffix();
        }

    }

    record TapComboAliasMove(String aliasOrKeyName, ComboMoveDuration duration,
                              Optionality optionality,
                              String expandedFromAlias) implements ComboAliasMove {

        @Override
        public boolean expand() {
            return false;
        }

        @Override
        public String toString() {
            return aliasOrKeyName + optionality.suffix();
        }
    }

    sealed interface WaitComboAliasMove extends ComboAliasMove
            permits WaitComboAliasMove.KeyWaitComboAliasMove,
                    WaitComboAliasMove.PressWaitComboAliasMove,
                    WaitComboAliasMove.ReleaseWaitComboAliasMove {

        boolean ignoredKeysEatEvents();

        @Override
        default String aliasOrKeyName() { return null; }

        @Override
        default Optionality optionality() { return Optionality.REQUIRED; }

        @Override
        default boolean expand() { return false; }

        record KeyWaitComboAliasMove(Set<String> keyAliasOrKeyNames,
                                     boolean listedKeysAreIgnored,
                                     boolean ignoredKeysEatEvents,
                                     ComboMoveDuration duration) implements WaitComboAliasMove {
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

        record PressWaitComboAliasMove(boolean ignoredKeysEatEvents,
                                       ComboMoveDuration duration) implements WaitComboAliasMove {
            @Override
            public String toString() {
                String prefix = ignoredKeysEatEvents ? "+" : "#";
                String durationPart = "-" + duration.min().toMillis() +
                       (duration.max() == null ? "" : "-" + duration.max().toMillis());
                return prefix + "{+}" + durationPart;
            }
        }

        record ReleaseWaitComboAliasMove(boolean ignoredKeysEatEvents,
                                         ComboMoveDuration duration) implements WaitComboAliasMove {
            @Override
            public String toString() {
                String prefix = ignoredKeysEatEvents ? "+" : "#";
                String durationPart = "-" + duration.min().toMillis() +
                       (duration.max() == null ? "" : "-" + duration.max().toMillis());
                return prefix + "{-}" + durationPart;
            }
        }
    }

}
