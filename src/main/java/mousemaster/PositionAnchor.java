package mousemaster;

public enum PositionAnchor {
    POINT,
    HINT_BOX_EDGE,
    HINT_CELL_EDGE;

    public static PositionAnchor fromString(String value) {
        return switch (value) {
            case "point" -> POINT;
            case "hint-box-edge" -> HINT_BOX_EDGE;
            case "hint-cell-edge" -> HINT_CELL_EDGE;
            default -> throw new IllegalArgumentException(
                    "Invalid position anchor: " + value +
                    ", must be 'point', 'hint-box-edge' or 'hint-cell-edge'");
        };
    }
}
