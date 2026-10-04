package mousemaster;

import java.util.List;

public enum TextFormat {

    PLAIN("plain"),
    RICH("rich");

    private final String name;

    TextFormat(String name) {
        this.name = name;
    }

    private static final List<TextFormat> values = List.of(values());

    public static TextFormat of(String name) {
        return values.stream()
                     .filter(a -> a.name.equals(name))
                     .findFirst()
                     .orElseThrow(() -> new IllegalArgumentException(
                             "Invalid text format " + name +
                             ": it should be one of " +
                             values.stream().map(a -> a.name).toList()));
    }

}
