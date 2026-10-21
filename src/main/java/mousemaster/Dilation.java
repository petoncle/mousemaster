package mousemaster;

import java.util.stream.IntStream;

/**
 * Dilation by a rectangle after Serra: along a row as its words shifted over the pixels
 * they reach, down the rows as each of them drawn over the ones it reaches.
 */
class Dilation {

    /** A mask holds a row's pixels 64 to a word, the last word's spare bits clear. */
    static int words(int width) {
        return (width + 63) >>> 6;
    }

    static boolean isSet(long[] mask, int words, int x, int y) {
        return (mask[y * words + (x >>> 6)] >>> x & 1) != 0;
    }

    static void set(long[] mask, int words, int x, int y) {
        mask[y * words + (x >>> 6)] |= 1L << x;
    }

    static void clear(long[] mask, int words, int x, int y) {
        mask[y * words + (x >>> 6)] &= ~(1L << x);
    }

    static long[] dilated(long[] mask, int width, int height, int horizontal,
                          int vertical) {
        return vertically(horizontally(mask, width, height, horizontal), width, height,
                vertical);
    }

    private static long[] horizontally(long[] mask, int width, int height, int reach) {
        int words = words(width);
        long last = -1L >>> (64 - width % 64) % 64;
        long[] dilated = new long[mask.length];
        IntStream.range(0, height).parallel().forEach(y -> {
            int row = y * words;
            for (int word = row; word < row + words; word++) {
                long bits = mask[word];
                long left = word > row ? mask[word - 1] : 0;
                long right = word + 1 < row + words ? mask[word + 1] : 0;
                for (int shift = 1; shift <= reach; shift++)
                    bits |= mask[word] << shift | left >>> (64 - shift)
                            | mask[word] >>> shift | right << (64 - shift);
                dilated[word] = bits;
            }
            dilated[row + words - 1] &= last;
        });
        return dilated;
    }

    private static long[] vertically(long[] mask, int width, int height, int reach) {
        int words = words(width);
        long[] dilated = new long[mask.length];
        IntStream.range(0, height).parallel().forEach(y -> {
            int row = y * words;
            int from = Math.max(0, y - reach) * words;
            int to = Math.min(height - 1, y + reach) * words;
            for (int word = 0; word < words; word++) {
                long bits = 0;
                for (int reached = from; reached <= to; reached += words)
                    bits |= mask[reached + word];
                dilated[row + word] = bits;
            }
        });
        return dilated;
    }

}
