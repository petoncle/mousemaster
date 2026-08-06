package mousemaster.platform.macos;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

public interface CoreGraphics extends Library {

    CoreGraphics INSTANCE = Native.load("CoreGraphics", CoreGraphics.class);

    int hidEventTap = 0;

    int leftMouseDown = 1;
    int leftMouseUp = 2;
    int rightMouseDown = 3;
    int rightMouseUp = 4;
    int mouseMoved = 5;
    int leftMouseDragged = 6;
    int rightMouseDragged = 7;
    int scrollWheel = 22;
    int otherMouseDown = 25;
    int otherMouseUp = 26;
    int otherMouseDragged = 27;

    int leftButton = 0;
    int rightButton = 1;
    int centerButton = 2;

    int scrollEventUnitPixel = 0;

    int hidSystemState = 1;

    @Structure.FieldOrder({"x", "y"})
    class CGPoint extends Structure {
        public double x;
        public double y;

        public static class ByValue extends CGPoint implements Structure.ByValue {
            public ByValue(double x, double y) {
                this.x = x;
                this.y = y;
            }
        }
    }

    @Structure.FieldOrder({"x", "y", "width", "height"})
    class CGRect extends Structure {
        public double x;
        public double y;
        public double width;
        public double height;

        public static class ByValue extends CGRect implements Structure.ByValue {
            public ByValue(double x, double y, double width, double height) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
            }
        }
    }

    int CGMainDisplayID();

    int CGDisplayHideCursor(int display);

    int CGDisplayShowCursor(int display);

    int CGWarpMouseCursorPosition(CGPoint.ByValue point);

    int CGAssociateMouseAndMouseCursorPosition(boolean connected);

    boolean CGEventSourceButtonState(int stateId, int button);

    /** The session of the caller, or null when it has no quartz session. */
    Pointer CGSessionCopyCurrentDictionary();

    int windowListOnScreenOnly = 1;
    int windowListOnScreenBelowWindow = 4;
    int windowListExcludeDesktopElements = 16;

    int windowImageDefault = 0;

    /** The on screen windows, front to back. */
    Pointer CGWindowListCopyWindowInfo(int option, int relativeToWindow);

    /**
     * Obsoleted in the headers since macOS 15 in favor of ScreenCaptureKit, whose frames
     * only arrive through an Objective-C protocol, but still exported and still working.
     */
    Pointer CGWindowListCreateImage(CGRect.ByValue bounds, int option,
                                    int relativeToWindow, int imageOption);

    Pointer CGEventCreateMouseEvent(Pointer source, int type, CGPoint.ByValue point,
                                    int button);

    /** The non-variadic variant: arm64 passes variadic arguments on the stack. */
    Pointer CGEventCreateScrollWheelEvent2(Pointer source, int units, int wheelCount,
                                           int wheel1, int wheel2, int wheel3);

    Pointer CGEventCreateKeyboardEvent(Pointer source, short virtualKey, boolean keyDown);

    /** Types the characters whatever the layout, the counterpart of KEYEVENTF_UNICODE. */
    void CGEventKeyboardSetUnicodeString(Pointer event, long length, char[] characters);

    void CGEventPost(int tap, Pointer event);

}
