package mousemaster;

/** When the colors of the indicator being transitioned to replace the previous ones. */
public enum IndicatorColorChange {
    IMMEDIATE,
    AT_END;

    public static IndicatorColorChange fromString(String value) {
        return switch (value) {
            case "immediate" -> IMMEDIATE;
            case "at-end" -> AT_END;
            default -> throw new IllegalArgumentException(
                    "Invalid indicator color change: " + value +
                    ", must be 'immediate' or 'at-end'");
        };
    }
}
