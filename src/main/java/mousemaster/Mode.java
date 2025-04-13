package mousemaster;

public record Mode(String name, boolean stopCommandsFromPreviousMode,
                   boolean pushModeToHistoryStack,
                   String modeAfterUnhandledKeyPress,
                   ComboMap comboMap, Mouse mouse,
                   Wheel wheel, GridConfiguration grid, HintMeshConfiguration hintMesh,
                   ModeTimeout timeout, IndicatorConfiguration indicator,
                   HideCursor hideCursor, ZoomConfiguration zoom) {
    public static final String IDLE_MODE_NAME = "idle-mode";
    public static final String PREVIOUS_MODE_FROM_HISTORY_STACK_IDENTIFIER =
            "previous-mode-from-history-stack";

}
