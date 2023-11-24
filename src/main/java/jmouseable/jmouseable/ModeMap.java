package jmouseable.jmouseable;

import java.util.Collection;
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

    public Collection<Mode> modes() {
        return modeByName.values();
    }

    public Mode get(String modeName) {
        return modeByName.get(modeName);
    }

    @Override
    public String toString() {
        return modeByName.toString();
    }
}