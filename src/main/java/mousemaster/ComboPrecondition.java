package mousemaster;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record ComboPrecondition(ComboKeyPrecondition keyPrecondition,
                                ComboAppPrecondition appPrecondition,
                                ComboVariablePrecondition variablePrecondition) {

    public boolean isEmpty() {
        return keyPrecondition.isEmpty() && appPrecondition.isEmpty() &&
               variablePrecondition.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!keyPrecondition.isEmpty())
            sb.append(keyPrecondition);
        if (!appPrecondition.isEmpty()) {
            if (!sb.isEmpty())
                sb.append(" ");
            sb.append(appPrecondition);
        }
        if (!variablePrecondition.isEmpty()) {
            if (!sb.isEmpty())
                sb.append(" ");
            sb.append(variablePrecondition);
        }
        return sb.toString();
    }

    public record ComboKeyPrecondition(Set<Key> unpressedKeySet,
                                       PressedKeyPrecondition pressedKeyPrecondition) {

        public boolean isEmpty() {
            return unpressedKeySet.isEmpty() &&
                   pressedKeyPrecondition.isEmpty();
        }

        @Override
        public String toString() {
            String unpressed = unpressedKeySet.isEmpty() ? "" : "^{" + keySetToString(unpressedKeySet) + "}";
            String pressed = pressedKeyPrecondition.isEmpty() ? "" : "_{" + pressedKeyPrecondition + "}";
            if (unpressed.isEmpty())
                return pressed;
            if (pressed.isEmpty())
                return unpressed;
            return unpressed + " " + pressed;
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
            String notActive = mustNotBeActiveApps.isEmpty() ? "" :
                    "^{" + mustNotBeActiveApps.stream()
                                              .map(App::executableName)
                                              .collect(Collectors.joining(" ")) + "}";
            String active = mustBeActiveApps.isEmpty() ? "" :
                    "_{" + mustBeActiveApps.stream()
                                           .map(App::executableName)
                                           .collect(Collectors.joining("|")) + "}";
            if (notActive.isEmpty())
                return active;
            if (active.isEmpty())
                return notActive;
            return notActive + " " + active;
        }
    }

    /**
     * A variable precondition is a list of alternative groups.
     * At least one group must be satisfied (OR between groups).
     * Within a group, all conditions must be satisfied (AND).
     * Example: _{islclick | ismclick | isrclick}
     *   groups = [[islclick], [ismclick], [isrclick]]
     */
    public record ComboVariablePrecondition(List<List<VariableCondition>> groups) {

        public boolean isEmpty() {
            return groups.isEmpty();
        }

        public boolean satisfiedBy(Set<String> activeVariables) {
            if (groups.isEmpty())
                return true;
            for (List<VariableCondition> group : groups) {
                boolean groupSatisfied = true;
                for (VariableCondition condition : group) {
                    boolean isSet = activeVariables.contains(condition.variableName());
                    if (condition.negated() ? isSet : !isSet) {
                        groupSatisfied = false;
                        break;
                    }
                }
                if (groupSatisfied)
                    return true;
            }
            return false;
        }

        public List<VariableCondition> conditions() {
            return groups.stream().flatMap(List::stream).toList();
        }

        @Override
        public String toString() {
            return "_{" + groups.stream()
                    .map(group -> group.stream()
                            .map(c -> (c.negated() ? "!" : "") + c.variableName())
                            .collect(Collectors.joining(" ")))
                    .collect(Collectors.joining(" | ")) + "}";
        }

        public record VariableCondition(String variableName, boolean negated) {}
    }

    private static String keySetToString(Set<Key> keySet) {
        return keySet.stream().map(Key::name).collect(Collectors.joining(" "));
    }

}
