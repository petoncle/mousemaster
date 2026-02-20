package mousemaster;

public sealed interface ComboMove {

    KeyOrAlias keyOrAlias();
    ComboMoveDuration duration();

    default boolean isPress() {
        return this instanceof PressComboMove;
    }

    default boolean isRelease() {
        return this instanceof ReleaseComboMove;
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

    record WaitComboMove(IgnoredKeySet ignoredKeySet,
                         ComboMoveDuration duration) implements ComboMove {
        @Override
        public KeyOrAlias keyOrAlias() {
            return null;
        }

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
            return "wait" + ignorePart + "-" + duration.min().toMillis() +
                   (duration.max() == null ? "" : "-" + duration.max().toMillis());
        }
    }

}
