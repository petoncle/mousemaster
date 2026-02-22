package mousemaster;

import java.util.HashSet;
import java.util.Set;

public sealed interface KeySet {

    KeySet NONE = new Only(Set.of());
    KeySet ALL = new AllExcept(Set.of());

    boolean contains(Key key);
    KeySet union(KeySet other);

    /**
     * Only the listed keys match (#{keys}).
     * Empty keys means no key matches (plain wait).
     */
    record Only(Set<Key> keys) implements KeySet {
        @Override
        public boolean contains(Key key) {
            return keys.contains(key);
        }

        @Override
        public KeySet union(KeySet other) {
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
     * All keys except the listed ones match (#!{keys}).
     * Empty keys means all keys match (#{*}).
     */
    record AllExcept(Set<Key> keys) implements KeySet {
        @Override
        public boolean contains(Key key) {
            return !keys.contains(key);
        }

        @Override
        public KeySet union(KeySet other) {
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
