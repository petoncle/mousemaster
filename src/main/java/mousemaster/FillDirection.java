package mousemaster;

public enum FillDirection {
    CLOCKWISE,
    COUNTERCLOCKWISE,
    BOTH;

    public static FillDirection fromString(String value) {
        return switch (value) {
            case "clockwise" -> CLOCKWISE;
            case "counterclockwise" -> COUNTERCLOCKWISE;
            case "both" -> BOTH;
            default -> throw new IllegalArgumentException(
                    "Invalid fill direction: " + value +
                    ", must be 'clockwise', 'counterclockwise', or 'both'");
        };
    }
}
