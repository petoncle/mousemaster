package mousemaster;

import java.util.stream.IntStream;

/**
 * Dilation by a rectangle after Serra: along a row as a count kept while the window
 * slides, down the rows as each of them drawn over the ones it reaches.
 */
class Dilation {

    static boolean[] dilated(boolean[] mask, int width, int height, int horizontal,
                             int vertical) {
        return vertically(horizontally(mask, width, height, horizontal), width, height,
                vertical);
    }

    private static boolean[] horizontally(boolean[] mask, int width, int height,
                                          int reach) {
        boolean[] dilated = new boolean[mask.length];
        IntStream.range(0, height).parallel().forEach(y -> {
            int row = y * width;
            int set = 0;
            for (int x = 0; x < Math.min(width, reach); x++)
                if (mask[row + x])
                    set++;
            for (int x = 0; x < width; x++) {
                int entering = x + reach;
                if (entering < width && mask[row + entering])
                    set++;
                dilated[row + x] = set > 0;
                int leaving = x - reach;
                if (leaving >= 0 && mask[row + leaving])
                    set--;
            }
        });
        return dilated;
    }

    private static boolean[] vertically(boolean[] mask, int width, int height,
                                        int reach) {
        boolean[] dilated = new boolean[mask.length];
        IntStream.range(0, height).parallel().forEach(y -> {
            int row = y * width;
            int from = Math.max(0, y - reach);
            int to = Math.min(height - 1, y + reach);
            for (int reached = from; reached <= to; reached++) {
                int reachedRow = reached * width;
                for (int x = 0; x < width; x++)
                    dilated[row + x] |= mask[reachedRow + x];
            }
        });
        return dilated;
    }

}
