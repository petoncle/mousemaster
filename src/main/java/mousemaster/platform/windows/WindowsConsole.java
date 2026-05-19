package mousemaster.platform.windows;

import mousemaster.*;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import mousemaster.platform.Console;

public class WindowsConsole implements Console {

    @Override
    public void show() {
        WinDef.HWND hwnd = Kernel32.INSTANCE.GetConsoleWindow();
        User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_SHOW);
    }

    @Override
    public void hide() {
        WinDef.HWND hwnd = Kernel32.INSTANCE.GetConsoleWindow();
        User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_HIDE);
    }

}
