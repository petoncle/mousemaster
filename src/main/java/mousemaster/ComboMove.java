package mousemaster;

public sealed interface ComboMove permits ComboMove.KeyComboMove, ComboMove.WaitComboMove {

    ComboMoveDuration duration();

    sealed interface KeyComboMove extends ComboMove permits PressComboMove, ReleaseComboMove, TapComboMove {
        KeyOrAlias keyOrAlias();
        boolean negated();

        default boolean isPress() {
            return this instanceof PressComboMove;
        }

        default boolean isRelease() {
            return this instanceof ReleaseComboMove;
        }

        default boolean isTap() {
            return this instanceof TapComboMove;
        }
    }

    record PressComboMove(KeyOrAlias keyOrAlias, boolean negated,
                          boolean eventMustBeEaten, ComboMoveDuration duration)
            implements KeyComboMove {
        @Override
        public String toString() {
            return (eventMustBeEaten ? "+" : "#") + (negated ? "!" : "") + keyOrAlias;
        }

    }

    record ReleaseComboMove(KeyOrAlias keyOrAlias, boolean negated,
                            ComboMoveDuration duration) implements KeyComboMove {
        @Override
        public String toString() {
            return "-" + (negated ? "!" : "") + keyOrAlias;
        }

    }

    record TapComboMove(KeyOrAlias keyOrAlias,
                         String sourceAlias,
                         ComboMoveDuration duration) implements KeyComboMove {
        @Override
        public boolean negated() {
            return false;
        }

        @Override
        public String toString() {
            return keyOrAlias.toString();
        }
    }

    record WaitComboMove(KeySet ignoredKeySet, boolean ignoredKeysEatEvents,
                         ComboMoveDuration duration) implements ComboMove {

        @Override
        public String toString() {
            String durationPart = "-" + duration.min().toMillis() +
                   (duration.max() == null ? "" : "-" + duration.max().toMillis());
            return switch (ignoredKeySet) {
                case KeySet.Only only -> {
                    if (only.keys().isEmpty()) {
                        // Plain wait (no ignore).
                        yield (ignoredKeysEatEvents ? "+" : "") + "wait" + durationPart;
                    }
                    // #{keys} or +{keys}
                    String prefix = ignoredKeysEatEvents ? "+" : "#";
                    String keys = only.keys().stream().map(Key::name)
                            .reduce((a, b) -> a + " " + b).orElse("");
                    yield prefix + "{" + keys + "}" + durationPart;
                }
                case KeySet.AllExcept allExcept -> {
                    String prefix = ignoredKeysEatEvents ? "+" : "#";
                    if (allExcept.keys().isEmpty()) {
                        // #{*} or +{*}
                        yield prefix + "{*}" + durationPart;
                    }
                    // #!{keys} or +!{keys}
                    String keys = allExcept.keys().stream().map(Key::name)
                            .reduce((a, b) -> a + " " + b).orElse("");
                    yield prefix + "!{" + keys + "}" + durationPart;
                }
            };
        }
    }

}
