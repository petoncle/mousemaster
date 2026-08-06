package mousemaster.platform.macos;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

/**
 * Prints how far the named application's focused window is scrolled. Every scroll area is
 * summed because only the one under the cursor moves.
 */
public class ScrollBarReader {

    private static final ApplicationServices applicationServices =
            ApplicationServices.INSTANCE;
    private static final CoreFoundation coreFoundation = CoreFoundation.INSTANCE;

    private static Pointer string(String value) {
        return coreFoundation.CFStringCreateWithCString(null, value,
                CoreFoundation.utf8Encoding);
    }

    private static Pointer copy(Pointer element, String attribute) {
        PointerByReference value = new PointerByReference();
        return applicationServices.AXUIElementCopyAttributeValue(element, string(attribute),
                value) == 0 ? value.getValue() : null;
    }

    private static String text(Pointer string) {
        byte[] buffer = new byte[64];
        return coreFoundation.CFStringGetCString(string, buffer, buffer.length,
                CoreFoundation.utf8Encoding) ? Native.toString(buffer) : "";
    }

    private static double scrolled(Pointer element, int depth) {
        if (depth > 16)
            return 0;
        double total = 0;
        Pointer role = copy(element, "AXRole");
        if (role != null && "AXScrollArea".equals(text(role))) {
            Pointer bar = copy(element, "AXVerticalScrollBar");
            Pointer number = bar == null ? null : copy(bar, "AXValue");
            double[] read = new double[1];
            if (number != null &&
                coreFoundation.CFNumberGetValue(number, CoreFoundation.doubleType, read))
                total += read[0];
        }
        Pointer children = copy(element, "AXChildren");
        if (children == null)
            return total;
        long count = coreFoundation.CFArrayGetCount(children);
        for (long index = 0; index < count; index++)
            total += scrolled(coreFoundation.CFArrayGetValueAtIndex(children, index),
                    depth + 1);
        return total;
    }

    /** The process to read is named, not taken from whichever one happens to be frontmost: an
     *  earlier suite can leave another application there, whose scroll bar never moves. */
    public static void main(String[] args) {
        int processId = args.length > 0 ? Integer.parseInt(args[0]) :
                MacosAccessibility.frontmostProcessId();
        Pointer application = applicationServices.AXUIElementCreateApplication(processId);
        Pointer window = copy(application, "AXFocusedWindow");
        System.out.println("read process " + processId);
        System.out.println("scrolled=" + (window == null ? -1 : scrolled(window, 0)));
    }

}
