package mousemaster;

public record Configuration(KeyboardLayout keyboardLayout,
                            int maxPositionHistorySize, ModeMap modeMap,
                            String logLevel, boolean logRedactKeys, boolean logToFile) {

}
