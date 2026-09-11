package mousemaster;

/**
 * The primitive shapes an effect layer can draw. Kept deliberately small: richer
 * visuals come from stacking layers and animating them with keyframes, not from
 * adding shapes. A shape reads the layer properties it needs (an arc its
 * {@code arc-start} and {@code arc-sweep}, a polygon its {@code edge-count}, a
 * rect its {@code corner-radius}, a star its {@code edge-count} as its number of points) and
 * ignores the rest.
 */
public enum EffectShape {

    /** An axis-aligned rectangle (a square when the size is one number); corner-radius rounds it. */
    RECT,
    /** A regular polygon with edge-count edges (3 triangle, 4 square, 6 hexagon, 100+ circle). */
    POLYGON,
    /** A horizontal line of the given length, centered; rotate to orient it. */
    LINE,
    /** A diagonal cross (×); rotate by 45 for a plus (+). */
    CROSS,
    /** A part of a circle, from arc-start over arc-sweep degrees; filled=true draws a pie slice. */
    ARC,
    /** A star with edge-count points (default 5), a point at the top; its inner radius is 0.4 of the size. */
    STAR,
    /** A line of text (layer<n>-text), in font-name / font-size / font-weight, with an optional background. */
    TEXT,
    // Aliases: the shapes below are shorthands for a polygon or a rect, kept because
    // they are what most effects are made of and read better than edge-count=100.
    /** Alias: a filled circle (a polygon of 100 edges, filled). */
    DOT,
    /** Alias: a circle outline (a polygon of 100 edges). */
    CIRCLE,
    /** Alias: an equilateral triangle, vertex at the top (a polygon of 3 edges). */
    TRIANGLE;

    public static String names() {
        return "rect, polygon, star, line, cross, arc, text, or the aliases dot, circle, triangle";
    }

    public static EffectShape parse(String string) {
        return switch (string) {
            case "rect" -> RECT;
            case "polygon" -> POLYGON;
            case "dot" -> DOT;
            case "circle" -> CIRCLE;
            case "triangle" -> TRIANGLE;
            case "line" -> LINE;
            case "cross" -> CROSS;
            case "arc" -> ARC;
            case "star" -> STAR;
            case "text" -> TEXT;
            default -> throw new IllegalArgumentException(
                    "Invalid shape value " + string + ": shape is what the layer draws; expected " +
                    "rect (a square or rectangle), polygon (edge-count sides), star (edge-count" +
                    " points), line, cross (an x), arc (part of a circle), text (letters, see" +
                    " layer1-text), or the shorthands" +
                    " dot (filled circle), circle, triangle, for example layer1-shape=circle");
        };
    }

}
