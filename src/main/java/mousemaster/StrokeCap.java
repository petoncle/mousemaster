package mousemaster;

public enum StrokeCap {
    FLAT,
    ROUND,
    SQUARE;

    public static StrokeCap fromString(String value) {
        return switch (value) {
            case "flat" -> FLAT;
            case "round" -> ROUND;
            case "square" -> SQUARE;
            default -> throw new IllegalArgumentException(
                    "Invalid stroke cap: " + value + ", must be 'flat', 'round' or 'square'");
        };
    }
}
