package mousemaster;

public sealed interface ComboMove permits ComboMove.KeyComboMove, ComboMove.WaitComboMove {

    ComboMoveDuration duration();

    sealed interface KeyComboMove extends ComboMove permits PressComboMove, ReleaseComboMove, TapComboMove {
        KeyOrAlias keyOrAlias();
        boolean negated();
        String expandedFromAlias();

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
                          boolean eventMustBeEaten, ComboMoveDuration duration,
                          String expandedFromAlias)
            implements KeyComboMove {
        @Override
        public String toString() {
            return (eventMustBeEaten ? "+" : "#") + (negated ? "!" : "") + keyOrAlias;
        }
    }

    record ReleaseComboMove(KeyOrAlias keyOrAlias, boolean negated,
                            ComboMoveDuration duration,
                            String expandedFromAlias) implements KeyComboMove {
        @Override
        public String toString() {
            return "-" + (negated ? "!" : "") + keyOrAlias;
        }
    }

    record TapComboMove(KeyOrAlias keyOrAlias,
                         String expandedFromAlias,
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

    sealed interface WaitComboMove extends ComboMove
            permits WaitComboMove.KeyWaitComboMove,
                    WaitComboMove.PressWaitComboMove,
                    WaitComboMove.ReleaseWaitComboMove {

        boolean ignoredKeysEatEvents();

        boolean matchesEvent(KeyEvent event);

        boolean canAbsorbEvents();

        record KeyWaitComboMove(KeySet ignoredKeySet, boolean ignoredKeysEatEvents,
                                ComboMoveDuration duration) implements WaitComboMove {
            @Override
            public boolean matchesEvent(KeyEvent event) {
                return ignoredKeySet.contains(event.key());
            }

            @Override
            public boolean canAbsorbEvents() {
                return !ignoredKeySet.equals(KeySet.NONE);
            }

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

        record PressWaitComboMove(boolean ignoredKeysEatEvents,
                                  ComboMoveDuration duration) implements WaitComboMove {
            @Override
            public boolean matchesEvent(KeyEvent event) {
                return event.isPress();
            }

            @Override
            public boolean canAbsorbEvents() {
                return true;
            }

            @Override
            public String toString() {
                String prefix = ignoredKeysEatEvents ? "+" : "#";
                String durationPart = "-" + duration.min().toMillis() +
                       (duration.max() == null ? "" : "-" + duration.max().toMillis());
                return prefix + "{+}" + durationPart;
            }
        }

        record ReleaseWaitComboMove(boolean ignoredKeysEatEvents,
                                    ComboMoveDuration duration) implements WaitComboMove {
            @Override
            public boolean matchesEvent(KeyEvent event) {
                return event.isRelease();
            }

            @Override
            public boolean canAbsorbEvents() {
                return true;
            }

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
