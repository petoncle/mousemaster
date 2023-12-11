package jmouseable.jmouseable;

import com.sun.jna.IntegerType;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

public interface Shcore extends StdCallLibrary {
    Shcore INSTANCE = Native.load("shcore", Shcore.class);

    class MONITOR_DPI_TYPE extends IntegerType {
        public static final int MDT_EFFECTIVE_DPI = 0;
        public static final int MDT_ANGULAR_DPI = 1;
        public static final int MDT_RAW_DPI = 2;
        public static final int MDT_DEFAULT = MDT_EFFECTIVE_DPI;

        public MONITOR_DPI_TYPE() {
            this(MDT_DEFAULT);
        }

        public MONITOR_DPI_TYPE(int value) {
            super(4, value);
        }
    }

    WinNT.HRESULT GetDpiForMonitor(WinUser.HMONITOR hmonitor, MONITOR_DPI_TYPE dpiType,
                                   IntByReference dpiX, IntByReference dpiY);

    WinNT.HRESULT GetScaleFactorForMonitor(WinUser.HMONITOR hmonitor,
                                           IntByReference scaleFactor);
}