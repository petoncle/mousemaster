package mousemaster.platform.linux;

import com.sun.jna.Pointer;
import mousemaster.platform.MouseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// X11-only implementation. Wayland cursor movement requires zwlr_virtual_pointer_v1
// or a working uinput REL device — deferred to a later milestone.
public class LinuxMouse implements MouseController {

    private static final Logger logger = LoggerFactory.getLogger(LinuxMouse.class);

    // X11 button numbers
    private static final int BTN_LEFT   = 1;
    private static final int BTN_MIDDLE = 2;
    private static final int BTN_RIGHT  = 3;
    // Vertical scroll: 4 = up, 5 = down
    // Horizontal scroll: 6 = left, 7 = right

    private final Pointer display;
    private final long rootWindow;
    private long hiddenCursor = 0;

    public LinuxMouse(Pointer display, long rootWindow) {
        this.display = display;
        this.rootWindow = rootWindow;
    }

    public void destroy() {
        if (hiddenCursor != 0) {
            LibX11.INSTANCE.XFreeCursor(display, hiddenCursor);
            hiddenCursor = 0;
        }
    }

    @Override
    public void beginMove() {
    }

    @Override
    public void endMove() {
        LibX11.INSTANCE.XFlush(display);
    }

    @Override
    public void moveBy(boolean xForward, double dx, boolean yForward, double dy) {
        int ix = (int) dx * (xForward ? 1 : -1);
        int iy = (int) dy * (yForward ? 1 : -1);
        if (ix == 0 && iy == 0) return;
        // dest_window = None (0) → coords are relative to current pointer position
        LibX11.INSTANCE.XWarpPointer(display, 0, 0, 0, 0, 0, 0, ix, iy);
    }

    @Override
    public void synchronousMoveTo(int x, int y) {
        LibX11.INSTANCE.XWarpPointer(display, 0, rootWindow, 0, 0, 0, 0, x, y);
        LibX11.INSTANCE.XFlush(display);
    }

    @Override
    public void pressLeft() {
        buttonEvent(BTN_LEFT, true);
    }

    @Override
    public void releaseLeft() {
        buttonEvent(BTN_LEFT, false);
    }

    @Override
    public void pressMiddle() {
        buttonEvent(BTN_MIDDLE, true);
    }

    @Override
    public void releaseMiddle() {
        buttonEvent(BTN_MIDDLE, false);
    }

    @Override
    public void pressRight() {
        buttonEvent(BTN_RIGHT, true);
    }

    @Override
    public void releaseRight() {
        buttonEvent(BTN_RIGHT, false);
    }

    @Override
    public void wheelVerticallyBy(boolean forward, double delta) {
        // forward = away from user = scroll up = button 4
        int button = forward ? 4 : 5;
        int count = Math.max(1, (int) delta);
        for (int i = 0; i < count; i++) {
            buttonEvent(button, true);
            buttonEvent(button, false);
        }
        LibX11.INSTANCE.XFlush(display);
    }

    @Override
    public void wheelHorizontallyBy(boolean forward, double delta) {
        // forward = right = button 7
        int button = forward ? 7 : 6;
        int count = Math.max(1, (int) delta);
        for (int i = 0; i < count; i++) {
            buttonEvent(button, true);
            buttonEvent(button, false);
        }
        LibX11.INSTANCE.XFlush(display);
    }

    @Override
    public void hideCursor() {
        if (hiddenCursor == 0) {
            byte[] blankData = {0};
            long pixmap = LibX11.INSTANCE.XCreateBitmapFromData(display, rootWindow, blankData, 1, 1);
            LibX11.XColor black = new LibX11.XColor();
            hiddenCursor = LibX11.INSTANCE.XCreatePixmapCursor(display, pixmap, pixmap, black, black, 0, 0);
            LibX11.INSTANCE.XFreePixmap(display, pixmap);
        }
        LibX11.INSTANCE.XDefineCursor(display, rootWindow, hiddenCursor);
        LibX11.INSTANCE.XFlush(display);
    }

    @Override
    public void showCursor() {
        LibX11.INSTANCE.XUndefineCursor(display, rootWindow);
        LibX11.INSTANCE.XFlush(display);
    }

    private void buttonEvent(int button, boolean press) {
        LibXTest.INSTANCE.XTestFakeButtonEvent(display, button, press ? 1 : 0, 0);
    }
}
