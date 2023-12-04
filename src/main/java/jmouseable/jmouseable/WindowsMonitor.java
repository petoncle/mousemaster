package jmouseable.jmouseable;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

import java.util.HashSet;
import java.util.Set;

public class WindowsMonitor {
    public static Set<Monitor> findMonitors() {
        Set<Monitor> monitors = new HashSet<>();
        User32.INSTANCE.EnumDisplayMonitors(null, null, new WinUser.MONITORENUMPROC() {
            @Override
            public int apply(WinUser.HMONITOR hMonitor, WinDef.HDC hdcMonitor,
                             WinDef.RECT lprcMonitor, WinDef.LPARAM dwData) {
                monitors.add(new Monitor(lprcMonitor.left, lprcMonitor.top,
                        lprcMonitor.right - lprcMonitor.left,
                        lprcMonitor.bottom - lprcMonitor.top));
                return 1;
            }
        }, null);
        return monitors;
    }

    public static MonitorRectangleAndDpi activeMonitorRectangleAndDpi(
            WinDef.POINT mousePosition) {
        WinUser.HMONITOR hMonitor = User32.INSTANCE.MonitorFromPoint(
                new WinDef.POINT.ByValue(mousePosition.getPointer()),
                WinUser.MONITOR_DEFAULTTONEAREST);
        WinUser.MONITORINFO monitorInfo = new WinUser.MONITORINFO();
        User32.INSTANCE.GetMonitorInfo(hMonitor, monitorInfo);
        IntByReference dpiX = new IntByReference();
        IntByReference dpiY = new IntByReference();
        Shcore.INSTANCE.GetDpiForMonitor(hMonitor,
                new Shcore.MONITOR_DPI_TYPE(Shcore.MONITOR_DPI_TYPE.MDT_EFFECTIVE_DPI),
                dpiX, dpiY);
        int dpi = dpiX.getValue();
        return new MonitorRectangleAndDpi(
                new Rectangle(monitorInfo.rcMonitor.left, monitorInfo.rcMonitor.top,
                        monitorInfo.rcMonitor.right - monitorInfo.rcMonitor.left,
                        monitorInfo.rcMonitor.bottom - monitorInfo.rcMonitor.top), dpi);
    }

    public record MonitorRectangleAndDpi(Rectangle rectangle, int dpi) {
    }

}
