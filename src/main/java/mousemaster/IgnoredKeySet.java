package mousemaster;

import java.util.HashSet;
import java.util.Set;

public sealed interface IgnoredKeySet {

    IgnoredKeySet NONE = new Only(Set.of());
    IgnoredKeySet ALL = new AllExcept(Set.of());

    boolean isIgnored(Key key);
    IgnoredKeySet union(IgnoredKeySet other);

    /**
     * Only the listed keys are ignored (wait-ignore{keys}).
     * Empty keys means no key is ignored (plain wait).
     */
    record Only(Set<Key> keys) implements IgnoredKeySet {
        @Override
        public boolean isIgnored(Key key) {
            return keys.contains(key);
        }

        @Override
        public IgnoredKeySet union(IgnoredKeySet other) {
            return switch (other) {
                case Only o -> {
                    Set<Key> merged = new HashSet<>(keys);
                    merged.addAll(o.keys);
                    yield new Only(Set.copyOf(merged));
                }
                case AllExcept a -> {
                    Set<Key> remaining = new HashSet<>(a.keys);
                    remaining.removeAll(keys);
                    yield new AllExcept(Set.copyOf(remaining));
                }
            };
        }
    }

    /**
     * All keys except the listed ones are ignored (wait-ignore-all-except{keys}).
     * Empty keys means all keys are ignored (wait-ignore-all).
     */
    record AllExcept(Set<Key> keys) implements IgnoredKeySet {
        @Override
        public boolean isIgnored(Key key) {
            return !keys.contains(key);
        }

        @Override
        public IgnoredKeySet union(IgnoredKeySet other) {
            return switch (other) {
                case Only o -> {
                    Set<Key> remaining = new HashSet<>(keys);
                    remaining.removeAll(o.keys);
                    yield new AllExcept(Set.copyOf(remaining));
                }
                case AllExcept a -> {
                    Set<Key> intersection = new HashSet<>(keys);
                    intersection.retainAll(a.keys);
                    yield new AllExcept(Set.copyOf(intersection));
                }
            };
        }
    }

}
