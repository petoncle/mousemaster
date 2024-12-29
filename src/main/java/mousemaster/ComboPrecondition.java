package mousemaster;

import java.util.Set;
import java.util.stream.Collectors;

public record ComboPrecondition(ComboKeyPrecondition keyPrecondition,
                                ComboAppPrecondition appPrecondition) {

    public boolean isEmpty() {
        return keyPrecondition.isEmpty() && appPrecondition.isEmpty();
    }

    public record ComboKeyPrecondition(Set<Key> unpressedKeySet,
                                       Set<Set<Key>> pressedKeySets) {

        public boolean isEmpty() {
            return unpressedKeySet.isEmpty() &&
                   pressedKeySets.isEmpty();
        }

        public boolean satisfied(Set<Key> currentlyPressedKeys) {
            for (Key unpressedKey : unpressedKeySet) {
                if (currentlyPressedKeys.contains(unpressedKey))
                    return false;
            }
            if (pressedKeySets.isEmpty())
                return true;
            for (Set<Key> pressedKeySet : pressedKeySets) {
                if (currentlyPressedKeys.containsAll(pressedKeySet))
                    return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return String.join(" ",
                    "^{" + keySetToString(unpressedKeySet) + "}",
                    "_{" + keySetsToString(pressedKeySets) + "}");
        }

    }

    public record ComboAppPrecondition(Set<App> mustNotBeActiveApps,
                                       Set<App> mustBeActiveApps) {

        public boolean isEmpty() {
            return mustNotBeActiveApps.isEmpty() && mustBeActiveApps.isEmpty();
        }

        public boolean satisfied(App activeApp) {
            if (activeApp == null) {
                return isEmpty();
            }
            if (mustNotBeActiveApps.contains(activeApp))
                return false;
            if (!mustBeActiveApps.isEmpty() && !mustBeActiveApps.contains(activeApp))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return String.join(" ",
                    "^{" + mustNotBeActiveApps.stream()
                                              .map(App::executableName)
                                              .collect(Collectors.joining(" ")) + "}",
                    "_{" + mustNotBeActiveApps.stream()
                                              .map(App::executableName)
                                              .collect(Collectors.joining("|")) + "}");
        }
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
