package mousemaster;

import java.util.Map;
import java.util.Set;

public record Configuration(Map<String, Integer> maxPositionHistorySizeByName,
                            ModeMap modeMap, String logLevel,
                            boolean logRedactKeys, boolean logToFile,
                            boolean hideConsole, KeyboardLayout forcedActiveKeyboardLayout,
                            Set<String> initiallySetVariables, Set<Key> virtualKeys,
                            Set<Key> initiallyPressedVirtualKeys) {


}
