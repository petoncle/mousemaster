package mousemaster;

public sealed interface Color {

    String lastSelectedHintBoxColor = "last-selected-hint-box-color";

    String hexColor(String lastSelectedHintBoxHexColor);

    static Color parse(String value) {
        if (value.equals(lastSelectedHintBoxColor))
            return new LastSelectedHintBoxColor();
        if (!value.matches("^#?([a-fA-F0-9]{6})$"))
            throw new IllegalArgumentException(
                    "Invalid color " + value + ": a color should be in the #FFFFFF format or " +
                    lastSelectedHintBoxColor);
        return new HexColor(value.startsWith("#") ? value : "#" + value);
    }

    static int rgb(String hexColor) {
        return Integer.parseUnsignedInt(
                hexColor.startsWith("#") ? hexColor.substring(1) : hexColor, 16);
    }

    static String hexColor(int rgb) {
        return String.format("#%06X", rgb);
    }

    record HexColor(String hexColor) implements Color {

        @Override
        public String hexColor(String lastSelectedHintBoxHexColor) {
            return hexColor;
        }

        @Override
        public String toString() {
            return hexColor;
        }

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
