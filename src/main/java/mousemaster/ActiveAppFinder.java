package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActiveAppFinder {

    private static final Logger logger = LoggerFactory.getLogger(ActiveAppFinder.class);

    private IntByReference processId = new IntByReference();
    private byte[] executableNameBytes = new byte[1024];

    public App activeApp() {
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, processId);
        WinNT.HANDLE processHandle = Kernel32.INSTANCE.OpenProcess(
                Kernel32.PROCESS_QUERY_INFORMATION | Kernel32.PROCESS_VM_READ,
                false, processId.getValue());
        if (processHandle == null) {
            logger.info("Unable to find the name of the active app");
            return null;
        }
        if (ExtendedPsapi.INSTANCE.GetModuleBaseNameA(processHandle, null,
                executableNameBytes,
                executableNameBytes.length) == 0) {
            logger.info("Unable to find the name of the active app");
            return null;
        }
        String executableName = Native.toString(executableNameBytes);
        Kernel32.INSTANCE.CloseHandle(processHandle);
        return new App(executableName);
    }

}
