package mousemaster;

import java.util.Map;
import java.util.Set;

public record Configuration(
        Map<String, PositionHistoryConfiguration> positionHistoryConfigurationByName,
        Map<String, AnimationConfiguration> animationConfigurationByName,
        ModeMap modeMap, String logLevel, KeyRedaction keyRedaction,
        boolean logLastKeyEventsOnExit, boolean logToFile,
        boolean hideConsole, KeyboardLayout activeKeyboardLayout,
        KeyboardLayout forcedActiveKeyboardLayout,
        Set<String> initiallySetVariables, Set<Key> virtualKeys,
        Set<Key> initiallyPressedVirtualKeys) {


}
