package mousemaster;

import com.sun.jna.platform.win32.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

public class WindowsMouse {

    private static final Logger logger = LoggerFactory.getLogger(WindowsMouse.class);
    private static final Executor mouseExecutor = Executors.newSingleThreadExecutor();

    public static void moveBy(boolean xForward, double deltaX, boolean yForward,
                              double deltaY) {
        if (((long) deltaX) == 0 && ((long) deltaY) == 0)
            return;
        mouseExecutor.execute(() -> sendInput((long) deltaX * (xForward ? 1 : -1),
                (long) deltaY * (yForward ? 1 : -1), 0, ExtendedUser32.MOUSEEVENTF_MOVE));
    }

    /**
     * Moves now, in the current thread.
     */
    public static void synchronousMoveTo(int x, int y) {
        setMousePosition(new WinDef.POINT(x, y));
    }

    private final static AtomicInteger latestX = new AtomicInteger();

    private final static AtomicInteger latestY = new AtomicInteger();

    public static void moveTo(int x, int y) {
        latestX.set(x);
        latestY.set(y);
        mouseExecutor.execute(() -> {
            if (x != latestX.get() || y != latestY.get())
                return;
            setMousePosition(new WinDef.POINT(x, y));
        });
    }

    public static void pressLeft() {
        mouseExecutor.execute(
                () -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_LEFTDOWN));
    }

    public static void pressMiddle() {
        mouseExecutor.execute(
                () -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_MIDDLEDOWN));
    }

    public static void pressRight() {
        mouseExecutor.execute(
                () -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_RIGHTDOWN));
    }

    public static void releaseLeft() {
        mouseExecutor.execute(
                () -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_LEFTUP));
    }

    public static void releaseMiddle() {
        mouseExecutor.execute(
                () -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_MIDDLEUP));
    }

    public static void releaseRight() {
        mouseExecutor.execute(
                () -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_RIGHTUP));
    }

    /**
     * For an unknown reason, SendInput() for wheel inputs can take up to 0.3s to execute.
     * On top of that, if it is executed from the same thread that listens for keyboard and mouse events,
     * the behavior of SendInput() for wheel inputs is erratic.
     */
    private static final Executor wheelExecutor = Executors.newSingleThreadExecutor();

    public static void wheelHorizontallyBy(boolean forward, double delta) {
        wheelExecutor.execute(() -> sendInput(0, 0, (int) delta * (forward ? 1 : -1),
                ExtendedUser32.MOUSEEVENTF_HWHEEL));
    }

    public static void wheelVerticallyBy(boolean forward, double delta) {
        wheelExecutor.execute(() -> sendInput(0, 0, (int) delta * (forward ? -1 : 1),
                ExtendedUser32.MOUSEEVENTF_WHEEL));
    }

    private static void sendInput(long dx, long dy, int wheelDelta, int eventFlag) {
        WinUser.INPUT input = new WinUser.INPUT();
        input.type = new WinDef.DWORD(WinUser.INPUT.INPUT_MOUSE);
        WinUser.MOUSEINPUT mouseInput = new WinUser.MOUSEINPUT();
        input.input.mi = mouseInput;
        input.input.setType(WinUser.MOUSEINPUT.class);
        mouseInput.dx = new WinDef.LONG(dx);
        mouseInput.dy = new WinDef.LONG(dy);
        mouseInput.mouseData = new WinDef.DWORD(wheelDelta);
        mouseInput.dwFlags = new WinDef.DWORD(eventFlag);
        mouseInput.time = new WinDef.DWORD(0);
        mouseInput.dwExtraInfo = new BaseTSD.ULONG_PTR(0L);
        WinDef.DWORD nInputs = new WinDef.DWORD(1);
        WinUser.INPUT[] pInputs = {input};
        int size = input.size();
        User32.INSTANCE.SendInput(nInputs, pInputs, size);
    }

    public static WindowsPlatform windowsPlatform; // TODO Get rid of this field.

    private static boolean setMousePosition(WinDef.POINT mousePosition) {
        windowsPlatform.mousePositionSet(mousePosition);
        return User32.INSTANCE.SetCursorPos(mousePosition.x, mousePosition.y);
    }

    private static boolean cursorHidden = false;

    public static void showCursor() {
        if (!cursorHidden)
            return;
        cursorHidden = false;
        ExtendedUser32.INSTANCE.SystemParametersInfoA(
                new WinDef.UINT(ExtendedUser32.SPI_SETCURSORS), new WinDef.UINT(0), null,
                new WinDef.UINT(0));
    }

    public static void hideCursor() {
        // User32 ShowCursor(false) always returns -1 and does not hide the cursor.
        if (cursorHidden)
            return;
        cursorHidden = true;
        int cursorWidth = mouseSize().width();
        int cursorHeight = mouseSize().height();
        byte[] andMask = new byte[cursorWidth * cursorHeight];
        byte[] xorMask = new byte[cursorWidth * cursorHeight];
        Arrays.fill(andMask, (byte) 0xFF);
        WinDef.HCURSOR transparentCursor =
                ExtendedUser32.INSTANCE.CreateCursor(null, 0, 0, cursorWidth,
                        cursorHeight, andMask, xorMask);
        if (transparentCursor == null)
            throw new IllegalStateException("Unable to create transparent cursor");
        List<WinDef.UINT> cursorIds =
                LongStream.of(32512, 32513, 32514, 32515, 32516, 32640, 32641, 32642, 32643,
                              32644, 32645, 32646, 32648, 32649, 32650, 32651)
                          .mapToObj(WinDef.UINT::new)
                          .toList();
        for (WinDef.UINT cursorId : cursorIds) {
            WinNT.HANDLE imageHandle =
                    ExtendedUser32.INSTANCE.CopyImage(transparentCursor,
                            new WinDef.UINT(ExtendedUser32.IMAGE_CURSOR), 0, 0,
                            new WinDef.UINT(0));
            ExtendedUser32.INSTANCE.SetSystemCursor(imageHandle, cursorId);
        }
    }

    public static WinDef.POINT findMousePosition() {
        WinDef.POINT mousePosition = new WinDef.POINT();
        boolean getCursorPosResult = User32.INSTANCE.GetCursorPos(mousePosition);
        if (!getCursorPosResult)
            throw new IllegalStateException("Unable to find mouse position");
        return mousePosition;
    }

    private static MouseSize mouseSize;

    static MouseSize mouseSize() {
        if (mouseSize != null)
            return mouseSize;
        ExtendedUser32.CURSORINFO cursorInfo = new ExtendedUser32.CURSORINFO();
        int cursorWidth, cursorHeight;
        if (!ExtendedUser32.INSTANCE.GetCursorInfo(cursorInfo) ||
            cursorInfo.hCursor == null)
            throw new IllegalStateException("Unable to find mouse size"); // TODO
        WinGDI.ICONINFO iconInfo = new WinGDI.ICONINFO();
        if (!User32.INSTANCE.GetIconInfo(new WinDef.HICON(cursorInfo.hCursor),
                iconInfo))
            throw new IllegalStateException("Unable to find mouse size"); // TODO
        WinGDI.BITMAP bmp = new WinGDI.BITMAP();
        int sizeOfBitmap = bmp.size();
        if (iconInfo.hbmColor != null) {
            // Get the color bitmap information.
            GDI32.INSTANCE.GetObject(iconInfo.hbmColor, sizeOfBitmap, bmp.getPointer());
        }
        else {
            // Get the mask bitmap information if there is no color bitmap.
            GDI32.INSTANCE.GetObject(iconInfo.hbmMask, sizeOfBitmap, bmp.getPointer());
        }
        bmp.read();
        cursorWidth = bmp.bmWidth.intValue();
        cursorHeight = bmp.bmHeight.intValue();
        // If there is no color bitmap, height is for both the mask and the inverted mask.
        if (iconInfo.hbmColor == null) {
            cursorHeight /= 2;
        }
        if (iconInfo.hbmColor != null)
            GDI32.INSTANCE.DeleteObject(iconInfo.hbmColor);
        if (iconInfo.hbmMask != null)
            GDI32.INSTANCE.DeleteObject(iconInfo.hbmMask);
        MouseSize mouseSize = new MouseSize(cursorWidth, cursorHeight);
        WindowsMouse.mouseSize = mouseSize;
        return mouseSize;
    }

    public record MouseSize(int width, int height) {

    }
}
