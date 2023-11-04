package jmouseable.jmouseable;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JmouseableApplication implements CommandLineRunner {

    private static final Logger logger =
            LoggerFactory.getLogger(JmouseableApplication.class);


    public static void main(String[] args) {
        SpringApplication.run(JmouseableApplication.class, args);
    }

    private WinUser.HHOOK keyboardHook;
    private WinUser.HHOOK mouseHook;
    private WinDef.HWND indicatorWindowHwnd;

    @Override
    public void run(String... args) throws Exception {
        WinDef.HMODULE hMod = Kernel32.INSTANCE.GetModuleHandle(null);
        keyboardHook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL,
                (WinUser.LowLevelKeyboardProc) this::keyboardHookCallback, hMod, 0);
        mouseHook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_MOUSE_LL,
                (WinUser.LowLevelMouseProc) this::mouseHookCallback, hMod, 0);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Running shutdown hook");
            User32.INSTANCE.UnhookWindowsHookEx(keyboardHook);
            User32.INSTANCE.UnhookWindowsHookEx(mouseHook);
        }));
        logger.info("Keyboard and mouse hooks installed");
        showIndicatorWindow();
        WinUser.MSG msg = new WinUser.MSG();
        while (User32.INSTANCE.GetMessage(msg, null, 0, 0) != 0) {
            User32.INSTANCE.TranslateMessage(msg);
            User32.INSTANCE.DispatchMessage(msg);
        }
    }

    private WinDef.LRESULT keyboardHookCallback(int nCode, WinDef.WPARAM wParam,
                                                WinUser.KBDLLHOOKSTRUCT info) {
        if (nCode >= 0) {
            switch (wParam.intValue()) {
                case WinUser.WM_KEYUP:
                case WinUser.WM_KEYDOWN:
                case WinUser.WM_SYSKEYUP:
                case WinUser.WM_SYSKEYDOWN:
                    logger.info("In callback, key state: " + wParam + ", " + info.vkCode);
                    // If you want to stop the event from continuing you would do so here
                    break;
            }
        }
        return User32.INSTANCE.CallNextHookEx(keyboardHook, nCode, wParam,
                new WinDef.LPARAM(Pointer.nativeValue(info.getPointer())));
    }

    private WinDef.LRESULT mouseHookCallback(int nCode, WinDef.WPARAM wParam,
                                             WinUser.MSLLHOOKSTRUCT info) {
        if (nCode >= 0) {
            WinDef.POINT mousePosition = info.pt;
            System.out.println(
                    "Mouse position: (" + mousePosition.x + "," + mousePosition.y + ")");
            int size = 16;
            WinUser.HMONITOR hMonitor = User32.INSTANCE.MonitorFromPoint(
                    new WinDef.POINT.ByValue(mousePosition.getPointer()),
                    WinUser.MONITOR_DEFAULTTONEAREST);
            WinUser.MONITORINFO monitorInfo = new WinUser.MONITORINFO();
            User32.INSTANCE.GetMonitorInfo(hMonitor, monitorInfo);
            User32.INSTANCE.MoveWindow(indicatorWindowHwnd,
                    bestIndicatorX(mousePosition.x, size, monitorInfo.rcMonitor.left,
                            monitorInfo.rcMonitor.right),
                    bestIndicatorY(mousePosition.y, size, monitorInfo.rcMonitor.top,
                            monitorInfo.rcMonitor.bottom), size, size, true);
        }
        return User32.INSTANCE.CallNextHookEx(mouseHook, nCode, wParam,
                new WinDef.LPARAM(Pointer.nativeValue(info.getPointer())));
    }

    private static final int indicatorEdgeThreshold = 100; // in pixels

    private static int bestIndicatorX(int mouseX, int indicatorSize, int monitorLeft,
                                      int monitorRight) {
        boolean isNearLeftEdge = mouseX <= (monitorLeft + indicatorEdgeThreshold);
        boolean isNearRightEdge = mouseX >= (monitorRight - indicatorEdgeThreshold);
        if (isNearRightEdge)
            return mouseX - indicatorSize;
        return mouseX + indicatorSize;
    }

    private static int bestIndicatorY(int mouseY, int indicatorSize, int monitorTop,
                                      int monitorBottom) {
        boolean isNearBottomEdge = mouseY >= (monitorBottom - indicatorEdgeThreshold);
        boolean isNearTopEdge = mouseY <= (monitorTop + indicatorEdgeThreshold);
        if (isNearBottomEdge)
            return mouseY - indicatorSize;
        return mouseY + indicatorSize;
    }

    private void showIndicatorWindow() {
        // Define a new window class
        WinUser.WNDCLASSEX wClass = new WinUser.WNDCLASSEX();
        wClass.hInstance = Kernel32.INSTANCE.GetModuleHandle(""); // not useful??
        wClass.hbrBackground = ExtendedGDI32.INSTANCE.CreateSolidBrush(0x000000FF);
        wClass.lpszClassName = "JMouseableOverlayClassName";
        wClass.lpfnWndProc = (WinUser.WindowProc) User32.INSTANCE::DefWindowProc;
        // Register the window class
        User32.INSTANCE.RegisterClassEx(wClass);
        indicatorWindowHwnd = User32.INSTANCE.CreateWindowEx(
                User32.WS_EX_TOPMOST | ExtendedUser32.WS_EX_TOOLWINDOW |
                ExtendedUser32.WS_EX_NOACTIVATE, wClass.lpszClassName,
                "JMouseableOverlayWindowName", WinUser.WS_POPUP, 100, 100, 16, 16, null,
                // Parent window
                null, // Menu
                wClass.hInstance, null  // Additional application data
        );
        User32.INSTANCE.ShowWindow(indicatorWindowHwnd, WinUser.SW_SHOWNORMAL);
    }

}
