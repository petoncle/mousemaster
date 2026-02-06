package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class ActiveAppFinder {

    private static final Logger logger = LoggerFactory.getLogger(ActiveAppFinder.class);

    private IntByReference processId = new IntByReference();
    private byte[] executableNameBytes = new byte[1024];
    String lastExecutableName;

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
        boolean isMinimized = ExtendedUser32.INSTANCE.IsIconic(hwnd) ||
                           // IsIconic can return false while the window is still at minimized-like coordinates (around -32000).
                           windowRect.left < -30_000 || windowRect.top < -30_000;
        if (isMinimized) {
            logger.debug(
                    "Ignoring active app change from " + lastExecutableName + " to " +
                    executableName + " because " + executableName + " is minimized: " +
                    windowRect);
            return new App(lastExecutableName);
        }
        if (!Objects.equals(executableName, lastExecutableName)) {
            logger.debug("Detected active app change from " + lastExecutableName + " to " + executableName + " " + windowRect);
            lastExecutableName = executableName;
        }
        return new App(executableName);
    }

}
