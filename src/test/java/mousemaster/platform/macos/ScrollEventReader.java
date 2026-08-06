package mousemaster.platform.macos;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

/** Prints the two scroll axes of every scroll event, so a wheel command's ask is visible. */
public class ScrollEventReader {

    private static final int scrollWheelEvent = 22;
    private static final int sessionEventTap = 1;
    private static final int headInsert = 0;
    private static final int listenOnly = 1;
    private static final int deltaAxis1 = 11;
    private static final int deltaAxis2 = 12;
    private static final int pointDeltaAxis1 = 96;
    private static final int pointDeltaAxis2 = 97;

    private interface Tap extends Library {
        Tap INSTANCE = Native.load("CoreGraphics", Tap.class);

        Pointer CGEventTapCreate(int tap, int place, int options, long eventsOfInterest,
                                 Callback callback, Pointer userInfo);

        void CGEventTapEnable(Pointer tap, boolean enable);

        long CGEventGetIntegerValueField(Pointer event, int field);
    }

    private interface Loop extends Library {
        Loop INSTANCE = Native.load("CoreFoundation", Loop.class);

        Pointer CFMachPortCreateRunLoopSource(Pointer allocator, Pointer port, long order);

        Pointer CFRunLoopGetCurrent();

        void CFRunLoopAddSource(Pointer runLoop, Pointer source, Pointer mode);

        int CFRunLoopRunInMode(Pointer mode, double seconds, boolean returnAfterSourceHandled);
    }

    /** Held in a field: a callback collected while the tap still holds it crashes the process. */
    private static Callback callback;

    public static void main(String[] args) {
        callback = new Callback() {
            @SuppressWarnings("unused")
            public Pointer callback(Pointer proxy, int type, Pointer event, Pointer userInfo) {
                if (type == scrollWheelEvent)
                    System.out.println("scroll axis1=" +
                                       Tap.INSTANCE.CGEventGetIntegerValueField(event, deltaAxis1) +
                                       " axis2=" +
                                       Tap.INSTANCE.CGEventGetIntegerValueField(event, deltaAxis2) +
                                       " pointAxis1=" +
                                       Tap.INSTANCE.CGEventGetIntegerValueField(event, pointDeltaAxis1) +
                                       " pointAxis2=" +
                                       Tap.INSTANCE.CGEventGetIntegerValueField(event, pointDeltaAxis2));
                return event;
            }
        };
        Pointer tap = Tap.INSTANCE.CGEventTapCreate(sessionEventTap, headInsert, listenOnly,
                1L << scrollWheelEvent, callback, null);
        if (tap == null) {
            System.out.println("no tap: the process is not allowed to observe events");
            return;
        }
        Pointer mode = NativeLibrary.getInstance("CoreFoundation")
                                    .getGlobalVariableAddress("kCFRunLoopDefaultMode")
                                    .getPointer(0);
        Loop.INSTANCE.CFRunLoopAddSource(Loop.INSTANCE.CFRunLoopGetCurrent(),
                Loop.INSTANCE.CFMachPortCreateRunLoopSource(null, tap, 0), mode);
        Tap.INSTANCE.CGEventTapEnable(tap, true);
        System.out.println("tap ready");
        long deadline = System.nanoTime() + Long.parseLong(args[0]) * 1_000_000_000L;
        while (System.nanoTime() < deadline)
            Loop.INSTANCE.CFRunLoopRunInMode(mode, 0.5, false);
        System.out.println("tap done");
    }

}
