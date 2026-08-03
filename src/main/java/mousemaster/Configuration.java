package mousemaster;

import java.util.Set;

public record Configuration(int maxPositionHistorySize, ModeMap modeMap, String logLevel,
                            boolean logRedactKeys, boolean logToFile,
                            boolean hideConsole, KeyboardLayout forcedActiveKeyboardLayout,
                            Set<String> initiallySetVariables, Set<Key> virtualKeys,
                            Set<Key> initiallyPressedVirtualKeys) {


}
