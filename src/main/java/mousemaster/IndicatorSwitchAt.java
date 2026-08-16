package mousemaster;

/** When a transition switches the values it cannot interpolate, like the colors. */
public enum IndicatorSwitchAt {
    START,
    END;

    public static IndicatorSwitchAt fromString(String value) {
        return switch (value) {
            case "start" -> START;
            case "end" -> END;
            default -> throw new IllegalArgumentException(
                    "Invalid indicator switch at: " + value + ", must be 'start' or 'end'");
        };
    }
}
