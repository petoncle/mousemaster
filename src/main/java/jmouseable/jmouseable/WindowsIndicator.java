package jmouseable.jmouseable;

import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import jmouseable.jmouseable.WindowsMouse.CursorPositionAndSize;

public class WindowsIndicator {

    private static final int indicatorEdgeThreshold = 100; // in pixels
    private static final int indicatorSize = 16;

    private static WinDef.HWND indicatorWindowHwnd;
    private static boolean mustShowOnceCreated;
    private static boolean showing;
    private static String currentHexColor;
    private static WinDef.POINT mousePosition;

    private static int bestIndicatorX(int mouseX, int cursorWidth, int monitorLeft,
                                      int monitorRight) {
        mouseX = Math.min(monitorRight, Math.max(monitorLeft, mouseX));
        boolean isNearLeftEdge = mouseX <= (monitorLeft + indicatorEdgeThreshold);
        boolean isNearRightEdge = mouseX >= (monitorRight - indicatorEdgeThreshold);
        if (isNearRightEdge)
            return mouseX - indicatorSize;
        return mouseX + cursorWidth / 2;
    }

    private static int bestIndicatorY(int mouseY, int cursorHeight, int monitorTop,
                                      int monitorBottom) {
        mouseY = Math.min(monitorBottom, Math.max(monitorTop, mouseY));
        boolean isNearBottomEdge = mouseY >= (monitorBottom - indicatorEdgeThreshold);
        boolean isNearTopEdge = mouseY <= (monitorTop + indicatorEdgeThreshold);
        if (isNearBottomEdge)
            return mouseY - indicatorSize;
        return mouseY + cursorHeight / 2;
    }

    public static void createIndicatorWindow() {
        CursorPositionAndSize cursorPositionAndSize = WindowsMouse.cursorPositionAndSize();
        WinUser.MONITORINFO monitorInfo = findCurrentMonitorPosition(cursorPositionAndSize.position());
        WinUser.WNDCLASSEX wClass = new WinUser.WNDCLASSEX();
        wClass.hbrBackground = null;
        wClass.lpszClassName = "JMouseableOverlayClassName";
        wClass.lpfnWndProc =
                (WinUser.WindowProc) WindowsIndicator::indicatorWindowCallback;
        User32.INSTANCE.RegisterClassEx(wClass);
        indicatorWindowHwnd = User32.INSTANCE.CreateWindowEx(
                User32.WS_EX_TOPMOST | ExtendedUser32.WS_EX_TOOLWINDOW |
                ExtendedUser32.WS_EX_NOACTIVATE, wClass.lpszClassName,
                "JMouseableOverlayWindowName", WinUser.WS_POPUP,
                bestIndicatorX(cursorPositionAndSize.position().x, cursorPositionAndSize.width(),
                        monitorInfo.rcMonitor.left, monitorInfo.rcMonitor.right),
                bestIndicatorY(cursorPositionAndSize.position().y, cursorPositionAndSize.height(),
                        monitorInfo.rcMonitor.top, monitorInfo.rcMonitor.bottom),
                indicatorSize, indicatorSize, null, null, wClass.hInstance, null);
        if (mustShowOnceCreated)
            show(currentHexColor);
    }

    private static WinDef.LRESULT indicatorWindowCallback(WinDef.HWND hwnd, int uMsg,
                                                          WinDef.WPARAM wParam,
                                                          WinDef.LPARAM lParam) {
        switch (uMsg) {
            case WinUser.WM_PAINT:
                ExtendedUser32.PAINTSTRUCT ps = new ExtendedUser32.PAINTSTRUCT();
                WinDef.RECT rect = new WinDef.RECT();
                WinDef.HBRUSH hbrBackground = ExtendedGDI32.INSTANCE.CreateSolidBrush(
                        hexColorStringToInt(currentHexColor));
                WinDef.HDC hdc = ExtendedUser32.INSTANCE.BeginPaint(hwnd, ps);
                User32.INSTANCE.GetClientRect(hwnd, rect);
                ExtendedUser32.INSTANCE.FillRect(hdc, rect, hbrBackground);
                ExtendedUser32.INSTANCE.EndPaint(hwnd, ps);
                GDI32.INSTANCE.DeleteObject(hbrBackground);
                break;
        }
        return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
    }

    private static int hexColorStringToInt(String hexColor) {
        if (hexColor.startsWith("#"))
            hexColor = hexColor.substring(1);
        int colorInt = Integer.parseUnsignedInt(hexColor, 16);
        // In COLORREF, the order is 0x00BBGGRR, so we need to reorder the components.
        int red = (colorInt >> 16) & 0xFF;
        int green = (colorInt >> 8) & 0xFF;
        int blue = colorInt & 0xFF;
        return (blue << 16) | (green << 8) | red;
    }

    public static void show(String hexColor) {
        if (showing && currentHexColor != null && currentHexColor.equals(hexColor))
            return;
        currentHexColor = hexColor;
        if (hexColor == null) {
            hide();
            return;
        }
        if (indicatorWindowHwnd == null) {
            mustShowOnceCreated = true;
            return;
        }
        showing = true;
        // Force window to repaint to reflect new color
        User32.INSTANCE.InvalidateRect(indicatorWindowHwnd, null, true);
        User32.INSTANCE.UpdateWindow(indicatorWindowHwnd);
        User32.INSTANCE.ShowWindow(indicatorWindowHwnd, WinUser.SW_SHOWNORMAL);
    }

    public static void hide() {
        if (!showing)
            return;
        showing = false;
        User32.INSTANCE.ShowWindow(indicatorWindowHwnd, WinUser.SW_HIDE);
    }

    public static WinUser.MONITORINFO findCurrentMonitorPosition(
            WinDef.POINT mousePosition) {
        WinUser.HMONITOR hMonitor = User32.INSTANCE.MonitorFromPoint(
                new WinDef.POINT.ByValue(mousePosition.getPointer()),
                WinUser.MONITOR_DEFAULTTONEAREST);
        WinUser.MONITORINFO monitorInfo = new WinUser.MONITORINFO();
        User32.INSTANCE.GetMonitorInfo(hMonitor, monitorInfo);
        return monitorInfo;
    }

    static void mouseMoved(WinDef.POINT mousePosition) {
        WindowsIndicator.mousePosition = mousePosition;
    }

    public static void mousePosition(double x, double y) {
        if (mousePosition == null)
            return;
        WinUser.MONITORINFO monitorInfo = findCurrentMonitorPosition(mousePosition);
        CursorPositionAndSize cursorPositionAndSize =
                WindowsMouse.cursorPositionAndSize();
        User32.INSTANCE.MoveWindow(indicatorWindowHwnd,
                bestIndicatorX(mousePosition.x, cursorPositionAndSize.width(),
                        monitorInfo.rcMonitor.left, monitorInfo.rcMonitor.right),
                bestIndicatorY(mousePosition.y, cursorPositionAndSize.height(),
                        monitorInfo.rcMonitor.top, monitorInfo.rcMonitor.bottom),
                indicatorSize, indicatorSize, false);
        mousePosition = null;
    }
}
