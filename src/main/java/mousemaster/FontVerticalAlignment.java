package mousemaster;

import java.util.List;

/**
 * How a label is positioned vertically within its box.
 * baseline: the glyph rests on the baseline like normal text (a dot/comma sits low).
 * middle: the glyph's tight bounding box is centered in the box.
 */
public enum FontVerticalAlignment {

    BASELINE("baseline"),
    MIDDLE("middle");

    private final String name;

    FontVerticalAlignment(String name) {
        this.name = name;
    }

    private static final List<FontVerticalAlignment> values = List.of(values());

    public static FontVerticalAlignment of(String name) {
        return values.stream()
                     .filter(a -> a.name.equals(name))
                     .findFirst()
                     .orElseThrow(() -> new IllegalArgumentException(
                             "Invalid font vertical alignment " + name +
                             ": it should be one of " +
                             values.stream().map(a -> a.name).toList()));
    }

}
