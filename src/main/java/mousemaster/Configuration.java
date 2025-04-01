package mousemaster;

public record Configuration(int maxPositionHistorySize, ModeMap modeMap, String logLevel,
                            boolean logRedactKeys, boolean logToFile,
                            KeyboardLayout configurationKeyboardLayout) {


}
