package mousemaster;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record ComboPrecondition(ComboKeyPrecondition keyPrecondition,
                                ComboAppPrecondition appPrecondition) {

    public boolean isEmpty() {
        return keyPrecondition.isEmpty() && appPrecondition.isEmpty();
    }

    public record ComboKeyPrecondition(Set<Key> unpressedKeySet,
                                       PressedKeyPrecondition pressedKeyPrecondition) {

        public boolean isEmpty() {
            return unpressedKeySet.isEmpty() &&
                   pressedKeyPrecondition.isEmpty();
        }

        @Override
        public String toString() {
            return String.join(" ",
                    "^{" + keySetToString(unpressedKeySet) + "}",
                    "_{" + pressedKeyPrecondition + "}");
        }

    }

    /**
     * A pressed key precondition is a list of alternative groups.
     * At least one group must be satisfied.
     * Example: _{hint1key leftctrl | none | x*y}
     *   groups = [PressedKeyGroup([{a,b,...,z}, {leftctrl}]), PressedKeyGroup([]), PressedKeyGroup([{x_keys ∪ y_keys}])]
     */
    public record PressedKeyPrecondition(List<PressedKeyGroup> groups) {

        public boolean isEmpty() {
            return groups.isEmpty();
        }

        public Set<Key> allKeys() {
            return groups.stream()
                         .flatMap(g -> g.allKeys().stream())
                         .collect(Collectors.toSet());
        }

        @Override
        public String toString() {
            return groups.stream()
                         .map(PressedKeyGroup::toString)
                         .collect(Collectors.joining(" | "));
        }
    }

    /**
     * A pressed key group is a list of key sets.
     * For each key set, at least one key must be pressed.
     * All pressed candidate keys must belong to some key set.
     * Example: "hint1key leftctrl" → keySets = [{a,b,...,z}, {leftctrl}]
     */
    public record PressedKeyGroup(List<Set<Key>> keySets) {
        /**
         * Check if the candidate keys satisfy this group.
         * - All candidateKeys must be contained in the union of all keySets.
         * - For each keySet, at least one candidateKey must be present.
         */
        public boolean satisfiedBy(Set<Key> candidateKeys) {
            Set<Key> allKeys = allKeys();
            // All candidate keys must belong to some keySet.
            if (!allKeys.containsAll(candidateKeys))
                return false;
            // For each keySet, at least one candidate key must be present.
            for (Set<Key> keySet : keySets) {
                if (candidateKeys.stream().noneMatch(keySet::contains))
                    return false;
            }
            return true;
        }

        public Set<Key> allKeys() {
            return keySets.stream()
                          .flatMap(Set::stream)
                          .collect(Collectors.toSet());
        }

        @Override
        public String toString() {
            if (keySets.isEmpty())
                return "none";
            return keySets.stream()
                          .map(ks -> ks.stream()
                                       .map(Key::name)
                                       .collect(Collectors.joining("*")))
                          .collect(Collectors.joining(" "));
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
                    "_{" + mustBeActiveApps.stream()
                                           .map(App::executableName)
                                           .collect(Collectors.joining("|")) + "}");
        }
    }

    private static String keySetToString(Set<Key> keySet) {
        return keySet.stream().map(Key::name).collect(Collectors.joining(" "));
    }

}
