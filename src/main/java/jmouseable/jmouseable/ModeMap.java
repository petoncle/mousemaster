package jmouseable.jmouseable;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ModeMap {

    private final Map<String, Mode> modeByName;

    public ModeMap(Set<Mode> modes) {
        this.modeByName =
                modes.stream().collect(Collectors.toMap(Mode::name, Function.identity()));
    }

    public Mode get(String modeName) {
        return modeByName.get(modeName);
    }
}