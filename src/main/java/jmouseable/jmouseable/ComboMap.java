package jmouseable.jmouseable;

import java.util.List;
import java.util.Map;

public record ComboMap(Map<Combo, List<Command>> commandsByCombo) {
}
