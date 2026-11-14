package mousemaster.platform.windows;

import mousemaster.*;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

public interface Dwmapi extends StdCallLibrary {

    Dwmapi INSTANCE = Native.loadLibrary("dwmapi", Dwmapi.class);

    boolean DwmGetWindowAttribute(WinDef.HWND hwnd, int dwAttribute, WinDef.RECT pRect, int cbAttribute);

    boolean DwmGetWindowAttribute(WinDef.HWND hwnd, int dwAttribute,
                                  IntByReference pvAttribute, int cbAttribute);

    boolean DwmSetWindowAttribute(WinDef.HWND hwnd, int dwAttribute,
                                  IntByReference pvAttribute, int cbAttribute);

    void DwmFlush();

    int DWMWA_EXTENDED_FRAME_BOUNDS = 9;
    int DWMWA_CLOAK = 13;
    int DWMWA_CLOAKED = 14;

    /**
     * A cloaked window stays shown and keeps its place in the z order, but it is not on the
     * screen, and the shell does not count it as a window that covers a screen. Windows cloaks
     * the windows of another virtual desktop, and suspended UWP apps.
     */
    static boolean isCloaked(WinDef.HWND hwnd) {
        IntByReference cloaked = new IntByReference();
        INSTANCE.DwmGetWindowAttribute(hwnd, DWMWA_CLOAKED, cloaked, 4);
        return cloaked.getValue() != 0;
    }

}
