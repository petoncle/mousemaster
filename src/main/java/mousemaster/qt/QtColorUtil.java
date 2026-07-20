package mousemaster.qt;

import io.qt.gui.QColor;
import mousemaster.Shadow;

public final class QtColorUtil {

    private QtColorUtil() {
    }

    public static QColor qColor(String hexColor, double opacity) {
        return QColor.fromRgba(rgba(hexColor, opacity));
    }

    public static QColor shadow(Shadow shadow) {
        return qColor(shadow.hexColor(), shadow.opacity());
    }

    public static int rgba(String hexColor, double opacity) {
        if (hexColor.startsWith("#"))
            hexColor = hexColor.substring(1);
        int colorInt = Integer.parseUnsignedInt(hexColor, 16);
        int red = (colorInt >> 16) & 0xFF;
        int green = (colorInt >> 8) & 0xFF;
        int blue = colorInt & 0xFF;
        int alpha = opacity > 0 ? Math.max(1, (int) (opacity * 255) & 0xFF) : 0;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    public static int rgb(String hexColor, double opacity) {
        // https://learn.microsoft.com/en-us/windows/win32/api/wingdi/ns-wingdi-blendfunction
        // Note that the APIs use premultiplied alpha, which means that the red, green
        // and blue channel values in the bitmap must be premultiplied with the alpha channel value.
        if (hexColor.startsWith("#"))
            hexColor = hexColor.substring(1);
        int colorInt = Integer.parseUnsignedInt(hexColor, 16);
        int red = (int) (((colorInt >> 16) & 0xFF) * opacity);
        int green = (int) (((colorInt >> 8) & 0xFF) * opacity);
        int blue = (int) ((colorInt & 0xFF) * opacity);
        return (red << 16) | (green << 8) | blue;
    }

    public static int alphaMultiplied(int color, double opacity) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        return ((int) Math.round(red * opacity) << 16) | ((int) Math.round(green * opacity) << 8) |
               (int) Math.round(blue * opacity);
    }

    // base is background, over is foreground.
    public static int blend(int base, int over, double overOpacity) {
        int red1 = (base >> 16) & 0xFF;
        int green1 = (base >> 8) & 0xFF;
        int blue1 = base & 0xFF;
        int red2 = (over >> 16) & 0xFF;
        int green2 = (over >> 8) & 0xFF;
        int blue2 = over & 0xFF;
        int blendedRed = (int) Math.round((red2 * overOpacity) + (red1 * (1 - overOpacity)));
        int blendedGreen = (int) Math.round((green2 * overOpacity) + (green1 * (1 - overOpacity)));
        int blendedBlue = (int) Math.round((blue2 * overOpacity) + (blue1 * (1 - overOpacity)));
        return (blendedRed << 16) | (blendedGreen << 8) | blendedBlue;
    }

    public static String blendOverWhite(String hexColor, double opacity) {
        if (hexColor.startsWith("#"))
            hexColor = hexColor.substring(1);
        int colorInt = Integer.parseUnsignedInt(hexColor, 16);
        int inputRed = (colorInt >> 16) & 0xFF;
        int inputGreen = (colorInt >> 8) & 0xFF;
        int inputBlue = colorInt & 0xFF;
        int whiteRed = 255;
        int whiteGreen = 255;
        int whiteBlue = 255;
        int blendedRed = (int) Math.round((inputRed * opacity) + (whiteRed * (1 - opacity)));
        int blendedGreen = (int) Math.round((inputGreen * opacity) + (whiteGreen * (1 - opacity)));
        int blendedBlue = (int) Math.round((inputBlue * opacity) + (whiteBlue * (1 - opacity)));
        return String.format("%02X%02X%02X", blendedRed, blendedGreen, blendedBlue);
    }
}
