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
        int[] columns = new int[scaledWidth + 1];
        for (int scaledX = 0; scaledX <= scaledWidth; scaledX++)
            columns[scaledX] = bounds.x() + scaledX * bounds.width() / scaledWidth;
        IntStream.range(0, scaledHeight).parallel().forEach(scaledY -> {
            int fromY = bounds.y() + scaledY * bounds.height() / scaledHeight;
            int toY = Math.max(fromY + 1,
                    bounds.y() + (scaledY + 1) * bounds.height() / scaledHeight);
            for (int scaledX = 0; scaledX < scaledWidth; scaledX++) {
                int fromX = columns[scaledX];
                int toX = Math.max(fromX + 1, columns[scaledX + 1]);
                int red = 0, green = 0, blue = 0;
                if (toY - fromY == 3 && toX - fromX == 3) {
                    // The 3 by 3 block of a scale of 3, its columns unrolled.
                    int sample = fromY * rowPitch + fromX * 4;
                    for (int y = 0; y < 3; y++, sample += rowPitch) {
                        blue += (bgra[sample] & 0xff) + (bgra[sample + 4] & 0xff)
                                + (bgra[sample + 8] & 0xff);
                        green += (bgra[sample + 1] & 0xff) + (bgra[sample + 5] & 0xff)
                                 + (bgra[sample + 9] & 0xff);
                        red += (bgra[sample + 2] & 0xff) + (bgra[sample + 6] & 0xff)
                               + (bgra[sample + 10] & 0xff);
                    }
                }
                else {
                    for (int y = fromY; y < toY; y++) {
                        int row = y * rowPitch;
                        for (int x = fromX; x < toX; x++) {
                            int sample = row + x * 4;
                            blue += bgra[sample] & 0xff;
                            green += bgra[sample + 1] & 0xff;
                            red += bgra[sample + 2] & 0xff;
                        }
                    }
                }
                // A division by multiplication, exact for any sum under 2^32 / samples.
                long inverse = (1L << 32) / ((toY - fromY) * (toX - fromX)) + 1;
                int scaledPixel = (scaledY * scaledWidth + scaledX) * 3;
                rgb[scaledPixel] = (byte) (red * inverse >>> 32);
                rgb[scaledPixel + 1] = (byte) (green * inverse >>> 32);
                rgb[scaledPixel + 2] = (byte) (blue * inverse >>> 32);
            }
        });
        return rgb;
    }

}
