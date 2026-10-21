package mousemaster;

import java.util.ArrayList;
import java.util.List;

public class ConnectedComponentFinder {

    private final int[] parent;
    private final int[] size;
    private final short[] left;
    private final short[] top;
    private final short[] right;
    private final short[] bottom;

    private ConnectedComponentFinder(long[] mask, int width, int height) {
        int words = Dilation.words(width);
        int components = 0;
        for (int y = 0; y < height; y++) {
            long carry = 0;
            for (int word = y * words; word < (y + 1) * words; word++) {
                components += Long.bitCount(mask[word] & ~(mask[word] << 1 | carry));
                carry = mask[word] >>> 63;
            }
        }
        parent = new int[components];
        size = new int[components];
        left = new short[components];
        top = new short[components];
        right = new short[components];
        bottom = new short[components];
        // A run of set pixels joins the runs of the row above that touch it.
        int[] previousStarts = new int[width];
        int[] previousEnds = new int[width];
        int[] currentStarts = new int[width];
        int[] currentEnds = new int[width];
        int previousCount = 0;
        int next = 0;
        for (int y = 0; y < height; y++) {
            int row = y * words;
            int count = 0;
            int above = 0;
            int firstComponent = next;
            int start = nextPixel(mask, row, words, 0, true);
            while (start < width) {
                int x = nextPixel(mask, row, words, start, false) - 1;
                int component = next++;
                parent[component] = component;
                size[component] = x - start + 1;
                left[component] = (short) start;
                right[component] = (short) x;
                top[component] = bottom[component] = (short) y;
                while (above < previousCount && previousEnds[above] < start - 1)
                    above++;
                for (int touching = above;
                     touching < previousCount && previousStarts[touching] <= x + 1;
                     touching++)
                    union(component, firstComponent - previousCount + touching);
                currentStarts[count] = start;
                currentEnds[count++] = x;
                start = nextPixel(mask, row, words, x + 1, true);
            }
            int[] swapped = previousStarts;
            previousStarts = currentStarts;
            currentStarts = swapped;
            swapped = previousEnds;
            previousEnds = currentEnds;
            currentEnds = swapped;
            previousCount = count;
        }
    }

    /** The first pixel of the row at or after x that is set, or clear, or past the row. */
    private static int nextPixel(long[] mask, int row, int words, int x, boolean set) {
        long flip = set ? 0 : -1;
        int word = x >>> 6;
        if (word == words)
            return x;
        long bits = (mask[row + word] ^ flip) & -1L << x;
        while (bits == 0) {
            if (++word == words)
                return word * 64;
            bits = mask[row + word] ^ flip;
        }
        return word * 64 + Long.numberOfTrailingZeros(bits);
    }

    public static List<Rectangle> boundingBoxes(long[] mask, int width, int height,
                                                int minimumPixels) {
        return new ConnectedComponentFinder(mask, width, height).boundingBoxes(minimumPixels);
    }

    private List<Rectangle> boundingBoxes(int minimumPixels) {
        List<Rectangle> boundingBoxes = new ArrayList<>();
        for (int component = 0; component < parent.length; component++) {
            if (parent[component] != component || size[component] < minimumPixels)
                continue;
            boundingBoxes.add(new Rectangle(left[component], top[component],
                    right[component] - left[component] + 1,
                    bottom[component] - top[component] + 1));
        }
        return boundingBoxes;
    }

    private int find(int component) {
        int root = component;
        while (parent[root] != root)
            root = parent[root];
        while (parent[component] != root) {
            int next = parent[component];
            parent[component] = root;
            component = next;
        }
        return root;
    }

    private void union(int component, int otherComponent) {
        int root = find(component);
        int otherRoot = find(otherComponent);
        if (root == otherRoot)
            return;
        if (size[root] < size[otherRoot]) {
            int smallerRoot = root;
            root = otherRoot;
            otherRoot = smallerRoot;
        }
        parent[otherRoot] = root;
        size[root] += size[otherRoot];
        left[root] = (short) Math.min(left[root], left[otherRoot]);
        top[root] = (short) Math.min(top[root], top[otherRoot]);
        right[root] = (short) Math.max(right[root], right[otherRoot]);
        bottom[root] = (short) Math.max(bottom[root], bottom[otherRoot]);
    }

}
