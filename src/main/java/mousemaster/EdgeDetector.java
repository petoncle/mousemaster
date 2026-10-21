package mousemaster;

import java.util.stream.IntStream;

/**
 * Edges against background: the ridge of a Sobel gradient after Canny, dilated into a
 * closed outline after Serra. Canny blurs first to suppress sensor noise; a drawn screen
 * has none.
 */
class EdgeDetector {

    private static final float contrast = 2;
    /** Light on white and dark on black both clamp away about the first of these. */
    static final float[] contrastMidpoints = {0.5f, 0.75f, 0.2f};

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
        boolean[] suppressed = nonMaximumSuppression(magnitude, direction, width, height);
        return Dilation.dilated(suppressed, width, height, dilation, verticalDilation);
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
        if (width < 3)
            return;
        IntStream.range(1, height - 1).parallel().forEach(y -> {
            int top = (y - 1) * width;
            int row = y * width;
            int bottom = (y + 1) * width;
            // Each column smoothed and differenced once, as the window slides along the row.
            int leftSum = luminance[top] + 2 * luminance[row] + luminance[bottom];
            int leftDifference = luminance[bottom] - luminance[top];
            int sum = luminance[top + 1] + 2 * luminance[row + 1] + luminance[bottom + 1];
            int difference = luminance[bottom + 1] - luminance[top + 1];
            for (int x = 1; x < width - 1; x++) {
                int rightSum = luminance[top + x + 1] + 2 * luminance[row + x + 1]
                               + luminance[bottom + x + 1];
                int rightDifference = luminance[bottom + x + 1] - luminance[top + x + 1];
                int horizontal = rightSum - leftSum;
                int vertical = leftDifference + 2 * difference + rightDifference;
                int pixel = row + x;
                magnitude[pixel] = (short) (Math.abs(horizontal) + Math.abs(vertical));
                if (magnitude[pixel] > edgeThreshold)
                    direction[pixel] = direction(horizontal, vertical);
                leftSum = sum;
                sum = rightSum;
                leftDifference = difference;
                difference = rightDifference;
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

    private static boolean[] nonMaximumSuppression(short[] magnitude, byte[] direction,
                                                   int width, int height) {
        boolean[] suppressed = new boolean[magnitude.length];
        IntStream.range(1, height - 1).parallel().forEach(y -> {
            for (int x = 1; x < width - 1; x++) {
                int pixel = y * width + x;
                short value = magnitude[pixel];
                if (value <= edgeThreshold)
                    continue;
                int along = alongGradientY[direction[pixel]] * width
                            + alongGradientX[direction[pixel]];
                suppressed[pixel] = value >= magnitude[pixel + along]
                                    && value >= magnitude[pixel - along];
            }
        });
        return suppressed;
    }

}
