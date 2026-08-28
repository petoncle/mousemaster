package mousemaster;

import java.util.ArrayList;
import java.util.Arrays;
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
        for (boolean set : mask)
            if (set)
                components++;
        parent = new int[components];
        size = new int[components];
        left = new short[components];
        top = new short[components];
        right = new short[components];
        bottom = new short[components];
        // The neighbours a pixel joins are all in its own row or the one above.
        int[] previousRow = new int[width];
        int[] currentRow = new int[width];
        int next = 0;
        Arrays.fill(previousRow, -1);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = y * width + x;
                if (!mask[pixel]) {
                    currentRow[x] = -1;
                    continue;
                }
                int component = next++;
                parent[component] = component;
                size[component] = 1;
                left[component] = right[component] = (short) x;
                top[component] = bottom[component] = (short) y;
                currentRow[x] = component;
                if (x > 0 && currentRow[x - 1] >= 0)
                    union(component, currentRow[x - 1]);
                if (previousRow[x] >= 0)
                    union(component, previousRow[x]);
                if (x > 0 && previousRow[x - 1] >= 0)
                    union(component, previousRow[x - 1]);
                if (x + 1 < width && previousRow[x + 1] >= 0)
                    union(component, previousRow[x + 1]);
            }
            int[] swapped = previousRow;
            previousRow = currentRow;
            currentRow = swapped;
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
