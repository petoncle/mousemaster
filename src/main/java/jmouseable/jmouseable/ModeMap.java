package jmouseable.jmouseable;

import java.util.Map;

public class ModeMap {

    private final Map<Mode, ComboMap> comboMapByMode;

    public ModeMap(Map<Mode, ComboMap> comboMapByMode) {
        this.comboMapByMode = comboMapByMode;
    }

    public ComboMap get(Mode mode) {
        return comboMapByMode.get(mode);
    }
}