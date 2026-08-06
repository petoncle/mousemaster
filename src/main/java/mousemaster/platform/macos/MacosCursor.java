package mousemaster.platform.macos;

import com.sun.jna.Pointer;
import mousemaster.Point;

/** The size and hot spot of the cursor being displayed. */
public final class MacosCursor {

    private static final ObjectiveC objectiveC = ObjectiveC.INSTANCE;
    private static final Pointer currentSystemCursor =
            objectiveC.sel_registerName("currentSystemCursor");
    private static final Pointer arrowCursor =
            objectiveC.sel_registerName("arrowCursor");
    private static final Pointer image = objectiveC.sel_registerName("image");
    private static final Pointer size = objectiveC.sel_registerName("size");
    private static final Pointer hotSpot = objectiveC.sel_registerName("hotSpot");
    private static final Pointer representations =
            objectiveC.sel_registerName("representations");
    private static final Pointer objectAtIndex = objectiveC.sel_registerName("objectAtIndex:");
    private static final Pointer bitmapData = objectiveC.sel_registerName("bitmapData");
    private static final Pointer pixelsWide = objectiveC.sel_registerName("pixelsWide");
    private static final Pointer pixelsHigh = objectiveC.sel_registerName("pixelsHigh");
    private static final Pointer bytesPerRow = objectiveC.sel_registerName("bytesPerRow");
    private static final Pointer samplesPerPixel =
            objectiveC.sel_registerName("samplesPerPixel");
    private static final Pointer bitmapFormat = objectiveC.sel_registerName("bitmapFormat");

    private static Pointer inkCursor;
    private static Point ink;

    private MacosCursor() {
    }

    /** The cursor image size, which is the glyph's own bounds. */
    public static Point size() {
        Pointer cursor = cursor();
        ObjectiveC.CGSize.ByValue imageSize = ObjectiveC.ReturningSize.INSTANCE
                .objc_msgSend(objectiveC.objc_msgSend(cursor, image), size);
        return new Point(imageSize.width, imageSize.height);
    }

    /** The center of what the cursor draws: the image is larger than the glyph in it. */
    public static Point visualCenter() {
        Pointer cursor = cursor();
        ObjectiveC.CGSize.ByValue hot =
                ObjectiveC.ReturningSize.INSTANCE.objc_msgSend(cursor, hotSpot);
        Point center = inkCenter(cursor);
        return new Point(center.x() - hot.width, center.y() - hot.height);
    }

    /** Cached per cursor: this is asked for on every mouse move. */
    private static Point inkCenter(Pointer cursor) {
        if (cursor.equals(inkCursor))
            return ink;
        inkCursor = cursor;
        ink = computeInkCenter(cursor);
        return ink;
    }

    /** The center of the non-transparent pixels, in image points. */
    private static Point computeInkCenter(Pointer cursor) {
        Pointer cursorImage = objectiveC.objc_msgSend(cursor, image);
        ObjectiveC.CGSize.ByValue imageSize =
                ObjectiveC.ReturningSize.INSTANCE.objc_msgSend(cursorImage, size);
        Pointer representation = objectiveC.objc_msgSend(
                objectiveC.objc_msgSend(cursorImage, representations), objectAtIndex, 0L);
        Pointer pixels = objectiveC.objc_msgSend(representation, bitmapData);
        int width = ObjectiveC.ReturningInt.INSTANCE.objc_msgSend(representation, pixelsWide);
        int height = ObjectiveC.ReturningInt.INSTANCE.objc_msgSend(representation, pixelsHigh);
        int rowBytes = ObjectiveC.ReturningInt.INSTANCE.objc_msgSend(representation, bytesPerRow);
        int samples = ObjectiveC.ReturningInt.INSTANCE.objc_msgSend(representation, samplesPerPixel);
        // Bit 0 of the format is alpha first, which puts the alpha byte before the colors.
        int alphaOffset =
                (ObjectiveC.ReturningInt.INSTANCE.objc_msgSend(representation, bitmapFormat) &
                 1) != 0 ? 0 : samples - 1;
        int minX = width, minY = height, maxX = -1, maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((pixels.getByte((long) y * rowBytes + (long) x * samples + alphaOffset) &
                     0xFF) < 8)
                    continue;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        if (maxX < 0)
            return new Point(imageSize.width / 2, imageSize.height / 2);
        double scale = width / imageSize.width;
        return new Point((minX + maxX + 1) / 2.0 / scale, (minY + maxY + 1) / 2.0 / scale);
    }

    /** currentSystemCursor is null when no application has set one yet. */
    private static Pointer cursor() {
        Pointer cursorClass = objectiveC.objc_getClass("NSCursor");
        Pointer cursor = objectiveC.objc_msgSend(cursorClass, currentSystemCursor);
        return cursor != null ? cursor :
                objectiveC.objc_msgSend(cursorClass, arrowCursor);
    }

}
