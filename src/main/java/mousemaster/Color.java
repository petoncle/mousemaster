package mousemaster;

import java.util.Map;

public sealed interface Color permits GradientColor, Color.LastSelectedHintBoxColor {

    String lastSelectedHintBoxColor = "last-selected-hint-box-color";

    String hexColor(String lastSelectedHintBoxHexColor);

    static Color parse(String value, Map<String, GradientColor> colorAliases) {
        if (value.equals(lastSelectedHintBoxColor))
            return new LastSelectedHintBoxColor();
        return GradientColor.parse(value, colorAliases);
    }

    static Color parse(String value) {
        return parse(value, Map.of());
    }

    static int rgb(String hexColor) {
        return Integer.parseUnsignedInt(
                hexColor.startsWith("#") ? hexColor.substring(1) : hexColor, 16);
    }

    static String hexColor(int rgb) {
        return String.format("#%06X", rgb);
    }

    record LastSelectedHintBoxColor() implements Color {

        @Override
        public String hexColor(String lastSelectedHintBoxHexColor) {
            return lastSelectedHintBoxHexColor;
        }

        @Override
        public String toString() {
            return lastSelectedHintBoxColor;
        }

    }

}
