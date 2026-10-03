package mousemaster;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The table of animatable effect layer properties. One entry declares everything
 * the parser, the inheritance and the keyframe interpolation need to know about a
 * property: its configuration key, its kind (how it is parsed and interpolated), its
 * default and its range. Adding a property is one line here plus its use in the
 * renderer; it is then accepted as a layer base value ({@code layerN-<key>=}), as a
 * keyframe token ({@code <key>=<value>}) and inherited through property references,
 * with nothing else to write.
 *
 * <p>Numbers interpolate linearly between the keyframes that mention them (shaped by
 * the segment's easing), colors interpolate in OkLab, and switches hold their latest
 * value. A null default means "not set": the renderer picks the fallback (the pivot
 * falls back to the layer's own center).
 */
public enum EffectProperty {

    X("x", Kind.NUMBER, 0d, -10_000, 10_000, "the layer's horizontal offset from the area center, in pixels (negative = left)", "x=20"),
    Y("y", Kind.NUMBER, 0d, -10_000, 10_000, "the layer's vertical offset from the area center, in pixels (negative = up)", "y=-20"),
    /** Set through {@code size=<n>} or {@code size=<w>x<h>}, never by its own key. */
    WIDTH("width", Kind.NUMBER, 16d, 0, 10_000, "the layer's width, set through size", "size=24 or size=64x32"),
    HEIGHT("height", Kind.NUMBER, 16d, 0, 10_000, "the layer's height, set through size", "size=24 or size=64x32"),
    /** Multiplies the size (not the thickness): 0.2 -> 1 grows a layer in from afar. */
    SCALE("scale", Kind.NUMBER, 1d, 0, 100, "a multiplier on the size (1 = as written, 2 = twice as big, 0.5 = half)", "scale=1.5"),
    /** Degrees, clockwise, about the pivot. */
    ROTATION("rotation", Kind.NUMBER, 0d, -100_000, 100_000, "the layer's rotation in degrees, clockwise, about its pivot", "rotation=45"),
    /** 3D tilt about the layer's own horizontal axis, in degrees. */
    ROTATION_X("rotation-x", Kind.NUMBER, 0d, -100_000, 100_000, "a 3D tilt about the layer's horizontal axis, in degrees (180 flips it upside down)", "rotation-x=60"),
    /** 3D tilt about the layer's own vertical axis, in degrees. */
    ROTATION_Y("rotation-y", Kind.NUMBER, 0d, -100_000, 100_000, "a 3D tilt about the layer's vertical axis, in degrees (180 flips it like a card)", "rotation-y=180"),
    /** Set through {@code pivot=<x>,<y>} (effect coordinates); unset = the layer's center. */
    PIVOT_X("pivot-x", Kind.NUMBER, null, -10_000, 10_000, "the point the rotation turns about, set through pivot", "pivot=0,0"),
    PIVOT_Y("pivot-y", Kind.NUMBER, null, -10_000, 10_000, "the point the rotation turns about, set through pivot", "pivot=0,0"),
    OPACITY("opacity", Kind.NUMBER, 1d, 0, 1, "how visible the layer is: 1 fully visible, 0 invisible, 0.5 half transparent", "opacity=0.5"),
    /** Outline width in pixels, for outlined shapes, lines and crosses. */
    THICKNESS("thickness", Kind.NUMBER, 1d, 0, 1_000, "the width of an outline, line or cross, in pixels", "thickness=2"),
    /** Square corners, in pixels. */
    CORNER_RADIUS("corner-radius", Kind.NUMBER, 0d, 0, 10_000, "how rounded a rect's corners (or a text background's) are, in pixels (0 = sharp)", "corner-radius=4"),
    /** Polygon edges, with the indicator's convention: 3 triangle, 4 square, 6 hexagon, 100+ circle. */
    EDGE_COUNT("edge-count", Kind.NUMBER, 6d, 3, 1_000, "the number of sides of a polygon (3 triangle, 4 square, 6 hexagon, 100 or more looks like a circle) or of points of a star", "edge-count=6"),
    /** Arc start, in degrees: 0 is 12 o'clock, 90 is 3 o'clock, clockwise (like the indicator's fill start angle). */
    ARC_START("arc-start", Kind.NUMBER, 0d, -100_000, 100_000, "where an arc begins, in degrees clockwise from 12 o'clock (90 = 3 o'clock)", "arc-start=90"),
    /** Arc length in degrees, clockwise; negative goes counterclockwise. */
    ARC_LENGTH("arc-length", Kind.NUMBER, 270d, -360, 360, "how far an arc goes from arc-start, in degrees clockwise (360 = full circle, negative = counterclockwise)", "arc-length=270"),
    /** Set through {@code dash=<on>,<off>} in pixels; a zero length means a solid line. */
    DASH_LENGTH("dash-length", Kind.NUMBER, 0d, 0, 10_000, "the length of each dash, set through dash", "dash=6,4"),
    DASH_GAP("dash-gap", Kind.NUMBER, 0d, 0, 10_000, "the gap between dashes, set through dash", "dash=6,4"),
    /** Where the dash pattern starts along the outline, in pixels: animate it to make the dashes travel. */
    DASH_OFFSET("dash-offset", Kind.NUMBER, 0d, -1_000_000, 1_000_000, "how far along the outline the dash pattern starts, in pixels: animating it makes the dashes travel", "dash-offset=10"),
    COLOR("color", Kind.COLOR, "#FFFFFF", 0, 0, "the layer's color (of a text layer, the text's)", "color=#96A8FF"),
    /** Text layers: the font size in points. */
    FONT_SIZE("font-size", Kind.NUMBER, 12d, 1, 1_000, "a text layer's font size, in points", "font-size=11"),
    /** Text layers: a box behind the text (rounded by corner-radius, grown by padding); unset = none. */
    BACKGROUND_COLOR("background-color", Kind.COLOR, null, 0, 0, "the color of a box drawn behind a text layer (none unless set)", "background-color=#202020"),
    /** Text layers: an outline around the glyphs (like the indicator's outline-color); unset = none. */
    OUTLINE_COLOR("outline-color", Kind.COLOR, null, 0, 0, "the color of an outline drawn around a text layer's letters (none unless set)", "outline-color=#000000"),
    /** Text layers: the outline's width, in pixels. */
    OUTLINE_THICKNESS("outline-thickness", Kind.NUMBER, 1d, 0, 1_000, "the width of a text layer's outline, in pixels", "outline-thickness=2"),
    /** Text layers: the space between the text and its background box, in pixels. */
    PADDING("padding", Kind.NUMBER, 4d, 0, 1_000, "the space between a text layer and its background box, in pixels", "padding=5"),
    /** Written as the bare keyframe keywords {@code show} and {@code hide}. */
    VISIBLE("visible", Kind.SWITCH, true, 0, 0, "whether the layer is drawn, set with the keyframe keywords show and hide", "50 hide");

    public enum Kind {
        /** A double; interpolates linearly. */
        NUMBER,
        /** A #RRGGBB color; interpolates in OkLab. */
        COLOR,
        /** A boolean; holds its latest value. */
        SWITCH
    }

    private static final Map<String, EffectProperty> propertyByKey = new LinkedHashMap<>();

    static {
        for (EffectProperty property : values())
            propertyByKey.put(property.key, property);
    }

    public final String key;
    public final Kind kind;
    /** The layer's value when nothing sets it; null means unset. */
    public final Object defaultValue;
    public final double min;
    public final double max;
    /** What the property means, in plain words, for error messages: "opacity is <meaning>". */
    public final String meaning;
    /** A correct way to write it, for error messages. */
    public final String example;

    EffectProperty(String key, Kind kind, Object defaultValue, double min, double max,
                   String meaning, String example) {
        this.key = key;
        this.kind = kind;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.meaning = meaning;
        this.example = example;
    }

    /** The property written with the given key, or null (compound keys like size are not here). */
    public static EffectProperty byKey(String key) {
        return propertyByKey.get(key);
    }

    /** The keys a user can write, for error messages. */
    public static String keys() {
        StringBuilder keys = new StringBuilder();
        for (EffectProperty property : values()) {
            if (property == WIDTH || property == HEIGHT || property == PIVOT_X ||
                property == PIVOT_Y || property == DASH_LENGTH || property == DASH_GAP ||
                property == VISIBLE)
                continue;
            if (!keys.isEmpty())
                keys.append(", ");
            keys.append(property.key);
        }
        return keys + ", size, pivot, dash";
    }

    public static double number(Map<EffectProperty, Object> values, EffectProperty property,
                                double fallback) {
        Object value = values.get(property);
        return value == null ? fallback : (Double) value;
    }

}
