package mousemaster.platform.windows;

import mousemaster.*;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.PointerByReference;
import mousemaster.platform.MouseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class WindowsMouseController implements MouseController {

    private static final Logger logger = LoggerFactory.getLogger(WindowsMouseController.class);

    private final Consumer<WinDef.POINT> mousePositionSetCallback;

    public WindowsMouseController(Consumer<WinDef.POINT> mousePositionSetCallback) {
        this.mousePositionSetCallback = mousePositionSetCallback;
    }

    @Override
    public void moveBy(boolean xForward, double deltaX, boolean yForward,
                       double deltaY) {
        if (((long) deltaX) == 0 && ((long) deltaY) == 0)
            return;
        sendInput((long) deltaX * (xForward ? 1 : -1),
                (long) deltaY * (yForward ? 1 : -1), 0,
                ExtendedUser32.MOUSEEVENTF_MOVE);
    }

    // Mutable field that will contain the user's mouse settings
    // (Control panel > Mouse settings > Pointer options tab).
    private final WinDef.DWORD[] originalMouseThresholdsAndAcceleration =
            new WinDef.DWORD[]{
                    new WinDef.DWORD(0), // Threshold1.
                    new WinDef.DWORD(0), // Threshold2.
                    new WinDef.DWORD(0)  // Acceleration.
            };
    private final WinDef.DWORD[] originalMouseSpeed = new WinDef.DWORD[] { new WinDef.DWORD(0) };
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
    private void findMouseThresholdsAndAccelerationAndSpeed(
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

    private void setMouseThresholdsAndAccelerationAndSpeed(
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

    private boolean moving;

    @Override
    public void beginMove() {
        if (moving)
            return;
        findMouseThresholdsAndAccelerationAndSpeed(originalMouseThresholdsAndAcceleration,
                originalMouseSpeed);
        setMouseThresholdsAndAccelerationAndSpeed(zeroMouseThresholdsAndAcceleration,
                tenMouseSpeed);
        moving = true;
    }

    @Override
    public void endMove() {
        if (!moving)
            return;
        moving = false;
        setMouseThresholdsAndAccelerationAndSpeed(originalMouseThresholdsAndAcceleration,
                originalMouseSpeed[0].intValue());
    }

    @Override
    public void synchronousMoveTo(int x, int y) {
        WinDef.POINT mousePosition = tryFindMousePosition();
        // GetCursorPos can fail on the Win+L lock screen.
        if (mousePosition == null) {
            logger.warn("Unable to find mouse position for synchronous move");
            return;
        }
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
     * 	at mousemaster.platform.windows.WindowsMouseController.sendInput(WindowsMouse.java:175)
     * 	at mousemaster.platform.windows.WindowsMouseController.pressLeft(WindowsMouse.java:120)
     * 	at mousemaster.MouseController.pressLeft(MouseController.java:237)
     * 	at mousemaster.CommandRunner.run(CommandRunner.java:38)
     */
    private static final Executor buttonExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void pressLeft() {
        buttonExecutor.execute(() -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_LEFTDOWN));
    }

    @Override
    public void pressMiddle() {
        buttonExecutor.execute(() -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_MIDDLEDOWN));
    }

    @Override
    public void pressRight() {
        buttonExecutor.execute(() -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_RIGHTDOWN));
    }

    @Override
    public void releaseLeft() {
        buttonExecutor.execute(() -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_LEFTUP));
    }

    @Override
    public void releaseMiddle() {
        buttonExecutor.execute(() -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_MIDDLEUP));
    }

    @Override
    public void releaseRight() {
        buttonExecutor.execute(() -> sendInput(0, 0, 0, ExtendedUser32.MOUSEEVENTF_RIGHTUP));
    }

    /**
     * For an unknown reason, SendInput() for wheel inputs can take up to 0.3s to execute.
     * On top of that, if it is executed from the same thread that listens for keyboard and mouse events,
     * the behavior of SendInput() for wheel inputs is erratic.
     */
    private static final Executor wheelExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void wheelHorizontallyBy(boolean forward, double delta) {
        wheelExecutor.execute(() -> sendInput(0, 0, (int) delta * (forward ? 1 : -1),
                ExtendedUser32.MOUSEEVENTF_HWHEEL));
    }

    @Override
    public void wheelVerticallyBy(boolean forward, double delta) {
        wheelExecutor.execute(() -> sendInput(0, 0, (int) delta * (forward ? -1 : 1),
                ExtendedUser32.MOUSEEVENTF_WHEEL));
    }

    private void sendInput(long dx, long dy, int wheelDelta, int eventFlag) {
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

    private boolean setMousePosition(WinDef.POINT mousePosition) {
        mousePositionSetCallback.accept(mousePosition);
        return User32.INSTANCE.SetCursorPos(mousePosition.x, mousePosition.y);
    }

    // 32640 (OCR_SIZE) and 32641 (OCR_ICON) are excluded: they are obsolete
    // cursors that SPI_SETCURSORS does not reload from the registry, so
    // SetSystemCursor for them leaks a GDI handle on every hide/show cycle.
    private static final long[] SYSTEM_CURSOR_IDS = {32512, 32513, 32514, 32515, 32516,
            32642, 32643, 32644, 32645, 32646, 32648, 32649, 32650, 32651};

    // A cursor glyph's XOR/inversion pixels (e.g. the mono I-beam) can't invert a static
    // bitmap, so they are drawn as a white core with a 1px black outline -- readable on any
    // background, the same trick the arrow cursor uses.
    private static final int INVERT_CORE = 255;   // white
    private static final int INVERT_OUTLINE = 0;  // black

    private boolean cursorHidden = false;
    private boolean indicatorCursorInstalled = false;
    private ExtendedKernel32.PhandlerRoutine consoleCtrlHandler;
    private volatile boolean cursorRestoreRequested;
    private volatile boolean cursorRestoreDone;

    @Override
    public void showCursor() {
        if (!cursorHidden && !indicatorCursorInstalled)
            return;
        cursorHidden = false;
        indicatorCursorInstalled = false;
        reloadSystemCursors();
    }

    private boolean reloadSystemCursors() {
        return ExtendedUser32.INSTANCE.SystemParametersInfoA(
                new WinDef.UINT(ExtendedUser32.SPI_SETCURSORS), new WinDef.UINT(0), null,
                new WinDef.UINT(0));
    }

    /**
     * SetSystemCursor persists after the process dies. The native-image shutdown hook does not
     * run on a console close / Ctrl+C, so a console control handler restores the cursor instead.
     */
    private void ensureConsoleCtrlHandler() {
        if (consoleCtrlHandler != null)
            return;
        consoleCtrlHandler = this::consoleCtrlEvent;
        boolean registered =
                ExtendedKernel32.INSTANCE.SetConsoleCtrlHandler(consoleCtrlHandler, true);
        logger.info("Registered cursor-restore console control handler: " + registered +
                    (registered ? "" : " (error " + Native.getLastError() + ")"));
    }

    private boolean consoleCtrlEvent(int dwCtrlType) {
        // SPI_SETCURSORS from this OS-created thread is unreliable; let the main thread restore
        // and block until it has (or times out) before the process terminates.
        cursorRestoreRequested = true;
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (!cursorRestoreDone && System.nanoTime() < deadlineNanos) {
            try {
                Thread.sleep(5);
            }
            catch (InterruptedException e) {
                break;
            }
        }
        if (!cursorRestoreDone)
            reloadSystemCursors(); // Last resort if the main thread did not respond.
        logger.info("Console control event " + dwCtrlType +
                    ": restored system cursors (main-thread done: " + cursorRestoreDone + ")");
        return false; // Let the default handler proceed with termination.
    }

    /** Runs the restore requested by consoleCtrlEvent; called every frame from the main loop. */
    public void processPendingCursorRestore() {
        if (cursorRestoreRequested && !cursorRestoreDone) {
            reloadSystemCursors();
            cursorRestoreDone = true;
        }
    }

    @Override
    public void hideCursor() {
        // User32 ShowCursor(false) always returns -1 and does not hide the cursor.
        if (cursorHidden)
            return;
        ensureConsoleCtrlHandler();
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
        for (long cursorId : SYSTEM_CURSOR_IDS) {
            WinNT.HANDLE imageHandle =
                    ExtendedUser32.INSTANCE.CopyImage(transparentCursor,
                            new WinDef.UINT(ExtendedUser32.IMAGE_CURSOR), 0, 0,
                            new WinDef.UINT(0));
            ExtendedUser32.INSTANCE.SetSystemCursor(imageHandle,
                    new WinDef.UINT(cursorId));
        }
        ExtendedUser32.INSTANCE.DestroyCursor(transparentCursor);
    }

    /** A snapshot of an original system cursor glyph: premultiplied ARGB, hotspot, and
     *  the center of its opaque bounding box (where the indicator is centered). */
    private record GlyphImage(int width, int height, int hotspotX, int hotspotY,
                              int visualCenterX, int visualCenterY,
                              int[] argbPremultiplied) {}

    private final Map<Long, GlyphImage> glyphByCursorId = new HashMap<>();

    /**
     * Installs the indicator (given as a premultiplied-ARGB image) as every system cursor.
     * When includeGlyph is set, each cursor's original glyph is composited on top so shape
     * semantics are preserved; hide-cursor omits the glyph. Either way the indicator is
     * anchored to the glyph's visual center and the glyph's real hotspot is kept, so toggling
     * the glyph does not shift the indicator. The OS keeps switching cursors by context; we
     * just replace each slot's image.
     */
    public void setIndicatorCursor(int[] indicatorArgb, int indicatorWidth, int indicatorHeight,
                                   boolean includeGlyph) {
        ensureConsoleCtrlHandler();
        if (glyphByCursorId.isEmpty())
            snapshotSystemGlyphs();
        for (long cursorId : SYSTEM_CURSOR_IDS) {
            GlyphImage glyph = glyphByCursorId.get(cursorId);
            if (glyph == null)
                continue;
            installCompositeCursor(cursorId, indicatorArgb, indicatorWidth, indicatorHeight,
                    glyph, includeGlyph);
        }
        cursorHidden = false;
        indicatorCursorInstalled = true;
    }

    /**
     * Snapshots every system cursor's glyph once. Restores the pristine system cursors
     * first, so the snapshot captures the real glyphs even if a hidden/composite cursor is
     * currently installed.
     */
    private void snapshotSystemGlyphs() {
        ExtendedUser32.INSTANCE.SystemParametersInfoA(
                new WinDef.UINT(ExtendedUser32.SPI_SETCURSORS), new WinDef.UINT(0), null,
                new WinDef.UINT(0));
        for (long cursorId : SYSTEM_CURSOR_IDS) {
            WinNT.HANDLE cursor = ExtendedUser32.INSTANCE.LoadImageW(null,
                    new Pointer(cursorId), ExtendedUser32.IMAGE_CURSOR, 0, 0,
                    ExtendedUser32.LR_SHARED);
            if (cursor == null)
                continue;
            WinDef.HICON icon = new WinDef.HICON(cursor.getPointer());
            WinGDI.ICONINFO iconInfo = new WinGDI.ICONINFO();
            if (!ExtendedUser32.INSTANCE.GetIconInfo(icon, iconInfo))
                continue;
            try {
                WinGDI.BITMAP bmp = new WinGDI.BITMAP();
                WinNT.HANDLE sizingBitmap =
                        iconInfo.hbmColor != null ? iconInfo.hbmColor : iconInfo.hbmMask;
                GDI32.INSTANCE.GetObject(sizingBitmap, bmp.size(), bmp.getPointer());
                bmp.read();
                int width = bmp.bmWidth.intValue();
                int height = bmp.bmHeight.intValue();
                // Monochrome cursor: mask is double-height (top AND, bottom XOR).
                if (iconInfo.hbmColor == null)
                    height /= 2;
                if (width <= 0 || height <= 0)
                    continue;
                int[] argb = rasterizeGlyph(icon, width, height);
                if (argb != null) {
                    int[] center = opaqueBoundsCenter(argb, width, height);
                    glyphByCursorId.put(cursorId, new GlyphImage(width, height,
                            iconInfo.xHotspot, iconInfo.yHotspot,
                            center[0], center[1], argb));
                }
            }
            finally {
                if (iconInfo.hbmColor != null)
                    GDI32.INSTANCE.DeleteObject(iconInfo.hbmColor);
                if (iconInfo.hbmMask != null)
                    GDI32.INSTANCE.DeleteObject(iconInfo.hbmMask);
            }
        }
    }

    /**
     * Rasterizes a cursor glyph to premultiplied ARGB. Draws it onto a white and a black
     * background and recovers per-pixel alpha (alpha = 255 - (onWhite - onBlack), color =
     * onBlack). XOR/inversion pixels (brighter over black than white, e.g. the mono I-beam)
     * can't invert a static bitmap, so they are drawn as a white core with a black outline.
     */
    private int[] rasterizeGlyph(WinDef.HICON icon, int width, int height) {
        WinGDI.BITMAPINFO bitmapInfo = new WinGDI.BITMAPINFO();
        bitmapInfo.bmiHeader.biSize = bitmapInfo.bmiHeader.size();
        bitmapInfo.bmiHeader.biWidth = width;
        bitmapInfo.bmiHeader.biHeight = -height; // Top-down.
        bitmapInfo.bmiHeader.biPlanes = 1;
        bitmapInfo.bmiHeader.biBitCount = 32;
        bitmapInfo.bmiHeader.biCompression = WinGDI.BI_RGB;
        WinDef.HDC dc = GDI32.INSTANCE.CreateCompatibleDC(null);
        PointerByReference bitsRef = new PointerByReference();
        WinDef.HBITMAP dib = ExtendedGDI32.INSTANCE.CreateDIBSection(
                dc, bitmapInfo, WinGDI.DIB_RGB_COLORS, bitsRef, null, 0);
        if (dib == null) {
            GDI32.INSTANCE.DeleteDC(dc);
            return null;
        }
        WinNT.HANDLE previous = GDI32.INSTANCE.SelectObject(dc, dib);
        Pointer bits = bitsRef.getValue();
        long byteCount = (long) width * height * 4;
        byte[] onWhite = drawGlyphOnBackground(dc, icon, bits, byteCount, (byte) 0xFF);
        byte[] onBlack = drawGlyphOnBackground(dc, icon, bits, byteCount, (byte) 0x00);
        GDI32.INSTANCE.SelectObject(dc, previous);
        GDI32.INSTANCE.DeleteObject(dib);
        GDI32.INSTANCE.DeleteDC(dc);
        int[] argb = new int[width * height];
        boolean[] invert = new boolean[width * height];
        for (int i = 0; i < argb.length; i++) {
            int o = i * 4;
            int wB = onWhite[o] & 0xFF, wG = onWhite[o + 1] & 0xFF, wR = onWhite[o + 2] & 0xFF;
            int bB = onBlack[o] & 0xFF, bG = onBlack[o + 1] & 0xFF, bR = onBlack[o + 2] & 0xFF;
            if (bB + bG + bR > wB + wG + wR + 16) {
                invert[i] = true;
                argb[i] = (255 << 24) | (INVERT_CORE << 16) | (INVERT_CORE << 8) | INVERT_CORE;
            }
            else {
                int a = Math.max(Math.max(255 - (wB - bB), 255 - (wG - bG)), 255 - (wR - bR));
                a = Math.max(0, Math.min(255, a));
                int r = Math.min(bR, a), g = Math.min(bG, a), b = Math.min(bB, a);
                argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        outlineInvertPixels(argb, invert, width, height);
        return argb;
    }

    /** Paints a 1px outline around invert pixels: any clear pixel touching the invert core
     *  becomes opaque outline ink, so an uninvertable glyph reads on any background. */
    private void outlineInvertPixels(int[] argb, boolean[] invert, int width, int height) {
        int outline = (255 << 24) | (INVERT_OUTLINE << 16) | (INVERT_OUTLINE << 8) | INVERT_OUTLINE;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                if (invert[i] || ((argb[i] >>> 24) & 0xFF) != 0)
                    continue;
                if (hasInvertNeighbor(invert, x, y, width, height))
                    argb[i] = outline;
            }
        }
    }

    private boolean hasInvertNeighbor(boolean[] invert, int x, int y, int width, int height) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0)
                    continue;
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height && invert[ny * width + nx])
                    return true;
            }
        }
        return false;
    }

    /** Center of the glyph's opaque bounding box, or the geometric center if fully clear. */
    private int[] opaqueBoundsCenter(int[] argb, int width, int height) {
        int minX = width, maxX = -1, minY = height, maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (((argb[y * width + x] >>> 24) & 0xFF) != 0) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX)
            return new int[]{width / 2, height / 2};
        return new int[]{(minX + maxX + 1) / 2, (minY + maxY + 1) / 2};
    }

    private byte[] drawGlyphOnBackground(WinDef.HDC dc, WinDef.HICON icon, Pointer bits,
                                         long byteCount, byte fill) {
        bits.setMemory(0, byteCount, fill);
        ExtendedUser32.INSTANCE.DrawIconEx(dc, 0, 0, icon, 0, 0, 0, null,
                ExtendedUser32.DI_NORMAL);
        ExtendedGDI32.INSTANCE.GdiFlush();
        return bits.getByteArray(0, (int) byteCount);
    }

    /** Composites the indicator (centered on the glyph's visual center) under the glyph and
     *  installs the result as the system cursor for the given id, keeping the glyph's
     *  real hotspot so clicks still land correctly. When includeGlyph is false the glyph
     *  pixels are omitted (hide-cursor), but its hotspot and visual center still anchor the
     *  indicator, so the indicator stays put when the glyph is toggled. */
    private void installCompositeCursor(long cursorId, int[] indicatorArgb, int indicatorWidth,
                                        int indicatorHeight, GlyphImage glyph,
                                        boolean includeGlyph) {
        int indicatorCenterX = indicatorWidth / 2;
        int indicatorCenterY = indicatorHeight / 2;
        // Extents relative to the hotspot; the indicator is centered on the glyph's visual
        // center so it sits where the window overlay would place it.
        int indicatorCenterRelX = glyph.visualCenterX - glyph.hotspotX;
        int indicatorCenterRelY = glyph.visualCenterY - glyph.hotspotY;
        int minX = Math.min(-glyph.hotspotX, indicatorCenterRelX - indicatorCenterX);
        int minY = Math.min(-glyph.hotspotY, indicatorCenterRelY - indicatorCenterY);
        int maxX = Math.max(glyph.width - glyph.hotspotX, indicatorCenterRelX + (indicatorWidth - indicatorCenterX));
        int maxY = Math.max(glyph.height - glyph.hotspotY, indicatorCenterRelY + (indicatorHeight - indicatorCenterY));
        int canvasWidth = maxX - minX;
        int canvasHeight = maxY - minY;
        int left = -minX;
        int top = -minY;
        int indicatorOriginX = left + indicatorCenterRelX - indicatorCenterX;
        int indicatorOriginY = top + indicatorCenterRelY - indicatorCenterY;
        int glyphOriginX = left - glyph.hotspotX;
        int glyphOriginY = top - glyph.hotspotY;
        byte[] bgra = new byte[canvasWidth * canvasHeight * 4];
        for (int y = 0; y < canvasHeight; y++) {
            for (int x = 0; x < canvasWidth; x++) {
                int indicatorPremB = 0, indicatorPremG = 0, indicatorPremR = 0, indicatorA = 0;
                int dx = x - indicatorOriginX, dy = y - indicatorOriginY;
                if (dx >= 0 && dx < indicatorWidth && dy >= 0 && dy < indicatorHeight) {
                    // indicatorArgb is already premultiplied.
                    int p = indicatorArgb[dy * indicatorWidth + dx];
                    indicatorA = (p >>> 24) & 0xFF;
                    indicatorPremR = (p >>> 16) & 0xFF;
                    indicatorPremG = (p >>> 8) & 0xFF;
                    indicatorPremB = p & 0xFF;
                }
                int glyphPremB = 0, glyphPremG = 0, glyphPremR = 0, glyphA = 0;
                int gx = x - glyphOriginX, gy = y - glyphOriginY;
                if (includeGlyph && gx >= 0 && gx < glyph.width && gy >= 0 && gy < glyph.height) {
                    int p = glyph.argbPremultiplied[gy * glyph.width + gx];
                    glyphA = (p >>> 24) & 0xFF;
                    glyphPremR = (p >>> 16) & 0xFF;
                    glyphPremG = (p >>> 8) & 0xFF;
                    glyphPremB = p & 0xFF;
                }
                int inv = 255 - glyphA;
                int outPremB = glyphPremB + indicatorPremB * inv / 255;
                int outPremG = glyphPremG + indicatorPremG * inv / 255;
                int outPremR = glyphPremR + indicatorPremR * inv / 255;
                int outA = glyphA + indicatorA * inv / 255;
                int o = (y * canvasWidth + x) * 4;
                // Composite in premultiplied space, then store STRAIGHT (un-premultiplied)
                // color: CreateIconIndirect alpha-blends the DIB as straight alpha, so
                // premultiplied color would darken every semi-transparent pixel to black.
                if (outA == 0) {
                    bgra[o] = 0;
                    bgra[o + 1] = 0;
                    bgra[o + 2] = 0;
                    bgra[o + 3] = 0;
                }
                else {
                    bgra[o] = (byte) Math.min(255, outPremB * 255 / outA);
                    bgra[o + 1] = (byte) Math.min(255, outPremG * 255 / outA);
                    bgra[o + 2] = (byte) Math.min(255, outPremR * 255 / outA);
                    bgra[o + 3] = (byte) outA;
                }
            }
        }
        WinDef.HBITMAP colorBitmap = create32bppDib(canvasWidth, canvasHeight, bgra);
        if (colorBitmap == null)
            return;
        WinDef.HBITMAP mask =
                ExtendedGDI32.INSTANCE.CreateBitmap(canvasWidth, canvasHeight, 1, 1, null);
        WinGDI.ICONINFO iconInfo = new WinGDI.ICONINFO();
        iconInfo.fIcon = false;
        iconInfo.xHotspot = left;
        iconInfo.yHotspot = top;
        iconInfo.hbmMask = mask;
        iconInfo.hbmColor = colorBitmap;
        WinDef.HICON composite = ExtendedUser32.INSTANCE.CreateIconIndirect(iconInfo);
        GDI32.INSTANCE.DeleteObject(colorBitmap);
        GDI32.INSTANCE.DeleteObject(mask);
        // SetSystemCursor takes ownership of and destroys the icon we pass.
        if (composite != null)
            ExtendedUser32.INSTANCE.SetSystemCursor(composite, new WinDef.UINT(cursorId));
    }

    /** Creates a top-down 32bpp BGRA DIB section and fills it with the given pixels. */
    private WinDef.HBITMAP create32bppDib(int width, int height, byte[] bgra) {
        WinGDI.BITMAPINFO bitmapInfo = new WinGDI.BITMAPINFO();
        bitmapInfo.bmiHeader.biSize = bitmapInfo.bmiHeader.size();
        bitmapInfo.bmiHeader.biWidth = width;
        bitmapInfo.bmiHeader.biHeight = -height;
        bitmapInfo.bmiHeader.biPlanes = 1;
        bitmapInfo.bmiHeader.biBitCount = 32;
        bitmapInfo.bmiHeader.biCompression = WinGDI.BI_RGB;
        PointerByReference bitsRef = new PointerByReference();
        WinDef.HBITMAP dib = ExtendedGDI32.INSTANCE.CreateDIBSection(
                null, bitmapInfo, WinGDI.DIB_RGB_COLORS, bitsRef, null, 0);
        if (dib != null)
            bitsRef.getValue().write(0, bgra, 0, bgra.length);
        return dib;
    }

    public WinDef.POINT findMousePosition() {
        WinDef.POINT mousePosition = tryFindMousePosition();
        if (mousePosition == null)
            throw new IllegalStateException("Unable to find mouse position");
        return mousePosition;
    }

    public WinDef.POINT tryFindMousePosition() {
        WinDef.POINT mousePosition = new WinDef.POINT();
        boolean getCursorPosResult = User32.INSTANCE.GetCursorPos(mousePosition);
        if (!getCursorPosResult)
            return null;
        return mousePosition;
    }

    private MouseSize mouseSize;

    MouseSize mouseSize() {
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
        this.mouseSize = mouseSize;
        return mouseSize;
    }

    private MouseSize mouseSizeFallback() {
        Screen activeScreen = WindowsScreen.findActiveScreen(findMousePosition());
        double scale = activeScreen.scale();
        logger.info("Unable to find mouse size, using 32x32 (multiplied by scale " +
                    scale + ") as temporary fallback");
        return new MouseSize((int) (scale * 32), (int) (scale * 32));
    }

    public record MouseSize(int width, int height) {

    }

    private final Map<Pointer, Point> centerByCursorHandle = new HashMap<>();

    /**
     * Returns the visual center of the current cursor relative to its hotspot,
     * computed by finding the bounding box of non-transparent pixels.
     * Results are cached per cursor handle.
     */
    Point cursorVisualCenter() {
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

    private Point computeCursorVisualCenter(
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
    private Rectangle colorBitmapBounds(WinDef.HBITMAP bitmap) {
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
    private Rectangle maskBitmapBounds(WinDef.HBITMAP bitmap, boolean andOnly) {
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

    private Memory readBitmap32(WinDef.HBITMAP bitmap, int width, int height) {
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
