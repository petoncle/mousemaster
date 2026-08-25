package mousemaster.platform;

import mousemaster.Rectangle;

import java.util.stream.IntStream;

/**
 * @param scaledRgb the capture scaled for the detection model, three bytes per pixel
 */
public record DesktopCapture(Rectangle bounds, byte[] scaledRgb,
                             int scaledWidth, int scaledHeight) {

    /** Averages each source block of the captured bgra into one rgb pixel. */
    public static byte[] boxDownscaledRgb(byte[] bgra, int rowPitch, Rectangle bounds,
                                          int scaledWidth, int scaledHeight) {
        byte[] rgb = new byte[scaledWidth * scaledHeight * 3];
        IntStream.range(0, scaledHeight).parallel().forEach(scaledY -> {
            int fromY = bounds.y() + scaledY * bounds.height() / scaledHeight;
            int toY = Math.max(fromY + 1,
                    bounds.y() + (scaledY + 1) * bounds.height() / scaledHeight);
            for (int scaledX = 0; scaledX < scaledWidth; scaledX++) {
                int fromX = bounds.x() + scaledX * bounds.width() / scaledWidth;
                int toX = Math.max(fromX + 1,
                        bounds.x() + (scaledX + 1) * bounds.width() / scaledWidth);
                int red = 0, green = 0, blue = 0, samples = 0;
                for (int y = fromY; y < toY; y++) {
                    int row = y * rowPitch;
                    for (int x = fromX; x < toX; x++) {
                        int sample = row + x * 4;
                        blue += bgra[sample] & 0xff;
                        green += bgra[sample + 1] & 0xff;
                        red += bgra[sample + 2] & 0xff;
                        samples++;
                    }
                }
                int scaledPixel = (scaledY * scaledWidth + scaledX) * 3;
                rgb[scaledPixel] = (byte) (red / samples);
                rgb[scaledPixel + 1] = (byte) (green / samples);
                rgb[scaledPixel + 2] = (byte) (blue / samples);
            }
        });
        return rgb;
    }

}
