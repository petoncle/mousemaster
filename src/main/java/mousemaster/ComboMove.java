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
     * @param keysAreExempt true for wait^{keys} (listed keys don't break the wait),
     *                      false for wait{keys} (only listed keys break the wait).
     *                      Empty keys + keysAreExempt=true means plain wait (all keys break).
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
            if (keys.isEmpty())
                // Plain wait: all keys break.
                return true;
            if (keysAreExempt)
                // wait^{keys}: listed keys are exempt (don't break), everything else breaks.
                return !keys.contains(key);
            else
                // wait{keys}: only listed keys break.
                return keys.contains(key);
        }

        @Override
        public String toString() {
            String keyPart = keys.isEmpty() ? "" :
                    (keysAreExempt ? "^" : "") + "{" + keys.stream().map(Key::name).reduce((a, b) -> a + " " + b).orElse("") + "}";
            return "wait" + keyPart + "-" + duration.min().toMillis() +
                   (duration.max() == null ? "" : "-" + duration.max().toMillis());
        }
    }

}
