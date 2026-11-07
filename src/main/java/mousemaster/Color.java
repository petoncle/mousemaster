package mousemaster;

import java.util.Map;

public sealed interface Color permits GradientColor, Color.LastSelectedHintBoxColor,
        Color.LastSelectedHintBoxCenterColor {

    String lastSelectedHintBoxColor = "last-selected-hint-box-color";
    String lastSelectedHintBoxCenterColor = "last-selected-hint-box-center-color";

    /** This color, once the colors of the hint selected last are known. */
    GradientColor gradientColor(LastSelectedHintColors lastSelectedHintColors);

    static Color parse(String value, Map<String, GradientColor> colorAliases) {
        return switch (value) {
            case lastSelectedHintBoxColor -> new LastSelectedHintBoxColor();
            case lastSelectedHintBoxCenterColor -> new LastSelectedHintBoxCenterColor();
            default -> GradientColor.parse(value, colorAliases);
        };
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

    /** The color of the box of the hint selected last, and its color at the center of the box. */
    record LastSelectedHintColors(GradientColor boxColor, GradientColor boxCenterColor) {
    }

    record LastSelectedHintBoxColor() implements Color {

        @Override
        public GradientColor gradientColor(LastSelectedHintColors lastSelectedHintColors) {
            return lastSelectedHintColors.boxColor();
        }

        @Override
        public String toString() {
            return lastSelectedHintBoxColor;
        }

    }

    record LastSelectedHintBoxCenterColor() implements Color {

        @Override
        public GradientColor gradientColor(LastSelectedHintColors lastSelectedHintColors) {
            return lastSelectedHintColors.boxCenterColor();
        }

        @Override
        public String toString() {
            return lastSelectedHintBoxCenterColor;
        }

    }

}
