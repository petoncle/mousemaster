package mousemaster;

public sealed interface ComboMove permits ComboMove.KeyComboMove, ComboMove.WaitComboMove {

    ComboMoveDuration duration();

    sealed interface KeyComboMove extends ComboMove permits PressComboMove, ReleaseComboMove {
        KeyOrAlias keyOrAlias();

        default boolean isPress() {
            return this instanceof PressComboMove;
        }

        default boolean isRelease() {
            return this instanceof ReleaseComboMove;
        }
    }

    record PressComboMove(KeyOrAlias keyOrAlias, boolean eventMustBeEaten, ComboMoveDuration duration)
            implements KeyComboMove {
        @Override
        public String toString() {
            return (eventMustBeEaten ? "+" : "#") + keyOrAlias;
        }

    }

    record ReleaseComboMove(KeyOrAlias keyOrAlias, ComboMoveDuration duration) implements KeyComboMove {
        @Override
        public String toString() {
            return "-" + keyOrAlias;
        }

    }

    record WaitComboMove(IgnoredKeySet ignoredKeySet, boolean ignoredKeysEatEvents,
                         ComboMoveDuration duration) implements ComboMove {

        @Override
        public String toString() {
            String ignorePart = switch (ignoredKeySet) {
                case IgnoredKeySet.Only only ->
                        only.keys().isEmpty() ? "" :
                        "-ignore{" + only.keys().stream().map(Key::name)
                                .reduce((a, b) -> a + " " + b).orElse("") + "}";
                case IgnoredKeySet.AllExcept allExcept ->
                        allExcept.keys().isEmpty() ? "-ignore-all" :
                        "-ignore-all-except{" + allExcept.keys().stream().map(Key::name)
                                .reduce((a, b) -> a + " " + b).orElse("") + "}";
            };
            return (ignoredKeysEatEvents ? "+" : "") + "wait" + ignorePart + "-" + duration.min().toMillis() +
                   (duration.max() == null ? "" : "-" + duration.max().toMillis());
        }
    }

}
