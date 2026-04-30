package mousemaster;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class WindowsMouse {

    private static final Logger logger = LoggerFactory.getLogger(WindowsMouse.class);

    public static void moveBy(boolean xForward, double deltaX, boolean yForward,
                              double deltaY) {
        if (((long) deltaX) == 0 && ((long) deltaY) == 0)
            return;
        sendInput((long) deltaX * (xForward ? 1 : -1),
                (long) deltaY * (yForward ? 1 : -1), 0,
                ExtendedUser32.MOUSEEVENTF_MOVE);
    }

    // Mutable field that will contain the user's mouse settings
    // (Control panel > Mouse settings > Pointer options tab).
    private static final WinDef.DWORD[] originalMouseThresholdsAndAcceleration =
            new WinDef.DWORD[]{
                    new WinDef.DWORD(0), // Threshold1.
                    new WinDef.DWORD(0), // Threshold2.
                    new WinDef.DWORD(0)  // Acceleration.
            };
    private static final WinDef.DWORD[] originalMouseSpeed = new WinDef.DWORD[] { new WinDef.DWORD(0) };
    // Following is a "no enhanced pointer precision" mouse setting that we use to be
    // able to predict where the mouse will end up when moving the mouse to a target
    // position using SendInput.
    private static final WinDef.DWORD[] zeroMouseThresholdsAndAcceleration =
            new WinDef.DWORD[]{
                    new WinDef.DWORD(0),
                    new WinDef.DWORD(0),
                    new WinDef.DWORD(0)
            };
    private static final int tenMouseSpeed = 10;

    /**
     * On my computer, with enhanced pointer precision enabled,
     * the values returned by SPI_GETMOUSE are:
     * Threshold 1: 6
     * Threshold 2: 10
     * Acceleration: 1
     * If enhanced pointer precision is disabled, then the values are all 0.
     * On my computer, SPI_GETMOUSESPEED is 10 (that's the default).
     */
    private static void findMouseThresholdsAndAccelerationAndSpeed(
            WinDef.DWORD[] mouseThresholdsAndAcceleration,
            WinDef.DWORD[] originalMouseSpeed) {
        boolean getMouseSuccess = ExtendedUser32.INSTANCE.SystemParametersInfoA(
                new WinDef.UINT(ExtendedUser32.SPI_GETMOUSE),
                new WinDef.UINT(0),
                mouseThresholdsAndAcceleration,
                new WinDef.UINT(0)
        );
        boolean getMousespeedSuccess = ExtendedUser32.INSTANCE.SystemParametersInfoA(
                new WinDef.UINT(ExtendedUser32.SPI_GETMOUSESPEED),
                new WinDef.UINT(0),
                originalMouseSpeed,
                new WinDef.UINT(0)
        );
        if (getMouseSuccess && getMousespeedSuccess) {
            if (logger.isTraceEnabled())
                logger.trace("SPI_GETMOUSE Threshold 1 = " + mouseThresholdsAndAcceleration[0].intValue()
                             + ", Threshold 2 = " + mouseThresholdsAndAcceleration[1].intValue()
                             + ", Acceleration: " + mouseThresholdsAndAcceleration[2].intValue()
                             + ", SPI_GETMOUSESPEED = " + originalMouseSpeed[0]);
        } else {
            logger.error("Unable to call get mouse thresholds, acceleration and speed");
        }
    }

    private static void setMouseThresholdsAndAccelerationAndSpeed(
            WinDef.DWORD[] mouseThresholdsAndAcceleration, int mouseSpeed) {
        int SPIF_SENDCHANGE = 0x02;
        boolean setMouseSuccess = ExtendedUser32.INSTANCE.SystemParametersInfoA(
                new WinDef.UINT(ExtendedUser32.SPI_SETMOUSE),
                new WinDef.UINT(0),
                mouseThresholdsAndAcceleration,
                new WinDef.UINT(SPIF_SENDCHANGE)
        );
        boolean setMousespeedSuccess = ExtendedUser32.INSTANCE.SystemParametersInfoA(
                new WinDef.UINT(ExtendedUser32.SPI_SETMOUSESPEED),
                new WinDef.UINT(0),
                mouseSpeed,
                new WinDef.UINT(SPIF_SENDCHANGE)
        );
        if (!setMouseSuccess || !setMousespeedSuccess) {
            logger.error("Unable to set mouse thresholds, acceleration and speed");
        }
    }

    private static boolean moving;

    public static void beginMove() {
        if (moving)
            return;
        findMouseThresholdsAndAccelerationAndSpeed(originalMouseThresholdsAndAcceleration,
                originalMouseSpeed);
        setMouseThresholdsAndAccelerationAndSpeed(zeroMouseThresholdsAndAcceleration,
                tenMouseSpeed);
        moving = true;
    }

    public static void endMove() {
        if (!moving)
            return;
        moving = false;
        setMouseThresholdsAndAccelerationAndSpeed(originalMouseThresholdsAndAcceleration,
                originalMouseSpeed[0].intValue());
    }

    public static void synchronousMoveTo(int x, int y) {
        WinDef.POINT mousePosition = findMousePosition();
        Screen activeScreen = WindowsScreen.findActiveScreen(mousePosition);
        // TODO What if the move is across multiple screens?
        double scaledDeltaX = Math.abs(x - mousePosition.x) / activeScreen.scale();
        double scaledDeltaY = Math.abs(y - mousePosition.y) / activeScreen.scale();
        moveBy(x > mousePosition.x, scaledDeltaX, y > mousePosition.y, scaledDeltaY);
        // Absolute positioning necessary to be pixel perfect (Or is there another way?).
        // I think this is only a problem if the screen scale is not 1?
        // This moves only after received mouse hook event (from the moveBy).
        setMousePosition(new WinDef.POINT(x, y));
    }

    /**
     * Windows keyboard events can be called when we call SendInput()
     * (JNA uses our call to SendInput() as an opportunity to run callbacks?).
     * That is why we execute some SendInput() in a thread other than the main thread.
     * That seems to be enough to prevent the unwanted behavior.
     * java.lang.IllegalStateException: singleKeyEventInProgress = true
     * 	at mousemaster.KeyboardManager.singleKeyEvent(KeyboardManager.java:83)
     * 	at mousemaster.KeyboardManager.keyEvent(KeyboardManager.java:76)
     * 	at mousemaster.WindowsPlatform.keyEvent(WindowsPlatform.java:282)
     * 	at mousemaster.WindowsPlatform.keyboardHookCallback(WindowsPlatform.java:265)
     * 	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
     * 	at java.base/java.lang.reflect.Method.invoke(Method.java:580)
     * 	at com.sun.jna.CallbackReference$DefaultCallbackProxy.invokeCallback(CallbackReference.java:585)
     * 	at com.sun.jna.CallbackReference$DefaultCallbackProxy.callback(CallbackReference.java:616)
     * 	at com.sun.jna.Native.invokeInt(Native Method)
     * 	at com.sun.jna.Function.invoke(Function.java:426)
     * 	at com.sun.jna.Function.invoke(Function.java:361)
     * 	at com.sun.jna.Library$Handler.invoke(Library.java:270)
     * 	at jdk.proxy2/jdk.proxy2.$Proxy6.SendInput(Unknown Source)
     * 	at mousemaster.WindowsMouse.sendInput(WindowsMouse.java:175)
     * 	at mousemaster.WindowsMouse.pressLeft(WindowsMouse.java:120)
     * 	at mousemaster.MouseController.pressLeft(MouseController.java:237)
     * 	at mousemaster.CommandRunner.run(CommandRunner.java:38)
     */
    private static final Executor buttonExecutor = Executors.newSingleThreadExecutor();

    public static void pressLeft() {
        buttonExecutor.execute(() -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_LEFTDOWN));
    }

    public static void pressMiddle() {
        buttonExecutor.execute(() -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_MIDDLEDOWN));
    }

    public static void pressRight() {
        buttonExecutor.execute(() -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_RIGHTDOWN));
    }

    public static void releaseLeft() {
        buttonExecutor.execute(() -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_LEFTUP));
    }

    public static void releaseMiddle() {
        buttonExecutor.execute(() -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_MIDDLEUP));
    }

    public static void releaseRight() {
        buttonExecutor.execute(() -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_RIGHTUP));
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
        if (transparentCursor == null) {
            logger.warn("Unable to create transparent cursor");
            cursorHidden = false;
            return;
        }
        // 32640 (OCR_SIZE) and 32641 (OCR_ICON) are excluded: they are obsolete
        // cursors that SPI_SETCURSORS does not reload from the registry, so
        // SetSystemCursor for them leaks a GDI handle on every hide/show cycle.
        long[] cursorIds = {32512, 32513, 32514, 32515, 32516,
                32642, 32643, 32644, 32645, 32646, 32648, 32649, 32650, 32651};
        for (long cursorId : cursorIds) {
            WinNT.HANDLE imageHandle =
                    ExtendedUser32.INSTANCE.CopyImage(transparentCursor,
                            new WinDef.UINT(ExtendedUser32.IMAGE_CURSOR), 0, 0,
                            new WinDef.UINT(0));
            ExtendedUser32.INSTANCE.SetSystemCursor(imageHandle,
                    new WinDef.UINT(cursorId));
        }
        ExtendedUser32.INSTANCE.DestroyCursor(transparentCursor);
    }

    public static WinDef.POINT findMousePosition() {
        WinDef.POINT mousePosition = tryFindMousePosition();
        if (mousePosition == null)
            throw new IllegalStateException("Unable to find mouse position");
        return mousePosition;
    }

    public static WinDef.POINT tryFindMousePosition() {
        WinDef.POINT mousePosition = new WinDef.POINT();
        boolean getCursorPosResult = User32.INSTANCE.GetCursorPos(mousePosition);
        if (!getCursorPosResult)
            return null;
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
            return mouseSizeFallback();
        WinGDI.ICONINFO iconInfo = new WinGDI.ICONINFO();
        if (!User32.INSTANCE.GetIconInfo(new WinDef.HICON(cursorInfo.hCursor),
                iconInfo))
            return mouseSizeFallback();
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

    private static MouseSize mouseSizeFallback() {
        Screen activeScreen = WindowsScreen.findActiveScreen(findMousePosition());
        double scale = activeScreen.scale();
        logger.info("Unable to find mouse size, using 32x32 (multiplied by scale " +
                    scale + ") as temporary fallback");
        return new MouseSize((int) (scale * 32), (int) (scale * 32));
    }

    public record MouseSize(int width, int height) {

    }

    private static final Map<Pointer, Point> centerByCursorHandle = new HashMap<>();

    /**
     * Returns the visual center of the current cursor relative to its hotspot,
     * computed by finding the bounding box of non-transparent pixels.
     * Results are cached per cursor handle.
     */
    static Point cursorVisualCenter() {
        ExtendedUser32.CURSORINFO cursorInfo = new ExtendedUser32.CURSORINFO();
        if (!ExtendedUser32.INSTANCE.GetCursorInfo(cursorInfo) ||
            cursorInfo.hCursor == null)
            return new Point(0, 0);
        Pointer cursorHandle = cursorInfo.hCursor.getPointer();
        Point cached = centerByCursorHandle.get(cursorHandle);
        if (cached != null)
            return cached;
        Point center = computeCursorVisualCenter(cursorInfo);
        if (center != null)
            centerByCursorHandle.put(cursorHandle, center);
        return center != null ? center : new Point(0, 0);
    }

    private static Point computeCursorVisualCenter(
            ExtendedUser32.CURSORINFO cursorInfo) {
        WinGDI.ICONINFO iconInfo = new WinGDI.ICONINFO();
        if (!User32.INSTANCE.GetIconInfo(
                new WinDef.HICON(cursorInfo.hCursor), iconInfo))
            return null;
        try {
            int hotspotX = iconInfo.xHotspot;
            int hotspotY = iconInfo.yHotspot;
            // Try color bitmap first (alpha-based detection).
            Rectangle bounds = null;
            String method = "none";
            if (iconInfo.hbmColor != null) {
                bounds = colorBitmapBounds(iconInfo.hbmColor);
                if (bounds != null)
                    method = "color";
            }
            // Fall back to mask bitmap if color had no visible pixels
            // or no color bitmap exists.
            if (bounds == null && iconInfo.hbmMask != null) {
                boolean hasColorBitmap = iconInfo.hbmColor != null;
                bounds = maskBitmapBounds(iconInfo.hbmMask, hasColorBitmap);
                if (bounds != null)
                    method = hasColorBitmap ? "mask-and" : "mask-andxor";
            }
            if (bounds == null)
                return null;
            double centerX = bounds.x() + bounds.width() / 2.0 - hotspotX;
            double centerY = bounds.y() + bounds.height() / 2.0 - hotspotY;
            logger.info("Cursor visual center: method={}, bounds={}x{} at ({},{}), hotspot=({},{}), center=({},{})",
                    method, bounds.width(), bounds.height(), bounds.x(), bounds.y(),
                    hotspotX, hotspotY, centerX, centerY);
            return new Point(centerX, centerY);
        }
        finally {
            if (iconInfo.hbmColor != null)
                GDI32.INSTANCE.DeleteObject(iconInfo.hbmColor);
            if (iconInfo.hbmMask != null)
                GDI32.INSTANCE.DeleteObject(iconInfo.hbmMask);
        }
    }

    /**
     * Returns the bounding box of visible pixels in a color bitmap.
     * Tries alpha > 0 first; if no pixels found, falls back to
     * checking for non-black RGB (for XOR/inversion cursors like I-beam where
     * alpha is 0 but RGB encodes the visible shape).
     */
    private static Rectangle colorBitmapBounds(WinDef.HBITMAP bitmap) {
        WinGDI.BITMAP bmp = new WinGDI.BITMAP();
        GDI32.INSTANCE.GetObject(bitmap, bmp.size(), bmp.getPointer());
        bmp.read();
        int width = bmp.bmWidth.intValue();
        int height = bmp.bmHeight.intValue();
        if (width <= 0 || height <= 0)
            return null;
        try (Memory pixels = readBitmap32(bitmap, width, height)) {
            if (pixels == null)
                return null;
            // First pass: alpha > 0 (standard 32-bit alpha cursors).
            int minX = width, maxX = 0, minY = height, maxY = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int alpha = pixels.getByte(((long) y * width + x) * 4 + 3) & 0xFF;
                    if (alpha > 0) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            if (maxX >= minX)
                return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
            // Second pass: non-black RGB (XOR/inversion cursors with alpha=0).
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    long offset = ((long) y * width + x) * 4;
                    boolean nonBlack =
                            (pixels.getByte(offset) & 0xFF) != 0 ||
                            (pixels.getByte(offset + 1) & 0xFF) != 0 ||
                            (pixels.getByte(offset + 2) & 0xFF) != 0;
                    if (nonBlack) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            return maxX >= minX ? new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1) : null;
        }
    }

    /**
     * Returns bounding rectangle of visible pixels in a mask bitmap.
     * @param andOnly true for color cursors (single-height AND-only mask: AND=0 is visible).
     *               false for monochrome cursors (double-height: top=AND, bottom=XOR;
     *               visible if AND=0 or XOR is non-zero).
     */
    private static Rectangle maskBitmapBounds(WinDef.HBITMAP bitmap, boolean andOnly) {
        WinGDI.BITMAP bmp = new WinGDI.BITMAP();
        GDI32.INSTANCE.GetObject(bitmap, bmp.size(), bmp.getPointer());
        bmp.read();
        int width = bmp.bmWidth.intValue();
        int fullHeight = bmp.bmHeight.intValue();
        int height = andOnly ? fullHeight : fullHeight / 2;
        if (width <= 0 || height <= 0)
            return null;
        int readHeight = andOnly ? height : fullHeight;
        try (Memory pixels = readBitmap32(bitmap, width, readHeight)) {
            if (pixels == null)
                return null;
            int minX = width, maxX = 0, minY = height, maxY = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    long andOffset = ((long) y * width + x) * 4;
                    boolean andOpaque =
                            (pixels.getByte(andOffset) & 0xFF) == 0 &&
                            (pixels.getByte(andOffset + 1) & 0xFF) == 0 &&
                            (pixels.getByte(andOffset + 2) & 0xFF) == 0;
                    boolean visible;
                    if (andOnly) {
                        visible = andOpaque;
                    } else {
                        long xorOffset = ((long) (y + height) * width + x) * 4;
                        boolean xorVisible =
                                (pixels.getByte(xorOffset) & 0xFF) != 0 ||
                                (pixels.getByte(xorOffset + 1) & 0xFF) != 0 ||
                                (pixels.getByte(xorOffset + 2) & 0xFF) != 0;
                        visible = andOpaque || xorVisible;
                    }
                    if (visible) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            return maxX >= minX ? new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1) : null;
        }
    }

    private static Memory readBitmap32(WinDef.HBITMAP bitmap, int width, int height) {
        WinGDI.BITMAPINFO bitmapInfo = new WinGDI.BITMAPINFO();
        bitmapInfo.bmiHeader.biWidth = width;
        bitmapInfo.bmiHeader.biHeight = -height; // Negative = top-down
        bitmapInfo.bmiHeader.biPlanes = 1;
        bitmapInfo.bmiHeader.biBitCount = 32;
        Memory pixels = new Memory((long) width * height * 4);
        WinDef.HDC hdc = GDI32.INSTANCE.CreateCompatibleDC(null);
        int result = GDI32.INSTANCE.GetDIBits(
                hdc, bitmap, 0, height, pixels, bitmapInfo,
                WinGDI.DIB_RGB_COLORS);
        GDI32.INSTANCE.DeleteDC(hdc);
        return result != 0 ? pixels : null;
    }
}
