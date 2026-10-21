package mousemaster;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Dilation by a rectangle after Serra: along a row as each set pixel drawn over the ones
 * it reaches, down the rows as each of them drawn over the ones it reaches.
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
            for (int x = 0; x < width; x++)
                if (mask[row + x])
                    Arrays.fill(dilated, row + Math.max(0, x - reach),
                            row + Math.min(width, x + reach + 1), true);
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
            System.arraycopy(mask, from * width, dilated, row, width);
            for (int reached = from + 1; reached <= to; reached++) {
                int reachedRow = reached * width;
                for (int x = 0; x < width; x++)
                    dilated[row + x] |= mask[reachedRow + x];
            }
        });
        return dilated;
    }

}
