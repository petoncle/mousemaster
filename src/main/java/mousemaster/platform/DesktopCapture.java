package mousemaster.platform;

import mousemaster.Rectangle;
import mousemaster.qt.ImageChunks;

/**
 * @param scaledRgb the capture scaled for the detection model, three bytes per pixel
 */
public record DesktopCapture(Rectangle bounds, byte[] scaledRgb,
                             int scaledWidth, int scaledHeight) {

    /** The pixels of a captured frame, read where the frame is. */
    public interface BgraRows {
        /** Copies the B8G8R8A8 pixels of the rows from fromY until toY, in the columns from x
         *  until x + width, into band, one row after the other. */
        void read(int x, int width, int fromY, int toY, byte[] band);
    }

    /** Averages each source block of the captured rows into one rgb pixel. */
    public static byte[] boxDownscaledRgb(BgraRows rows, Rectangle bounds,
                                          int scaledWidth, int scaledHeight) {
        byte[] rgb = new byte[scaledWidth * scaledHeight * 3];
        int[] columns = new int[scaledWidth + 1];
        for (int scaledX = 0; scaledX <= scaledWidth; scaledX++)
            columns[scaledX] = scaledX * bounds.width() / scaledWidth;
        int bandPitch = bounds.width() * 4;
        int maxBandHeight = (bounds.height() + scaledHeight - 1) / scaledHeight;
        ImageChunks.overRange(scaledHeight, bounds.width() * maxBandHeight, (from, to) -> {
            byte[] band = new byte[maxBandHeight * bandPitch];
            for (int scaledY = from; scaledY < to; scaledY++) {
                int fromY = bounds.y() + scaledY * bounds.height() / scaledHeight;
                int bandHeight = Math.max(1,
                        bounds.y() + (scaledY + 1) * bounds.height() / scaledHeight - fromY);
                rows.read(bounds.x(), bounds.width(), fromY, fromY + bandHeight, band);
                for (int scaledX = 0; scaledX < scaledWidth; scaledX++) {
                    int fromX = columns[scaledX];
                    int toX = Math.max(fromX + 1, columns[scaledX + 1]);
                    int red = 0, green = 0, blue = 0;
                    if (bandHeight == 3 && toX - fromX == 3) {
                        // The 3 by 3 block of a scale of 3, its columns unrolled.
                        int sample = fromX * 4;
                        for (int y = 0; y < 3; y++, sample += bandPitch) {
                            blue += (band[sample] & 0xff) + (band[sample + 4] & 0xff)
                                    + (band[sample + 8] & 0xff);
                            green += (band[sample + 1] & 0xff) + (band[sample + 5] & 0xff)
                                     + (band[sample + 9] & 0xff);
                            red += (band[sample + 2] & 0xff) + (band[sample + 6] & 0xff)
                                   + (band[sample + 10] & 0xff);
                        }
                    }
                    else {
                        for (int y = 0; y < bandHeight; y++) {
                            int row = y * bandPitch;
                            for (int x = fromX; x < toX; x++) {
                                int sample = row + x * 4;
                                blue += band[sample] & 0xff;
                                green += band[sample + 1] & 0xff;
                                red += band[sample + 2] & 0xff;
                            }
                        }
                    }
                    // A division by multiplication, exact for any sum under 2^32 / samples.
                    long inverse = (1L << 32) / (bandHeight * (toX - fromX)) + 1;
                    int scaledPixel = (scaledY * scaledWidth + scaledX) * 3;
                    rgb[scaledPixel] = (byte) (red * inverse >>> 32);
                    rgb[scaledPixel + 1] = (byte) (green * inverse >>> 32);
                    rgb[scaledPixel + 2] = (byte) (blue * inverse >>> 32);
                }
            }
        });
        return rgb;
    }

}
