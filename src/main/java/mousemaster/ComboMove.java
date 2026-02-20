package mousemaster;

import java.util.Set;

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

    /**
     * @param keysAreExempt true for wait-ignore{keys} (listed keys don't break the wait),
     *                      false for wait-ignore-all-except{keys} (only listed keys break the wait).
     *                      Empty keys + keysAreExempt=true means plain wait (all keys break).
     *                      Empty keys + keysAreExempt=false means wait-ignore-all (no keys break).
     */
    record WaitComboMove(Set<Key> keys, boolean keysAreExempt,
                         ComboMoveDuration duration) implements ComboMove {
        @Override
        public KeyOrAlias keyOrAlias() {
            return null;
        }

        /**
         * Returns true if the given key breaks (cancels/resets) the wait.
         */
        public boolean keyBreaksWait(Key key) {
            if (keysAreExempt)
                // wait-ignore{keys} or plain wait: listed keys are ignored (don't break),
                // everything else breaks.
                // Empty keys + keysAreExempt=true: all keys break (plain wait).
                return !keys.contains(key);
            else
                // wait-ignore-all-except{keys} or wait-ignore-all: only listed keys break.
                // Empty keys + keysAreExempt=false: no keys break (wait-ignore-all).
                return keys.contains(key);
        }

        @Override
        public String toString() {
            String ignorePart;
            if (keys.isEmpty()) {
                ignorePart = keysAreExempt ? "" : "-ignore-all";
            }
            else if (keysAreExempt) {
                ignorePart = "-ignore{" + keys.stream().map(Key::name).reduce((a, b) -> a + " " + b).orElse("") + "}";
            }
            else {
                ignorePart = "-ignore-all-except{" + keys.stream().map(Key::name).reduce((a, b) -> a + " " + b).orElse("") + "}";
            }
            return "wait" + ignorePart + "-" + duration.min().toMillis() +
                   (duration.max() == null ? "" : "-" + duration.max().toMillis());
        }
    }

}
