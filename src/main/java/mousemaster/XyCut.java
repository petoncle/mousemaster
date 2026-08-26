package mousemaster;

import java.util.ArrayList;
import java.util.List;

/**
 * The recursive X-Y cut of Nagy and Seth: part a box at the valleys of its projection
 * profile, rows then columns, until nothing parts it.
 */
public class XyCut {

    private static final int minimumInkPixels = 1;
    /** Of what the box's own lines average, so a sparse outline stays whole. */
    private static final double gapInkRatio = 0.15;
    /** Deeper than this parts nothing further. */
    private static final int maximumCutDepth = 4;
    /** Below this an element is an ink sliver rather than a target. */
    private static final int minimumElementColumns = 2;
    /** A drawn border is a pixel or two wide at this resolution. */
    private static final int borderInset = 2;

    static void cut(boolean[] ink, SummedAreaTable inkTable, int width, Rectangle box,
                    double maximumElementArea, int minimumGapColumns, List<Rectangle> out) {
        cut(ink, inkTable, width, box, maximumElementArea, minimumGapColumns, 0, out);
    }

    private static void cut(boolean[] ink, SummedAreaTable inkTable, int width,
                            Rectangle box, double maximumElementArea,
                            int minimumGapColumns, int depth, List<Rectangle> out) {
        List<Rectangle> rows = cutAtProfileValleys(inkTable, box, false, minimumGapColumns);
        if (rows.size() > 1 && depth < maximumCutDepth) {
            for (Rectangle row : rows)
                cut(ink, inkTable, width, row, maximumElementArea, minimumGapColumns,
                        depth + 1, out);
            return;
        }
        Rectangle tightened = rows.size() == 1 ? rows.getFirst() : box;
        List<Rectangle> columns = cutAtProfileValleys(inkTable, tightened, true, minimumGapColumns);
        if (columns.size() > 1 && depth < maximumCutDepth) {
            for (Rectangle column : columns)
                cut(ink, inkTable, width, column, maximumElementArea, minimumGapColumns,
                        depth + 1, out);
            return;
        }
        Rectangle element = columns.size() == 1 ? columns.getFirst() : tightened;
        // A border inks every line of the box, leaving no gap to cut at.
        if (depth < maximumCutDepth && element.width() > 2 * borderInset
            && element.height() > 2 * borderInset
            && enclosesMoreThanOneBlock(inkTable, element, minimumGapColumns)) {
            List<Rectangle> inside = new ArrayList<>();
            boolean[] enclosed = borderCleared(ink, width, element);
            cut(enclosed,
                    new SummedAreaTable(enclosed, element.width(), element.height()),
                    element.width(),
                    new Rectangle(0, 0, element.width(), element.height()),
                    maximumElementArea, minimumGapColumns, depth + 1, inside);
            if (!inside.isEmpty()) {
                for (Rectangle part : inside)
                    out.add(new Rectangle(part.x() + element.x(), part.y() + element.y(),
                            part.width(), part.height()));
                return;
            }
        }
        double area = (double) element.width() * element.height();
        if (element.width() >= minimumElementColumns && area <= maximumElementArea)
            out.add(element);
    }

    /** The box trimmed to its ink and parted at the valleys of its projection profile. */
    private static List<Rectangle> cutAtProfileValleys(SummedAreaTable inkTable,
                                                       Rectangle box, boolean columns,
                                                       int minimumGapColumns) {
        int along = columns ? box.width() : box.height();
        int gapInk = Math.max(minimumInkPixels,
                (int) (gapInkRatio * inkTable.count(box) / along));
        List<Rectangle> parts = new ArrayList<>();
        int start = -1;
        int lastInk = -1;
        int gap = 0;
        for (int offset = 0; offset < along; offset++) {
            int lineInk = columns
                    ? inkTable.count(box.x() + offset, box.y(), box.x() + offset + 1,
                            box.y() + box.height())
                    : inkTable.count(box.x(), box.y() + offset, box.x() + box.width(),
                            box.y() + offset + 1);
            if (lineInk > gapInk) {
                if (start < 0)
                    start = offset;
                lastInk = offset;
                gap = 0;
            }
            else if (start >= 0 && ++gap >= (columns ? minimumGapColumns : 1)) {
                parts.add(band(box, start, lastInk, columns));
                start = -1;
            }
        }
        if (start >= 0)
            parts.add(band(box, start, lastInk, columns));
        return parts;
    }

