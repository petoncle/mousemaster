package mousemaster.platform;

import mousemaster.Rectangle;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class DesktopCaptureTest {

    private static final int width = 29, height = 23;
    /** Padded, as a mapped texture's rows can be. */
    private static final int rowPitch = width * 4 + 8;

    private static byte[] frame() {
        byte[] bgra = new byte[rowPitch * height];
        new Random(7).nextBytes(bgra);
        return bgra;
    }

    /** Each scaled pixel is the floor of the average of its source block. */
    private static byte[] averagedRgb(byte[] bgra, Rectangle bounds, int scaledWidth,
                                      int scaledHeight) {
        byte[] rgb = new byte[scaledWidth * scaledHeight * 3];
        for (int scaledY = 0; scaledY < scaledHeight; scaledY++) {
            int fromY = bounds.y() + scaledY * bounds.height() / scaledHeight;
            int toY = Math.max(fromY + 1,
                    bounds.y() + (scaledY + 1) * bounds.height() / scaledHeight);
            for (int scaledX = 0; scaledX < scaledWidth; scaledX++) {
                int fromX = bounds.x() + scaledX * bounds.width() / scaledWidth;
                int toX = Math.max(fromX + 1,
                        bounds.x() + (scaledX + 1) * bounds.width() / scaledWidth);
                int[] sums = new int[3];
                for (int y = fromY; y < toY; y++)
                    for (int x = fromX; x < toX; x++)
                        for (int channel = 0; channel < 3; channel++)
                            sums[channel] += bgra[y * rowPitch + x * 4 + 2 - channel] & 0xff;
                int samples = (toY - fromY) * (toX - fromX);
                for (int channel = 0; channel < 3; channel++)
                    rgb[(scaledY * scaledWidth + scaledX) * 3 + channel] =
                            (byte) (sums[channel] / samples);
            }
        }
        return rgb;
    }

    private static void assertAveraged(Rectangle bounds, int scaledWidth, int scaledHeight) {
        byte[] bgra = frame();
        assertArrayEquals(averagedRgb(bgra, bounds, scaledWidth, scaledHeight),
                DesktopCapture.boxDownscaledRgb((x, width, fromY, toY, band) -> {
                    for (int y = fromY; y < toY; y++)
                        System.arraycopy(bgra, y * rowPitch + x * 4, band,
                                (y - fromY) * width * 4, width * 4);
                }, bounds, scaledWidth, scaledHeight));
    }

    @Test
    void blocksOfThreeByThree() {
        assertAveraged(new Rectangle(2, 3, 27, 18), 9, 6);
    }

    @Test
    void unevenBlocks() {
        assertAveraged(new Rectangle(1, 1, 25, 20), 7, 6);
    }

    @Test
    void wholeFrame() {
        assertAveraged(new Rectangle(0, 0, width, height), 10, 8);
    }

    @Test
    void moreScaledPixelsThanSourcePixels() {
        assertAveraged(new Rectangle(3, 2, 5, 4), 12, 9);
    }
}
