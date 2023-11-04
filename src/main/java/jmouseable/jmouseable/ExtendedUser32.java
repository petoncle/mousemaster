package jmouseable.jmouseable;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;

public interface ExtendedUser32 extends User32 {
    ExtendedUser32 INSTANCE = Native.load("user32", ExtendedUser32.class);

    int WS_EX_NOACTIVATE = 0x08000000;
    int WS_EX_TOOLWINDOW = 0x00000080;

}