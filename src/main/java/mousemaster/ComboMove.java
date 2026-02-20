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
     * @param listedKeysAreIgnored true for wait-ignore{keys} (listed keys are ignored),
     *                             false for wait-ignore-all-except{keys} (all keys except listed are ignored).
     *                             Empty keys + listedKeysAreIgnored=true means plain wait (no key is ignored).
     *                             Empty keys + listedKeysAreIgnored=false means wait-ignore-all (all keys are ignored).
     */
    record WaitComboMove(Set<Key> keys, boolean listedKeysAreIgnored,
                         ComboMoveDuration duration) implements ComboMove {
        @Override
        public KeyOrAlias keyOrAlias() {
            return null;
        }

        public boolean noKeyIsIgnored() {
            return keys.isEmpty() && listedKeysAreIgnored;
        }

        public boolean allKeysAreIgnored() {
            return keys.isEmpty() && !listedKeysAreIgnored;
        }

        /**
         * Returns true if the given key is ignored by the wait.
         */
        public boolean keyIsIgnored(Key key) {
            if (listedKeysAreIgnored)
                // wait-ignore{keys} or plain wait: listed keys are ignored.
                // Empty keys + listedKeysAreIgnored=true: no key is ignored (plain wait).
                return keys.contains(key);
            else
                // wait-ignore-all-except{keys} or wait-ignore-all: all except listed are ignored.
                // Empty keys + listedKeysAreIgnored=false: all keys are ignored (wait-ignore-all).
                return !keys.contains(key);
        }

        @Override
        public String toString() {
            String ignorePart;
            if (keys.isEmpty()) {
                ignorePart = listedKeysAreIgnored ? "" : "-ignore-all";
            }
            else if (listedKeysAreIgnored) {
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
