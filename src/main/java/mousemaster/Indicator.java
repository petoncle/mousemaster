package mousemaster;

public record Indicator(int size, int edgeCount, String hexColor, double opacity,
                        double outlineThickness, String outlineHexColor,
                        double outlineOpacity, Shadow shadow) {
}
