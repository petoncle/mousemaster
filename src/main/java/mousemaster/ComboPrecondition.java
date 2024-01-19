package mousemaster;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public record ComboPrecondition(Set<Set<Key>> mustRemainUnpressedKeySets,
                                Set<Set<Key>> mustRemainPressedKeySets) {

    public boolean isEmpty() {
        return mustRemainUnpressedKeySets.isEmpty() && mustRemainPressedKeySets.isEmpty();
    }

    public boolean satisfied(Set<Key> currentlyPressedKeys) {
        for (Set<Key> mustRemainUnpressedKeySet : mustRemainUnpressedKeySets) {
            if (currentlyPressedKeys.containsAll(mustRemainUnpressedKeySet))
                return false;
        }
        if (mustRemainPressedKeySets.isEmpty())
            return true;
        for (Set<Key> mustRemainPressedKeySet : mustRemainPressedKeySets) {
            if (currentlyPressedKeys.containsAll(mustRemainPressedKeySet))
                return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.join(" ", "^{" + keySetsToString(mustRemainUnpressedKeySets) + "}",
                "_{" + keySetsToString(mustRemainPressedKeySets) + "}");
    }

    private static String keySetsToString(Set<Set<Key>> keySets) {
        return keySets.stream()
                      .map(keySet -> keySet.stream()
                                           .map(Key::name)
                                           .collect(Collectors.joining(" ")))
                      .collect(Collectors.joining("|"));
    }
}