    private static Rectangle band(Rectangle box, int from, int to, boolean columns) {
        return columns ? new Rectangle(box.x() + from, box.y(), to - from + 1, box.height())
                       : new Rectangle(box.x(), box.y() + from, box.width(), to - from + 1);
    }

    private static boolean enclosesMoreThanOneBlock(SummedAreaTable inkTable,
                                                   Rectangle box,
                                                   int minimumGapColumns) {
        Rectangle within = new Rectangle(box.x() + borderInset, box.y() + borderInset,
                box.width() - 2 * borderInset, box.height() - 2 * borderInset);
        return cutAtProfileValleys(inkTable, within, false, minimumGapColumns).size() > 1
               || cutAtProfileValleys(inkTable, within, true, minimumGapColumns).size() > 1;
    }

    /** As imclearborder, except that only ink reaching two opposite edges is a border:
     *  anything else touching an edge is content and stays. */
    private static boolean[] borderCleared(boolean[] ink, int width, Rectangle box) {
        int boxWidth = box.width(), boxHeight = box.height();
        boolean[] enclosed = new boolean[boxWidth * boxHeight];
        for (int y = 0; y < boxHeight; y++)
            for (int x = 0; x < boxWidth; x++)
                enclosed[y * boxWidth + x] = ink[(box.y() + y) * width + box.x() + x];
        int[] reaching = new int[enclosed.length];
        for (int x = 0; x < boxWidth; x++) {
            clearIfSpansOppositeEdges(enclosed, reaching, boxWidth, boxHeight, x);
            clearIfSpansOppositeEdges(enclosed, reaching, boxWidth, boxHeight,
                    (boxHeight - 1) * boxWidth + x);
        }
        for (int y = 0; y < boxHeight; y++) {
            clearIfSpansOppositeEdges(enclosed, reaching, boxWidth, boxHeight,
                    y * boxWidth);
            clearIfSpansOppositeEdges(enclosed, reaching, boxWidth, boxHeight,
                    y * boxWidth + boxWidth - 1);
        }
        return enclosed;
    }

    private static void clearIfSpansOppositeEdges(boolean[] enclosed, int[] reaching,
                                                  int boxWidth, int boxHeight, int seed) {
        int count = flood(enclosed, reaching, 0, seed);
        int left = seed % boxWidth, top = seed / boxWidth;
        int right = left, bottom = top;
        for (int next = 0; next < count; next++) {
            int reached = reaching[next];
            int x = reached % boxWidth, y = reached / boxWidth;
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x);
            bottom = Math.max(bottom, y);
            for (int neighborY = Math.max(0, y - 1);
                 neighborY <= Math.min(boxHeight - 1, y + 1); neighborY++)
                for (int neighborX = Math.max(0, x - 1);
                     neighborX <= Math.min(boxWidth - 1, x + 1); neighborX++)
                    count = flood(enclosed, reaching, count,
                            neighborY * boxWidth + neighborX);
        }
        if ((left != 0 || right != boxWidth - 1)
            && (top != 0 || bottom != boxHeight - 1))
            for (int next = 0; next < count; next++)
                enclosed[reaching[next]] = true;
    }

    private static int flood(boolean[] enclosed, int[] reaching, int count, int pixel) {
        if (!enclosed[pixel])
            return count;
        enclosed[pixel] = false;
        reaching[count] = pixel;
        return count + 1;
    }

}
