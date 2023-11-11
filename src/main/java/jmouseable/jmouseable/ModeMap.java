package jmouseable.jmouseable;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ModeMap {

    private final Map<String, Mode> modeByName;
    private final Duration defaultComboBreakingTimeout;

    public ModeMap(Set<Mode> modes, Duration defaultComboBreakingTimeout) {
        this.modeByName =
                modes.stream().collect(Collectors.toMap(Mode::name, Function.identity()));
        this.defaultComboBreakingTimeout = defaultComboBreakingTimeout;
    }

    public Mode get(String modeName) {
        return modeByName.get(modeName);
    }

    public Duration defaultComboBreakingTimeout() {
        return defaultComboBreakingTimeout;
    }

}