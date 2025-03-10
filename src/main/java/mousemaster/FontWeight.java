package mousemaster;

import io.qt.gui.QFont;

import java.util.List;

public enum FontWeight {

    THIN("thin"),
    EXTRA_LIGHT("extra-light"),
    LIGHT("light"),
    NORMAL("normal"),
    MEDIUM("medium"),
    DEMI_BOLD("demi-bold"),
    BOLD("bold"),
    EXTRA_BOLD("extra-bold"),
    BLACK("black");

    private final String name;

    FontWeight(String name) {
        this.name = name;
    }

    private static final List<FontWeight> values = List.of(values());

    public static FontWeight of(String name) {
        return values.stream()
                     .filter(w -> w.name.equals(name))
                     .findFirst()
                     .orElseThrow(() -> new IllegalArgumentException(
                             "Invalid font weight " + name + ": font weight should be one of " +
                             values.stream()
                                   .map(fontWeight -> fontWeight.name)
                                   .toList()));
    }

    public QFont.Weight qtWeight() {
        return switch (this) {
            case THIN -> QFont.Weight.Thin;
            case EXTRA_LIGHT -> QFont.Weight.ExtraLight;
            case LIGHT -> QFont.Weight.Light;
            case NORMAL -> QFont.Weight.Normal;
            case MEDIUM -> QFont.Weight.Medium;
            case DEMI_BOLD -> QFont.Weight.DemiBold;
            case BOLD -> QFont.Weight.Bold;
            case EXTRA_BOLD -> QFont.Weight.ExtraBold;
            case BLACK -> QFont.Weight.Black;
        };
    }

}
