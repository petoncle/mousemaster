package mousemaster;

/**
 * The primitive shapes an effect layer can draw. Kept deliberately small: richer
 * visuals come from stacking layers and animating them with keyframes, not from
 * adding shapes. A shape reads the layer properties it needs (an arc its
 * {@code arc-start} and {@code arc-sweep}, a polygon its {@code edge-count}, a
 * square its {@code corner-radius}) and ignores the rest.
 */
public enum EffectShape {

    /** A filled circle. */
    DOT,
    /** A circle (outline by default, filled with filled=true). */
    CIRCLE,
    /** An axis-aligned square, or a rectangle when the size is WxH; corner-radius rounds it. */
    SQUARE,
    /** An equilateral triangle, vertex at the top. */
    TRIANGLE,
    /** A regular polygon with edge-count edges (3 triangle, 4 square, 6 hexagon, 100+ circle). */
    POLYGON,
    /** A horizontal line of the given length, centered; rotate to orient it. */
    LINE,
    /** A diagonal cross (×); rotate by 45 for a plus (+). */
    CROSS,
    /** A part of a circle, from arc-start over arc-sweep degrees; filled=true draws a pie slice. */
    ARC,
    /** A line of text (layer<n>-text), in font-name / font-size / font-weight, with an optional background. */
    TEXT;

    public static String names() {
        return "dot, circle, square, triangle, polygon, line, cross, arc, text";
    }

    public static EffectShape parse(String string) {
        return switch (string) {
            case "dot" -> DOT;
            case "circle" -> CIRCLE;
            case "square" -> SQUARE;
            case "triangle" -> TRIANGLE;
            case "polygon" -> POLYGON;
            case "line" -> LINE;
            case "cross" -> CROSS;
            case "arc" -> ARC;
            case "text" -> TEXT;
            default -> throw new IllegalArgumentException(
                    "Invalid effect shape " + string + ": expected one of " + names());
        };
    }

}
