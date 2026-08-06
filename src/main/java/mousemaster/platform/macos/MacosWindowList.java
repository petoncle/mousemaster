package mousemaster.platform.macos;

import com.sun.jna.Pointer;
import mousemaster.Rectangle;

import java.util.ArrayList;
import java.util.List;

/** The on screen windows of every application, front to back. */
public final class MacosWindowList {

    private static final CoreFoundation coreFoundation = CoreFoundation.INSTANCE;

    private static final Pointer ownerProcessIdKey = string("kCGWindowOwnerPID");
    private static final Pointer boundsKey = string("kCGWindowBounds");
    private static final Pointer layerKey = string("kCGWindowLayer");
    private static final Pointer xKey = string("X");
    private static final Pointer yKey = string("Y");
    private static final Pointer widthKey = string("Width");
    private static final Pointer heightKey = string("Height");

    public record OnScreenWindow(int processId, Rectangle bounds) {
    }

    private MacosWindowList() {
    }

    private static Pointer string(String value) {
        return coreFoundation.CFStringCreateWithCString(null, value,
                CoreFoundation.utf8Encoding);
    }

    /**
     * Only the normal window layer, which leaves out the menu bar extras, the window
     * server's own indicators and the lock screen, none of which a hint should land on.
     */
    public static List<OnScreenWindow> onScreenWindows() {
        Pointer list = CoreGraphics.INSTANCE.CGWindowListCopyWindowInfo(
                CoreGraphics.windowListOnScreenOnly |
                CoreGraphics.windowListExcludeDesktopElements, 0);
        if (list == null)
            return List.of();
        try {
            List<OnScreenWindow> windows = new ArrayList<>();
            long count = coreFoundation.CFArrayGetCount(list);
            for (long index = 0; index < count; index++) {
                Pointer window = coreFoundation.CFArrayGetValueAtIndex(list, index);
                if (integer(window, layerKey) != 0)
                    continue;
                Rectangle bounds = bounds(window);
                if (bounds == null)
                    continue;
                windows.add(new OnScreenWindow(integer(window, ownerProcessIdKey), bounds));
            }
            return windows;
        }
        finally {
            coreFoundation.CFRelease(list);
        }
    }

    private static Rectangle bounds(Pointer window) {
        Pointer bounds = coreFoundation.CFDictionaryGetValue(window, boundsKey);
        if (bounds == null)
            return null;
        return new Rectangle((int) real(bounds, xKey), (int) real(bounds, yKey),
                (int) real(bounds, widthKey), (int) real(bounds, heightKey));
    }

    private static int integer(Pointer dictionary, Pointer key) {
        Pointer number = coreFoundation.CFDictionaryGetValue(dictionary, key);
        int[] value = new int[1];
        if (number != null)
            coreFoundation.CFNumberGetValue(number, CoreFoundation.intType, value);
        return value[0];
    }

    private static double real(Pointer dictionary, Pointer key) {
        Pointer number = coreFoundation.CFDictionaryGetValue(dictionary, key);
        double[] value = new double[1];
        if (number != null)
            coreFoundation.CFNumberGetValue(number, CoreFoundation.doubleType, value);
        return value[0];
    }

}
