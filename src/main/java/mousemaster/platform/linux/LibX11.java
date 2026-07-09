package mousemaster.platform.linux;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * JNA bindings for Xlib (libX11.so)
 */
public interface LibX11 extends Library {

    LibX11 INSTANCE = Native.load("X11", LibX11.class);

    // Display management
    Pointer XOpenDisplay(String displayName);
    int XCloseDisplay(Pointer display);
    int XDefaultScreen(Pointer display);
    long XDefaultRootWindow(Pointer display);
    int XFlush(Pointer display);
    int XSync(Pointer display, boolean discard);

    // Atom management
    long XInternAtom(Pointer display, String atomName, boolean onlyIfExists);

    // Window properties
    int XChangeProperty(Pointer display, long window, long property, long type,
                       int format, int mode, byte[] data, int nelements);

    int XGetWindowProperty(Pointer display, long window, long property,
                          long longOffset, long longLength, boolean delete,
                          long reqType, LongByReference actualTypeReturn,
                          IntByReference actualFormatReturn,
                          LongByReference nitemsReturn,
                          LongByReference bytesAfterReturn,
                          PointerByReference propReturn);

    // Mouse pointer control
    int XWarpPointer(Pointer display, long srcWindow, long destWindow,
                    int srcX, int srcY, int srcWidth, int srcHeight,
                    int destX, int destY);

    boolean XQueryPointer(Pointer display, long window,
                         LongByReference rootReturn, LongByReference childReturn,
                         IntByReference rootXReturn, IntByReference rootYReturn,
                         IntByReference winXReturn, IntByReference winYReturn,
                         IntByReference maskReturn);

    // Window management
    int XGetWindowAttributes(Pointer display, long window, XWindowAttributes attributesReturn);

    // Memory management
    int XFree(Pointer data);

    // Event types
    int KeyPress = 2;
    int KeyRelease = 3;

    // Event handling
    int XPending(Pointer display);
    int XNextEvent(Pointer display, XEvent eventReturn);
    String XKeysymToString(long keysym);
    long XLookupKeysym(XKeyEvent event, int index);

    // Keyboard grabbing
    int XGrabKeyboard(Pointer display, long window, int ownerEvents,
                     int pointerMode, int keyboardMode, long time);
    void XUngrabKeyboard(Pointer display, long time);

    // Event masks
    void XSelectInput(Pointer display, long window, long eventMask);

    // Grab modes
    int GrabModeSync = 0;
    int GrabModeAsync = 1;

    // Grab status
    int GrabSuccess = 0;
    int AlreadyGrabbed = 1;
    int GrabInvalidTime = 2;
    int GrabNotViewable = 3;
    int GrabFrozen = 4;

    // Event masks
    long KeyPressMask = 1L << 0;
    long KeyReleaseMask = 1L << 1;

    // Time constants
    long CurrentTime = 0L;

    // Constants
    int PropModeReplace = 0;
    long None = 0L;
    long AnyPropertyType = 0L;
    long XA_ATOM = 4L;
    long XA_WINDOW = 33L;

    /**
     * X11 Event union structure
     */
    @Structure.FieldOrder({"type", "pad"})
    class XEvent extends Structure {
        public int type;
        public byte[] pad = new byte[192]; // XEvent is 192 bytes

        public XKeyEvent getKeyEvent() {
            XKeyEvent keyEvent = new XKeyEvent(getPointer());
            keyEvent.read();
            return keyEvent;
        }
    }

    /**
     * X11 KeyPress/KeyRelease event structure
     */
    @Structure.FieldOrder({"type", "serial", "send_event", "display", "window",
                          "root", "subwindow", "time", "x", "y", "x_root", "y_root",
                          "state", "keycode", "same_screen"})
    class XKeyEvent extends Structure {
        public int type;
        public long serial;
        public int send_event;
        public Pointer display;
        public long window;
        public long root;
        public long subwindow;
        public long time;
        public int x, y;
        public int x_root, y_root;
        public int state;
        public int keycode;
        public int same_screen;

        public XKeyEvent() {
            super();
        }

        public XKeyEvent(Pointer p) {
            super(p);
        }
    }

    /**
     * Structure for window attributes
     */
    @Structure.FieldOrder({"x", "y", "width", "height", "borderWidth", "depth",
                          "visual", "root", "clazz", "bitGravity", "winGravity",
                          "backingStore", "backingPlanes", "backingPixel",
                          "saveUnder", "colormap", "mapInstalled", "mapState",
                          "allEventMasks", "yourEventMask", "doNotPropagateMask",
                          "overrideRedirect", "screen"})
    class XWindowAttributes extends Structure {
        public int x, y;
        public int width, height;
        public int borderWidth;
        public int depth;
        public Pointer visual;
        public long root;
        public int clazz;
        public int bitGravity;
        public int winGravity;
        public int backingStore;
        public long backingPlanes;
        public long backingPixel;
        public boolean saveUnder;
        public long colormap;
        public boolean mapInstalled;
        public int mapState;
        public long allEventMasks;
        public long yourEventMask;
        public long doNotPropagateMask;
        public boolean overrideRedirect;
        public Pointer screen;
    }

}
