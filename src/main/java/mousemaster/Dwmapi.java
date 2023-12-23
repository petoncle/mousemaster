package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;

public interface Dwmapi extends StdCallLibrary {

    Dwmapi INSTANCE = Native.loadLibrary("dwmapi", Dwmapi.class);

    boolean DwmGetWindowAttribute(WinDef.HWND hwnd, int dwAttribute, WinDef.RECT pRect, int cbAttribute);

    int DWMWA_EXTENDED_FRAME_BOUNDS = 9;

}
