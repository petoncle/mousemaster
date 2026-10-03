package mousemaster;

public enum StrokeAnchor {
    START,
    MIDDLE;

    public static StrokeAnchor fromString(String value) {
        return switch (value) {
            case "start" -> START;
            case "middle" -> MIDDLE;
            default -> throw new IllegalArgumentException(
                    "Invalid stroke anchor: " + value + ", must be 'start' or 'middle'");
        };
    }
}
