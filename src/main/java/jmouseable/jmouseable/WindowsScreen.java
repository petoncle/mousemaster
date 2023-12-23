package jmouseable.jmouseable;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

import java.util.HashSet;
import java.util.Set;

public class WindowsScreen {
    public static Set<Screen> findScreens() {
        Set<Screen> screens = new HashSet<>();
        User32.INSTANCE.EnumDisplayMonitors(null, null, new WinUser.MONITORENUMPROC() {
            @Override
            public int apply(WinUser.HMONITOR hMonitor, WinDef.HDC hdcMonitor,
                             WinDef.RECT lprcMonitor, WinDef.LPARAM dwData) {
                int scaledDpi = findScreenDpi(hMonitor, true);
                double scale = findScreenScale(hMonitor);
                int dpi = (int) (scaledDpi / scale);
                screens.add(new Screen(new Rectangle(lprcMonitor.left, lprcMonitor.top,
                        lprcMonitor.right - lprcMonitor.left,
                        lprcMonitor.bottom - lprcMonitor.top), dpi,
                        scale));
                return 1;
            }
        }, null);
        return screens;
    }

    public static Screen findActiveScreen(WinDef.POINT containedPoint) {
        WinUser.HMONITOR hMonitor = User32.INSTANCE.MonitorFromPoint(
                new WinDef.POINT.ByValue(containedPoint.x, containedPoint.y),
                WinUser.MONITOR_DEFAULTTONEAREST);
        WinUser.MONITORINFO monitorInfo = new WinUser.MONITORINFO();
        User32.INSTANCE.GetMonitorInfo(hMonitor, monitorInfo);
        int scaledDpi = findScreenDpi(hMonitor, true);
        double scale = findScreenScale(hMonitor);
        int dpi = (int) (scaledDpi / scale);
        return new Screen(
                new Rectangle(monitorInfo.rcMonitor.left, monitorInfo.rcMonitor.top,
                        monitorInfo.rcMonitor.right - monitorInfo.rcMonitor.left,
                        monitorInfo.rcMonitor.bottom - monitorInfo.rcMonitor.top), dpi,
                scale);
    }

    private static int findScreenDpi(WinUser.HMONITOR hMonitor, boolean scaled) {
        IntByReference dpiX = new IntByReference();
        IntByReference dpiY = new IntByReference();
        // Scaled DPI (effective DPI) = Raw DPI * Scale%
        // For example, with a Display scale of 150%:
        // Scaled DPI = 96 * 150% = 144
        // For an unknown reason, at 150% scale, MDT_EFFECTIVE_DPI is 144 (correct),
        // but MDT_RAW_DPI is 51 (expected 96).
        Shcore.INSTANCE.GetDpiForMonitor(hMonitor, new Shcore.MONITOR_DPI_TYPE(
                scaled ? Shcore.MONITOR_DPI_TYPE.MDT_EFFECTIVE_DPI :
                        Shcore.MONITOR_DPI_TYPE.MDT_RAW_DPI), dpiX, dpiY);
        return dpiX.getValue();
    }

    private static double findScreenScale(WinUser.HMONITOR hMonitor) {
        // https://stackoverflow.com/questions/63692872/is-getscalefactorformonitor-winapi-returning-incorrect-scaling-factor
        // When running with GraalVM, and with a Display scale of 150%, GetScaleFactorForMonitor returns 140.
        // When running with Temurin, GetScaleFactorForMonitor returns 150.
        // This is apparently due to process DPI awareness, which is set to unaware when running with Temurin (??).
//        IntByReference scaleFactor = new IntByReference();
//        Shcore.INSTANCE.GetScaleFactorForMonitor(hMonitor, scaleFactor);
//        return scaleFactor.getValue() / 100d;
        double scale = findScreenDpi(hMonitor, true) / 96d;
        return scale;
    }

}
