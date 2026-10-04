package mousemaster;

public enum IndicatorPosition {
    MOUSE,
    MOUSE_TOP_LEFT,
    MOUSE_TOP_RIGHT,
    MOUSE_BOTTOM_LEFT,
    MOUSE_BOTTOM_RIGHT,
    SCREEN_CENTER;

    public static IndicatorPosition fromString(String value) {
        return switch (value) {
            case "mouse" -> MOUSE;
            case "mouse-top-left" -> MOUSE_TOP_LEFT;
            case "mouse-top-right" -> MOUSE_TOP_RIGHT;
            case "mouse-bottom-left" -> MOUSE_BOTTOM_LEFT;
            case "mouse-bottom-right" -> MOUSE_BOTTOM_RIGHT;
            case "screen-center" -> SCREEN_CENTER;
            case "center" -> throw new IllegalArgumentException(
                    "Invalid indicator position: center has been renamed mouse");
            case "top-left", "top-right", "bottom-left", "bottom-right" ->
                    throw new IllegalArgumentException(
                            "Invalid indicator position: " + value + " has been renamed mouse-" +
                            value);
            default -> throw new IllegalArgumentException(
                    "Invalid indicator position: " + value +
                    ", must be 'mouse', 'mouse-top-left', 'mouse-top-right', 'mouse-bottom-left', 'mouse-bottom-right', or 'screen-center'");
        };
    }
}
