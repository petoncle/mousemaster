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
        parent = new int[mask.length];
        size = new int[mask.length];
        left = new short[mask.length];
        top = new short[mask.length];
        right = new short[mask.length];
        bottom = new short[mask.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = y * width + x;
                if (!mask[pixel])
                    continue;
                parent[pixel] = pixel;
                size[pixel] = 1;
                left[pixel] = (short) x;
                top[pixel] = (short) y;
                right[pixel] = (short) x;
                bottom[pixel] = (short) y;
                if (x > 0 && mask[pixel - 1])
                    union(pixel, pixel - 1);
                if (y > 0) {
                    if (mask[pixel - width])
                        union(pixel, pixel - width);
                    if (x > 0 && mask[pixel - width - 1])
                        union(pixel, pixel - width - 1);
                    if (x + 1 < width && mask[pixel - width + 1])
                        union(pixel, pixel - width + 1);
                }
            }
        }
    }

    public static List<Rectangle> boundingBoxes(boolean[] mask, int width, int height,
                                                int minimumPixels) {
        return new ConnectedComponentFinder(mask, width, height).boundingBoxes(minimumPixels);
    }

    private List<Rectangle> boundingBoxes(int minimumPixels) {
        List<Rectangle> boundingBoxes = new ArrayList<>();
        for (int pixel = 0; pixel < parent.length; pixel++) {
            if (parent[pixel] != pixel || size[pixel] < minimumPixels)
                continue;
            boundingBoxes.add(new Rectangle(left[pixel], top[pixel],
                    right[pixel] - left[pixel] + 1, bottom[pixel] - top[pixel] + 1));
        }
        return boundingBoxes;
    }

    private int find(int pixel) {
        int root = pixel;
        while (parent[root] != root)
            root = parent[root];
        while (parent[pixel] != root) {
            int next = parent[pixel];
            parent[pixel] = root;
            pixel = next;
        }
        return root;
    }

    private void union(int pixel, int otherPixel) {
        int root = find(pixel);
        int otherRoot = find(otherPixel);
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
