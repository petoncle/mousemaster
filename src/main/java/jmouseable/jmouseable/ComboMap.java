package jmouseable.jmouseable;

import java.util.List;
import java.util.Map;

public class ComboMap {

    private final Map<Combo, List<Command>> commandsByCombo;

    public ComboMap(Map<Combo, List<Command>> commandsByCombo) {
        this.commandsByCombo = commandsByCombo;
    }
}
