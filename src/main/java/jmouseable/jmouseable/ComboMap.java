package jmouseable.jmouseable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ComboMap(Map<Combo, List<Command>> commandsByCombo) {

    public static class ComboMapBuilder {

        private final Map<Combo, List<Command>> commandsByCombo = new HashMap<>();

        public ComboMapBuilder add(Combo combo, Command command) {
            commandsByCombo.computeIfAbsent(combo, combo1 -> new ArrayList<>())
                           .add(command);
            return this;
        }

        public Map<Combo, List<Command>> commandsByCombo() {
            return commandsByCombo;
        }

        public ComboMap build() {
            return new ComboMap(Map.copyOf(commandsByCombo));
        }

    }

}
