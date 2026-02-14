package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class ActiveAppFinder {

    private static final Logger logger = LoggerFactory.getLogger(ActiveAppFinder.class);

    private IntByReference processId = new IntByReference();
    private byte[] executableNameBytes = new byte[1024];
    String lastExecutableName;
    private String lastIgnoredExecutableName;

    public App activeApp() {
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, processId);
        WinNT.HANDLE processHandle = Kernel32.INSTANCE.OpenProcess(
                Kernel32.PROCESS_QUERY_INFORMATION | Kernel32.PROCESS_VM_READ,
                false, processId.getValue());
        if (processHandle == null) {
            logger.info("Unable to find the name of the active app");
            lastExecutableName = null;
            return null;
        }
        if (ExtendedPsapi.INSTANCE.GetModuleBaseNameA(processHandle, null,
                executableNameBytes,
                executableNameBytes.length) == 0) {
            logger.info("Unable to find the name of the active app");
            lastExecutableName = null;
            return null;
        }
        String executableName = Native.toString(executableNameBytes).replaceAll(" ", "");
        Kernel32.INSTANCE.CloseHandle(processHandle);
        WinDef.RECT windowRect = WindowsOverlay.windowRectExcludingShadow(hwnd);
        if (windowRect.right - windowRect.left == 0 ||
            windowRect.bottom - windowRect.top == 0) {
            if (!executableName.equals(lastIgnoredExecutableName)) {
                logger.debug(
                        "Ignoring active app change from " + lastExecutableName + " to " +
                        executableName + " because " + executableName + " is zero-sized: " +
                        windowRect);
                lastIgnoredExecutableName = executableName;
            }
            return new App(lastExecutableName);
        }
        // IsIconic can return false for some minimized/off-screen windows.
        // Use MonitorFromRect to find out if a window intersects with a monitor.
        boolean isMinimized = ExtendedUser32.INSTANCE.IsIconic(hwnd) ||
                              User32.INSTANCE.MonitorFromRect(windowRect,
                                      WinUser.MONITOR_DEFAULTTONULL) == null;
        if (isMinimized) {
            if (!executableName.equals(lastIgnoredExecutableName)) {
                logger.debug(
                        "Ignoring active app change from " + lastExecutableName + " to " +
                        executableName + " because " + executableName + " is minimized: " +
                        windowRect);
                lastIgnoredExecutableName = executableName;
            }
            return new App(lastExecutableName);
        }
        lastIgnoredExecutableName = null;
        if (!Objects.equals(executableName, lastExecutableName)) {
            logger.debug("Detected active app change from " + lastExecutableName + " to " + executableName + " " + windowRect);
            lastExecutableName = executableName;
        }
        return new App(executableName);
    }

}
