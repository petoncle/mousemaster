package mousemaster;

public enum IndicatorShape {
    CIRCLE,
    TRIANGLE,
    RECTANGLE,
    STAR,
    LINE,
    CROSS,
    PATH,
    TEXT;

    public static IndicatorShape fromString(String value) {
        return switch (value) {
            case "circle" -> CIRCLE;
            case "triangle" -> TRIANGLE;
            case "rectangle" -> RECTANGLE;
            case "star" -> STAR;
            case "line" -> LINE;
            case "cross" -> CROSS;
            case "path" -> PATH;
            case "text" -> TEXT;
            default -> throw new IllegalArgumentException(
                    "Invalid shape: " + value + ", must be 'circle', 'triangle', 'rectangle'," +
                    " 'star', 'line', 'cross', 'path' or 'text'");
        };
    }

    /** A partial stroke follows the one closed outline around a shape. A line or a cross has
     *  no closed outline and text has one per letter, so they are always stroked in full. */
    public boolean hasOneOutline() {
        return this != LINE && this != CROSS && this != TEXT;
    }
}
