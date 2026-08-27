package mousemaster;

import java.util.stream.IntStream;

/**
 * Edges against background: the ridge of a Sobel gradient after Canny, dilated into a
 * closed outline after Serra. Canny blurs first to suppress sensor noise; a drawn screen
 * has none.
 */
class EdgeDetector {

    private static final float contrast = 2;
    /** Light edges on a white pane clamp away about the first of these. */
    static final float[] contrastMidpoints = {0.5f, 0.75f};
    private static final int[][] stretched = stretched();
    /** Axis aligned within half of a 45 degree bin. */
    private static final float axisAlignedGradientRatio =
            (float) (1 / Math.tan(Math.toRadians(45 / 2.0)));

    /** Where suppression steps per bin. Bins 1 and 3 step across their gradient, leaving
     *  diagonals double thick; along it would be {0, 1, 1, 1} and {1, 1, 0, -1}. */
    private static final int[] alongGradientX = {0, 1, 1, -1};
    private static final int[] alongGradientY = {1, -1, 0, -1};

    /** A quarter of the 3060 a Sobel step reaches from black to white. */
    private static final int edgeThreshold = 765;

    static boolean[] edges(byte[] rgb, int width, int height, int dilation,
                           int verticalDilation, int midpoint) {
        short[] luminance = luminance(rgb, width, height, stretched[midpoint]);
        short[] magnitude = new short[width * height];
        byte[] direction = new byte[width * height];
        sobel(luminance, width, height, magnitude, direction);
        short[] suppressed = nonMaximumSuppression(magnitude, direction, width, height);
        boolean[] thresholded = threshold(suppressed, width, height);
        return Dilation.dilated(thresholded, width, height, dilation, verticalDilation);
    }

    /** The unweighted channel sum, in 0..765: brightness, not a perceptual luma. */
    private static short[] luminance(byte[] rgb, int width, int height,
                                     int[] stretched) {
        short[] luminance = new short[width * height];
        IntStream.range(0, height).parallel().forEach(y -> {
            for (int x = 0; x < width; x++) {
                int pixel = y * width + x;
                int channel = pixel * 3;
                luminance[pixel] = (short) (stretched[rgb[channel] & 0xff] +
                                            stretched[rgb[channel + 1] & 0xff] +
                                            stretched[rgb[channel + 2] & 0xff]);
            }
        });
        return luminance;
    }

    private static int[][] stretched() {
        int[][] levels = new int[contrastMidpoints.length][256];
        for (int midpoint = 0; midpoint < levels.length; midpoint++)
            for (int level = 0; level < levels[midpoint].length; level++)
                levels[midpoint][level] = Math.round(255 * Math.clamp(
                        (level / 255f - contrastMidpoints[midpoint]) * contrast
                        + contrastMidpoints[midpoint], 0, 1));
        return levels;
    }

    private static void sobel(short[] luminance, int width, int height, short[] magnitude,
                              byte[] direction) {
        IntStream.range(1, height - 1).parallel().forEach(y -> {
            for (int x = 1; x < width - 1; x++) {
                int pixel = y * width + x;
                int topLeft = luminance[pixel - width - 1];
                int top = luminance[pixel - width];
                int topRight = luminance[pixel - width + 1];
                int left = luminance[pixel - 1];
                int right = luminance[pixel + 1];
                int bottomLeft = luminance[pixel + width - 1];
                int bottom = luminance[pixel + width];
                int bottomRight = luminance[pixel + width + 1];
                int horizontal = topRight - topLeft + 2 * (right - left) + bottomRight -
                                 bottomLeft;
                int vertical = bottomLeft - topLeft + 2 * (bottom - top) + bottomRight -
                               topRight;
                magnitude[pixel] = (short) (Math.abs(horizontal) + Math.abs(vertical));
                direction[pixel] = direction(horizontal, vertical);
            }
        });
    }

    private static byte direction(int horizontal, int vertical) {
        int absoluteHorizontal = Math.abs(horizontal);
        int absoluteVertical = Math.abs(vertical);
        if (axisAlignedGradientRatio * absoluteHorizontal < absoluteVertical)
            return 0;
        if (axisAlignedGradientRatio * absoluteVertical < absoluteHorizontal)
            return 2;
        return (byte) (horizontal * vertical > 0 ? 1 : 3);
    }

    private static short[] nonMaximumSuppression(short[] magnitude, byte[] direction,
                                                 int width, int height) {
        short[] suppressed = new short[magnitude.length];
        IntStream.range(0, height).parallel().forEach(y -> {
            for (int x = 0; x < width; x++) {
                int pixel = y * width + x;
                short value = magnitude[pixel];
                if (value == 0)
                    continue;
                int offsetX = alongGradientX[direction[pixel]];
                int offsetY = alongGradientY[direction[pixel]];
                short forward = magnitude[clamped(x + offsetX, width) +
                                          clamped(y + offsetY, height) * width];
                short backward = magnitude[clamped(x - offsetX, width) +
                                           clamped(y - offsetY, height) * width];
                if (value >= forward && value >= backward)
                    suppressed[pixel] = value;
            }
        });
        return suppressed;
    }

    private static int clamped(int coordinate, int size) {
        return Math.clamp(coordinate, 0, size - 1);
    }

    private static boolean[] threshold(short[] suppressed, int width, int height) {
        boolean[] thresholded = new boolean[suppressed.length];
        IntStream.range(0, height).parallel().forEach(y -> {
            for (int x = 0; x < width; x++) {
                int pixel = y * width + x;
                thresholded[pixel] = suppressed[pixel] > edgeThreshold;
            }
        });
        return thresholded;
    }

}
