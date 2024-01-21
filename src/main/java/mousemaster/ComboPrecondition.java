package mousemaster;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record ComboPrecondition(Set<Key> mustRemainUnpressedKeySet,
                                Set<Set<Key>> mustRemainPressedKeySets) {

    public boolean isEmpty() {
        return mustRemainUnpressedKeySet.isEmpty() && mustRemainPressedKeySets.isEmpty();
    }

    public boolean satisfied(Set<Key> currentlyPressedKeys) {
        for (Key mustRemainUnpressedKey : mustRemainUnpressedKeySet) {
            if (currentlyPressedKeys.contains(mustRemainUnpressedKey))
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
        return String.join(" ", "^{" + keySetToString(mustRemainUnpressedKeySet) + "}",
                "_{" + keySetsToString(mustRemainPressedKeySets) + "}");
    }

    private static String keySetsToString(Set<Set<Key>> keySets) {
        return keySets.stream()
                      .map(ComboPrecondition::keySetToString)
                      .collect(Collectors.joining("|"));
    }

    private static String keySetToString(Set<Key> keySet) {
        return keySet.stream().map(Key::name).collect(Collectors.joining(" "));
    }
}
