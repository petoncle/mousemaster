package mousemaster;

/** Crow's summed area table: any rectangle's count in four lookups. */
public class SummedAreaTable {

    private final int[] sums;
    private final int stride;

    public SummedAreaTable(boolean[] mask, int width, int height) {
        stride = width + 1;
        sums = new int[stride * (height + 1)];
        for (int y = 0; y < height; y++) {
            int rowSum = 0;
            for (int x = 0; x < width; x++) {
                if (mask[y * width + x])
                    rowSum++;
                sums[(y + 1) * stride + x + 1] = sums[y * stride + x + 1] + rowSum;
            }
        }
    }

    public int count(Rectangle box) {
        return count(box.x(), box.y(), box.x() + box.width(), box.y() + box.height());
    }

    public int count(int left, int top, int right, int bottom) {
        return sums[bottom * stride + right] - sums[top * stride + right]
               - sums[bottom * stride + left] + sums[top * stride + left];
    }

}
