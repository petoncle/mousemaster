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

    private ConnectedComponentFinder(boolean[] mask, int width, int height) {
        int components = 0;
        for (int y = 0; y < height; y++)
            for (int x = 0, row = y * width; x < width; x++)
                if (mask[row + x] && (x == 0 || !mask[row + x - 1]))
                    components++;
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
            int row = y * width;
            int count = 0;
            int above = 0;
            int firstComponent = next;
            for (int x = 0; x < width; x++) {
                if (!mask[row + x])
                    continue;
                int start = x;
                while (x + 1 < width && mask[row + x + 1])
                    x++;
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

    public static List<Rectangle> boundingBoxes(boolean[] mask, int width, int height,
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
