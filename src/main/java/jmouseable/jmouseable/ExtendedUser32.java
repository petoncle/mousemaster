package jmouseable.jmouseable;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinNT;

import java.util.List;

public interface ExtendedUser32 extends User32 {
    ExtendedUser32 INSTANCE = Native.load("user32", ExtendedUser32.class);

    int WS_EX_NOACTIVATE = 0x08000000;
    int WS_EX_TOOLWINDOW = 0x00000080;

    int MOUSEEVENTF_MOVE = 0x0001;

    boolean GetCursorInfo(CURSORINFO pci);

    boolean GetIconInfo(HICON hIcon, WinGDI.ICONINFO piconinfo);


    class CURSORINFO extends Structure {
        public int cbSize;
        public int flags;
        public WinNT.HANDLE hCursor;
        public WinDef.POINT ptScreenPos;

        public CURSORINFO() {
            super();
            cbSize = this.size(); // Must initialize the size for the structure
        }

        @Override
        protected List<String> getFieldOrder() {
            return List.of("cbSize", "flags", "hCursor", "ptScreenPos");
        }

        public static class ByReference extends CURSORINFO
                implements Structure.ByReference {
        }
    }


}