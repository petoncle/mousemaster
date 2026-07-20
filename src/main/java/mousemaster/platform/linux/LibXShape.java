package mousemaster.platform.linux;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * JNA bindings for the X Shape extension (libXext.so), used to make overlay windows
 * click-through by giving them an empty input shape. Qt's WA_TransparentForMouseEvents
 * attribute does not reliably achieve this for frameless, X11BypassWindowManagerHint
 * (override-redirect) windows on every window manager, so the input shape is cleared
 * directly at the X11 level instead - the same mechanism compositors/overlay tools use.
 */
public interface LibXShape extends Library {

    LibXShape INSTANCE = Native.load("Xext", LibXShape.class);

    int ShapeInput = 2;
    int ShapeSet = 0;

    /**
     * Combines rectangles is normally used to set a window's shape; passing a null
     * rectangle list with a count of 0 sets the given shape kind to the empty region -
     * for ShapeInput, that means the window accepts no mouse/keyboard input anywhere,
     * so clicks pass through to whatever is behind it.
     */
    void XShapeCombineRectangles(Pointer display, long window, int destKind,
                                 int xOffset, int yOffset, Pointer rectangles,
                                 int rectangleCount, int operation, int ordering);

}
