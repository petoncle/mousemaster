package jmouseable.jmouseable;

import com.sun.jna.platform.win32.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WindowsIndicator {

    private static final Logger logger = LoggerFactory.getLogger(WindowsIndicator.class);
    private static final int indicatorEdgeThreshold = 100; // in pixels
    private static final int indicatorSize = 16;

    private static int cursorWidth, cursorHeight;
    private static WinDef.HWND indicatorWindowHwnd;
    private static boolean mustShowOnceCreated;
    private static int currentColor = 0x000000FF;

    private static int bestIndicatorX(int mouseX, int monitorLeft, int monitorRight) {
        mouseX = Math.min(monitorRight, Math.max(monitorLeft, mouseX));
        boolean isNearLeftEdge = mouseX <= (monitorLeft + indicatorEdgeThreshold);
        boolean isNearRightEdge = mouseX >= (monitorRight - indicatorEdgeThreshold);
        if (isNearRightEdge)
            return mouseX - indicatorSize;
        return mouseX + cursorWidth / 2;
    }

    private static int bestIndicatorY(int mouseY, int monitorTop, int monitorBottom) {
        mouseY = Math.min(monitorBottom, Math.max(monitorTop, mouseY));
        boolean isNearBottomEdge = mouseY >= (monitorBottom - indicatorEdgeThreshold);
        boolean isNearTopEdge = mouseY <= (monitorTop + indicatorEdgeThreshold);
        if (isNearBottomEdge)
            return mouseY - indicatorSize;
        return mouseY + cursorHeight / 2;
    }

    public static void createIndicatorWindow() {
        WinDef.POINT mousePosition = findCursorPositionAndSize();
        WinUser.MONITORINFO monitorInfo = findCurrentMonitorPosition(mousePosition);
        logger.info("Cursor size: " + cursorWidth + " " + cursorHeight);
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
                bestIndicatorX(mousePosition.x, monitorInfo.rcMonitor.left,
                        monitorInfo.rcMonitor.right),
                bestIndicatorY(mousePosition.y, monitorInfo.rcMonitor.top,
                        monitorInfo.rcMonitor.bottom), 16, 16, null, null,
                wClass.hInstance, null);
        if (mustShowOnceCreated)
            show();
    }

    private static WinDef.LRESULT indicatorWindowCallback(WinDef.HWND hwnd, int uMsg,
                                                          WinDef.WPARAM wParam,
                                                          WinDef.LPARAM lParam) {
        switch (uMsg) {
            case WinUser.WM_PAINT:
                ExtendedUser32.PAINTSTRUCT ps = new ExtendedUser32.PAINTSTRUCT();
                WinDef.RECT rect = new WinDef.RECT();
                WinDef.HBRUSH hbrBackground = ExtendedGDI32.INSTANCE.CreateSolidBrush(currentColor);
                WinDef.HDC hdc = ExtendedUser32.INSTANCE.BeginPaint(hwnd, ps);
                User32.INSTANCE.GetClientRect(hwnd, rect);
                ExtendedUser32.INSTANCE.FillRect(hdc, rect, hbrBackground);
                ExtendedUser32.INSTANCE.EndPaint(hwnd, ps);
                GDI32.INSTANCE.DeleteObject(hbrBackground);
                break;
        }
        return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
    }

    public static void changeColor(int newColor) {
        currentColor = newColor;
        // Force window to repaint to reflect new color
        User32.INSTANCE.InvalidateRect(indicatorWindowHwnd, null, true);
        User32.INSTANCE.UpdateWindow(indicatorWindowHwnd);
    }

    public static void show() {
        if (indicatorWindowHwnd == null) {
            mustShowOnceCreated = true;
            return;
        }
        User32.INSTANCE.ShowWindow(indicatorWindowHwnd, WinUser.SW_SHOWNORMAL);
    }

    public static void hide() {
        User32.INSTANCE.ShowWindow(indicatorWindowHwnd, WinUser.SW_HIDE);
    }

    private static WinDef.POINT findCursorPositionAndSize() {
        ExtendedUser32.CURSORINFO cursorInfo = new ExtendedUser32.CURSORINFO();
        if (ExtendedUser32.INSTANCE.GetCursorInfo(cursorInfo)) {
            WinDef.POINT mousePosition = cursorInfo.ptScreenPos;
            WinGDI.ICONINFO iconInfo = new WinGDI.ICONINFO();
            if (User32.INSTANCE.GetIconInfo(new WinDef.HICON(cursorInfo.hCursor),
                    iconInfo)) {
                WinGDI.BITMAP bmp = new WinGDI.BITMAP();

                int sizeOfBitmap = bmp.size();
                if (iconInfo.hbmColor != null) {
                    // Get the color bitmap information.
                    GDI32.INSTANCE.GetObject(iconInfo.hbmColor, sizeOfBitmap,
                            bmp.getPointer());
                }
                else {
                    // Get the mask bitmap information if there is no color bitmap.
                    GDI32.INSTANCE.GetObject(iconInfo.hbmMask, sizeOfBitmap,
                            bmp.getPointer());
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
            }
            return mousePosition;
        }
        throw new IllegalStateException();
    }

    private static WinUser.MONITORINFO findCurrentMonitorPosition(
            WinDef.POINT mousePosition) {
        WinUser.HMONITOR hMonitor = User32.INSTANCE.MonitorFromPoint(
                new WinDef.POINT.ByValue(mousePosition.getPointer()),
                WinUser.MONITOR_DEFAULTTONEAREST);
        WinUser.MONITORINFO monitorInfo = new WinUser.MONITORINFO();
        User32.INSTANCE.GetMonitorInfo(hMonitor, monitorInfo);
        return monitorInfo;
    }

    public static void mouseMoved(WinDef.POINT mousePosition) {
        WinUser.MONITORINFO monitorInfo = findCurrentMonitorPosition(mousePosition);
        User32.INSTANCE.MoveWindow(indicatorWindowHwnd,
                bestIndicatorX(mousePosition.x, monitorInfo.rcMonitor.left,
                        monitorInfo.rcMonitor.right),
                bestIndicatorY(mousePosition.y, monitorInfo.rcMonitor.top,
                        monitorInfo.rcMonitor.bottom), indicatorSize, indicatorSize,
                true);
    }
}
