package mousemaster;

import java.util.stream.IntStream;

/**
 * Content against background: a channel far enough from the median of a window around it,
 * that median kept as the window slides after Huang, Yang and Tang. Per channel, so a
 * colour that keeps the brightness still reads as content.
 */
public class InkDetector {

    private static final int channelLevels = 64;
    /** Measured as the median run of ink: 2 on all ten screens tried. */
    static final int strokeWidth = 2;
    /** Wider than a stroke, so a stroke stays a minority of its own window. */
    private static final int backgroundRadius = 3 * strokeWidth;
    /** Levels of 255 from the background. Empirical; Otsu chose 82 here, which
     *  drops grey text on white. */
    private static final int inkThreshold = 42;
    private static final int horizontalDilation = strokeWidth;
    private static final int verticalDilation = strokeWidth / 2;
    /** Rows of the window the median is taken over, a stroke apart: any further and a
     *  stroke could fall between two of them and leave the background it sits on. */
    private static final int backgroundStep = strokeWidth;
    /** Rows sharing one background, a stroke apart for the same reason. */
    private static final int backgroundRows = strokeWidth;

    public static boolean[] ink(byte[] rgb, int width, int height) {
        boolean[] ink = new boolean[width * height];
        byte[] backgrounds = new byte[rgb.length];
        IntStream.range(0, (height + backgroundRows - 1) / backgroundRows)
                 .parallel().forEach(band -> {
            int y = band * backgroundRows;
            int top = Math.max(0, y - backgroundRadius);
            int bottom = Math.min(height - 1, y + backgroundRadius);
            Background[] backgroundByChannel =
                    {new Background(), new Background(), new Background()};
            for (int x = 0; x <= Math.min(width - 1, backgroundRadius); x++)
                for (int windowY = top; windowY <= bottom; windowY += backgroundStep)
                    count(backgroundByChannel, rgb, (windowY * width + x) * 3, 1);
            for (int x = 0; x < width; x++) {
                int pixel = y * width + x;
                for (int channel = 0; channel < 3; channel++) {
                    int median = backgroundByChannel[channel].median();
                    for (int row = y; row < Math.min(height, y + backgroundRows);
                         row++) {
                        int shared = row * width + x;
                        backgrounds[shared * 3 + channel] = (byte) median;
                        if (Math.abs((rgb[shared * 3 + channel] & 0xff) - value(median)) >
                            inkThreshold)
                            ink[shared] = true;
                    }
                }
                int leaving = x - backgroundRadius;
                int entering = x + backgroundRadius + 1;
                for (int windowY = top; windowY <= bottom; windowY += backgroundStep) {
                    if (leaving >= 0)
                        count(backgroundByChannel, rgb, (windowY * width + leaving) * 3,
                                -1);
                    if (entering < width)
                        count(backgroundByChannel, rgb, (windowY * width + entering) * 3,
                                1);
                }
            }
        });
        addBackgroundEdges(backgrounds, width, height, ink);
        return ink;
    }

    private static void count(Background[] backgroundByChannel, byte[] rgb, int pixel,
                              int delta) {
        for (int channel = 0; channel < 3; channel++)
            backgroundByChannel[channel].count(level(rgb[pixel + channel]), delta);
    }

    /** The median of the window, kept as it slides rather than searched for each pixel. */
    private static final class Background {

        private final int[] histogram = new int[channelLevels];
        private int median;
        private int below;
        private int count;

        private void count(int level, int delta) {
            histogram[level] += delta;
            count += delta;
            if (level < median)
                below += delta;
        }

        private int median() {
            while (median > 0 && below * 2 >= count)
                below -= histogram[--median];
            while (median < channelLevels - 1 && (below + histogram[median]) * 2 < count)
                below += histogram[median++];
            return median;
        }
    }

    private static int level(byte channel) {
        return (channel & 0xff) * channelLevels / 256;
    }

    private static int value(int level) {
        return (level * 256 + 128) / channelLevels;
    }

    private static void addBackgroundEdges(byte[] backgrounds, int width, int height,
                                           boolean[] ink) {
        int step = inkThreshold * channelLevels / 256;
        IntStream.range(0, height).parallel().forEach(y -> {
            for (int x = 0; x < width; x++) {
                int pixel = y * width + x;
                if (!ink[pixel] &&
                    (x + 1 < width && changes(backgrounds, pixel, pixel + 1, step) ||
                     y + 1 < height && changes(backgrounds, pixel, pixel + width, step)))
                    ink[pixel] = true;
            }
        });
    }

    private static boolean changes(byte[] backgrounds, int pixel, int neighbor, int step) {
        for (int channel = 0; channel < 3; channel++)
            if (Math.abs(backgrounds[pixel * 3 + channel] -
                         backgrounds[neighbor * 3 + channel]) > step)
                return true;
        return false;
    }

    public static boolean[] dilated(boolean[] ink, int width, int height) {
        return Dilation.dilated(ink, width, height, horizontalDilation, verticalDilation);
    }

    public static boolean[] downsampledInk(boolean[] ink, int width, int height,
                                           int downsampleFactor, int downsampledWidth,
                                           int downsampledHeight) {
        boolean[] downsampled = new boolean[downsampledWidth * downsampledHeight];
        IntStream.range(0, downsampledHeight).parallel().forEach(downsampledY -> {
            int toY = Math.min(height, (downsampledY + 1) * downsampleFactor);
            for (int downsampledX = 0; downsampledX < downsampledWidth; downsampledX++) {
                int toX = Math.min(width, (downsampledX + 1) * downsampleFactor);
                for (int y = downsampledY * downsampleFactor; y < toY; y++) {
                    for (int x = downsampledX * downsampleFactor; x < toX; x++) {
                        if (ink[y * width + x]) {
                            downsampled[downsampledY * downsampledWidth + downsampledX] =
                                    true;
                            y = toY;
                            break;
                        }
                    }
                }
            }
        });
        return downsampled;
    }

}
