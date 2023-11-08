package jmouseable.jmouseable;

import java.util.Map;

public class ModeMap {

    private final Map<Mode, ComboMap> comboMapByMode;

    public ModeMap(Map<Mode, ComboMap> comboMapByMode) {
        this.comboMapByMode = comboMapByMode;
    }
}