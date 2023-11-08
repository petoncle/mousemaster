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

    private static int bestIndicatorX(int mouseX, int monitorLeft, int monitorRight) {
        boolean isNearLeftEdge = mouseX <= (monitorLeft + indicatorEdgeThreshold);
        boolean isNearRightEdge = mouseX >= (monitorRight - indicatorEdgeThreshold);
        if (isNearRightEdge)
            return mouseX - indicatorSize;
        return mouseX + cursorWidth / 2;
    }

    private static int bestIndicatorY(int mouseY, int monitorTop, int monitorBottom) {
        boolean isNearBottomEdge = mouseY >= (monitorBottom - indicatorEdgeThreshold);
        boolean isNearTopEdge = mouseY <= (monitorTop + indicatorEdgeThreshold);
        if (isNearBottomEdge)
            return mouseY - indicatorSize;
        return mouseY + cursorHeight / 2;
    }

    public static void showIndicatorWindow() {
        WinDef.POINT mousePosition = findCursorPositionAndSize();
        WinUser.MONITORINFO monitorInfo = findCurrentMonitorPosition(mousePosition);
        logger.info("Cursor size: " + cursorWidth + " " + cursorHeight);
        WinUser.WNDCLASSEX wClass = new WinUser.WNDCLASSEX();
        wClass.hbrBackground = ExtendedGDI32.INSTANCE.CreateSolidBrush(0x000000FF);
        wClass.lpszClassName = "JMouseableOverlayClassName";
        wClass.lpfnWndProc = (WinUser.WindowProc) User32.INSTANCE::DefWindowProc;
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
        User32.INSTANCE.ShowWindow(indicatorWindowHwnd, WinUser.SW_SHOWNORMAL);
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
        logger.info("Mouse position: " + mousePosition.x + "," + mousePosition.y);
        WinUser.MONITORINFO monitorInfo = findCurrentMonitorPosition(mousePosition);
        User32.INSTANCE.MoveWindow(indicatorWindowHwnd,
                bestIndicatorX(mousePosition.x, monitorInfo.rcMonitor.left,
                        monitorInfo.rcMonitor.right),
                bestIndicatorY(mousePosition.y, monitorInfo.rcMonitor.top,
                        monitorInfo.rcMonitor.bottom), indicatorSize, indicatorSize,
                true);
    }
}
