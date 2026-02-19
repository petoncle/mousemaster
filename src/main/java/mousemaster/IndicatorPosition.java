package mousemaster;

public enum IndicatorPosition {
    CENTER,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    public static IndicatorPosition fromString(String value) {
        return switch (value) {
            case "center" -> CENTER;
            case "top-left" -> TOP_LEFT;
            case "top-right" -> TOP_RIGHT;
            case "bottom-left" -> BOTTOM_LEFT;
            case "bottom-right" -> BOTTOM_RIGHT;
            default -> throw new IllegalArgumentException(
                    "Invalid indicator position: " + value +
                    ", must be 'center', 'top-left', 'top-right', 'bottom-left', or 'bottom-right'");
        };
    }
}
