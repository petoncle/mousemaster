package mousemaster;

import com.sun.jna.platform.win32.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.LongStream;

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
