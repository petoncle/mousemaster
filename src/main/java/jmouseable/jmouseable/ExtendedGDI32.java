package jmouseable.jmouseable;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.WinDef;

public interface ExtendedGDI32 extends GDI32 {
    ExtendedGDI32 INSTANCE = Native.load("gdi32", ExtendedGDI32.class);

    WinDef.HBRUSH CreateSolidBrush(int color);

}