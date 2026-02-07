package mousemaster;

public record Indicator(int size, int edgeCount, String hexColor, double opacity,
                        IndicatorOutline firstOutline, IndicatorOutline secondOutline,
                        Shadow shadow,
                        boolean labelEnabled, String labelText,
                        FontStyle labelFontStyle) {
}
