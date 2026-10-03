package mousemaster;

public enum IndicatorShape {
    CIRCLE,
    TRIANGLE,
    RECTANGLE,
    STAR,
    LINE,
    CROSS,
    PATH;

    public static IndicatorShape fromString(String value) {
        return switch (value) {
            case "circle" -> CIRCLE;
            case "triangle" -> TRIANGLE;
            case "rectangle" -> RECTANGLE;
            case "star" -> STAR;
            case "line" -> LINE;
            case "cross" -> CROSS;
            case "path" -> PATH;
            default -> throw new IllegalArgumentException(
                    "Invalid shape: " + value + ", must be 'circle', 'triangle', 'rectangle'," +
                    " 'star', 'line', 'cross' or 'path'");
        };
    }

    /** A partial stroke follows the closed outline around a shape. A line or a cross has
     *  none, so it is always stroked in full. */
    public boolean closed() {
        return this != LINE && this != CROSS;
    }
}
