package mousemaster;

import java.util.Arrays;
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
    /** Rows a Sobel task slides its three luminance rows down. */
    private static final int bandRows = 16;
    /** Detection pixels down a column, past which an edge is a pane's border, not a
     *  control's outline. */
    private static final int paneBorderRows = 300;
    private static final int paneBorderSamples = 8;

    static long[] edges(byte[] rgb, int width, int height, int dilation,
                        int verticalDilation, int midpoint) {
        short[] magnitude = new short[width * height];
        byte[] direction = new byte[width * height];
        sobel(rgb, width, height, stretched[midpoint], magnitude, direction);
        long[] suppressed = nonMaximumSuppression(magnitude, direction, width, height);
        clearPaneBorders(suppressed, width, height);
        return Dilation.dilated(suppressed, width, height, dilation, verticalDilation);
    }

    /** Dilated, a pane's border would join whatever is beside it into one region. Its ridge
     *  wanders between two columns, so it is looked for a pixel wider. */
    private static void clearPaneBorders(long[] suppressed, int width, int height) {
        int words = Dilation.words(width);
        int length = Math.min(paneBorderRows, height / 2);
        long[] edges = suppressed.clone();
        // Only a column set at every sample spaced down a border's length can hold one.
        int spacing = Math.max(1, length / paneBorderSamples);
        long[] columns = new long[words];
        for (int y = 0; y + (paneBorderSamples - 1) * spacing < height; y += spacing)
            for (int word = 0; word < words; word++) {
                long bits = -1;
                for (int sample = 0; sample < paneBorderSamples; sample++) {
                    int row = (y + sample * spacing) * words;
                    bits &= Dilation.horizontally(edges, row, words, row + word, 1);
                }
                columns[word] |= bits;
            }
        int[] runs = new int[64];
        for (int word = 0; word < words; word++) {
            if (columns[word] == 0)
                continue;
            Arrays.fill(runs, 0);
            for (int y = 0; y <= height; y++) {
                long bits = y < height
                        ? Dilation.horizontally(edges, y * words, words, y * words + word, 1)
                        : 0;
                for (long remaining = columns[word]; remaining != 0;
                     remaining &= remaining - 1) {
                    int bit = Long.numberOfTrailingZeros(remaining);
                    if ((bits >>> bit & 1) != 0) {
                        runs[bit]++;
                        continue;
                    }
                    if (runs[bit] >= length)
                        for (int cleared = y - runs[bit]; cleared < y; cleared++)
                            suppressed[cleared * words + word] &= ~(1L << bit);
                    runs[bit] = 0;
                }
            }
        }
    }

    /** The unweighted channel sum, in 0..765: brightness, not a perceptual luma. */
    private static void luminance(byte[] rgb, int width, int y, int[] stretched,
                                  short[] luminance) {
        int channel = y * width * 3;
        for (int x = 0; x < width; x++, channel += 3)
            luminance[x] = (short) (stretched[rgb[channel] & 0xff] +
                                    stretched[rgb[channel + 1] & 0xff] +
                                    stretched[rgb[channel + 2] & 0xff]);
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

    private static void sobel(byte[] rgb, int width, int height, int[] stretched,
                              short[] magnitude, byte[] direction) {
        if (width < 3)
            return;
        int bands = (height - 2 + bandRows - 1) / bandRows;
        IntStream.range(0, bands).parallel().forEach(band -> {
            int from = 1 + band * bandRows;
            int to = Math.min(height - 1, from + bandRows);
            short[] top = new short[width];
            short[] row = new short[width];
            short[] bottom = new short[width];
            luminance(rgb, width, from - 1, stretched, top);
            luminance(rgb, width, from, stretched, row);
            for (int y = from; y < to; y++) {
                luminance(rgb, width, y + 1, stretched, bottom);
                sobel(top, row, bottom, width, magnitude, direction, y * width);
                short[] above = top;
                top = row;
                row = bottom;
                bottom = above;
            }
        });
    }

    private static void sobel(short[] top, short[] row, short[] bottom, int width,
                              short[] magnitude, byte[] direction, int start) {
        // Each column smoothed and differenced once, as the window slides along the row.
        int leftSum = top[0] + 2 * row[0] + bottom[0];
        int leftDifference = bottom[0] - top[0];
        int sum = top[1] + 2 * row[1] + bottom[1];
        int difference = bottom[1] - top[1];
        for (int x = 1; x < width - 1; x++) {
            int rightSum = top[x + 1] + 2 * row[x + 1] + bottom[x + 1];
            int rightDifference = bottom[x + 1] - top[x + 1];
            int horizontal = rightSum - leftSum;
            int vertical = leftDifference + 2 * difference + rightDifference;
            int pixel = start + x;
            magnitude[pixel] = (short) (Math.abs(horizontal) + Math.abs(vertical));
            if (magnitude[pixel] > edgeThreshold)
                direction[pixel] = direction(horizontal, vertical);
            leftSum = sum;
            sum = rightSum;
            leftDifference = difference;
            difference = rightDifference;
        }
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

    private static long[] nonMaximumSuppression(short[] magnitude, byte[] direction,
                                                int width, int height) {
        int words = Dilation.words(width);
        long[] suppressed = new long[words * height];
        IntStream.range(1, height - 1).parallel().forEach(y -> {
            for (int x = 1; x < width - 1; x++) {
                int pixel = y * width + x;
                short value = magnitude[pixel];
                if (value <= edgeThreshold)
                    continue;
                int along = alongGradientY[direction[pixel]] * width
                            + alongGradientX[direction[pixel]];
                if (value >= magnitude[pixel + along] && value >= magnitude[pixel - along])
                    Dilation.set(suppressed, words, x, y);
            }
        });
        return suppressed;
    }

}
