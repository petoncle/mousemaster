package jmouseable.jmouseable;

import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class WindowsMouse {

    private static final Executor mouseExecutor = Executors.newSingleThreadExecutor();

    public static void moveBy(boolean xForward, double deltaX, boolean yForward,
                              double deltaY) {
        if (((long) deltaX) == 0 && ((long) deltaY) == 0)
            return;
        mouseExecutor.execute(() -> sendInput((long) deltaX * (xForward ? 1 : -1),
                (long) deltaY * (yForward ? 1 : -1), 0, ExtendedUser32.MOUSEEVENTF_MOVE));
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

    public static void attachUp(Attach attach) {
        attach(false, false, attach);
    }

    public static void attachDown(Attach attach) {
        attach(false, true, attach);
    }

    public static void attachLeft(Attach attach) {
        attach(true, false, attach);
    }

    public static void attachRight(Attach attach) {
        attach(true, true, attach);
    }

    private static void attach(boolean horizontal, boolean forward, Attach attach) {
        WinDef.POINT mousePosition = mousePosition();
        WinUser.MONITORINFO monitorInfo =
                WindowsIndicator.findCurrentMonitorPosition(mousePosition);
        int rowWidth = (monitorInfo.rcMonitor.right - monitorInfo.rcMonitor.left) /
                       attach.gridRowCount();
        int columnHeight = (monitorInfo.rcMonitor.bottom - monitorInfo.rcMonitor.top) /
                           attach.gridColumnCount();
        double mouseRow = (double) mousePosition.x / rowWidth;
        double mouseColumn = (double) mousePosition.y / columnHeight;
        if (horizontal)
            mousePosition.x = (int) ((forward ? Math.floor(mouseRow) + 1 :
                    Math.ceil(mouseRow) - 1) * rowWidth);
        else
            mousePosition.y = (int) ((forward ? Math.floor(mouseColumn) + 1 :
                    Math.ceil(mouseColumn) - 1) * columnHeight);
        setMousePosition(mousePosition);
    }

    private static WinDef.POINT mousePosition() {
        ExtendedUser32.CURSORINFO cursorInfo = new ExtendedUser32.CURSORINFO();
        ExtendedUser32.INSTANCE.GetCursorInfo(cursorInfo);
        return cursorInfo.ptScreenPos;
    }

    private static boolean setMousePosition(WinDef.POINT mousePosition) {
        WindowsIndicator.mouseMoved(mousePosition);
        return User32.INSTANCE.SetCursorPos(mousePosition.x, mousePosition.y);
    }

}
