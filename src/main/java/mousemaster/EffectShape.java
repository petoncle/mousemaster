package mousemaster;

/**
 * The primitive shapes an effect layer can draw. Kept deliberately small: richer
 * visuals come from stacking layers and animating them with keyframes, not from
 * adding shapes.
 */
public enum EffectShape {

    /** A filled circle. */
    DOT,
    /** A circle (outline by default, filled with filled=true). */
    CIRCLE,
    /** An axis-aligned square, or a rectangle when the size is WxH. */
    SQUARE,
    /** An equilateral triangle, vertex at the top. */
    TRIANGLE,
    /** A horizontal line of the given length, centered; rotate to orient it. */
    LINE,
    /** A diagonal cross (×); rotate by 45 for a plus (+). */
    CROSS;

    public static EffectShape parse(String string) {
        return switch (string) {
            case "dot" -> DOT;
            case "circle" -> CIRCLE;
            case "square" -> SQUARE;
            case "triangle" -> TRIANGLE;
            case "line" -> LINE;
            case "cross" -> CROSS;
            default -> throw new IllegalArgumentException(
                    "Invalid effect shape " + string + ": expected one of " +
                    "dot, circle, square, triangle, line, cross");
        };
    }

}
