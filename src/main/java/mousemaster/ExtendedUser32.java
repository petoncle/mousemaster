package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.*;
import com.sun.jna.win32.W32APIOptions;

import java.util.List;

public interface ExtendedUser32 extends User32 {
    // W32APIOptions.DEFAULT_OPTIONS is required for JNA to find DrawText().
    ExtendedUser32 INSTANCE =
            Native.load("user32", ExtendedUser32.class, W32APIOptions.DEFAULT_OPTIONS);

    int WS_EX_NOACTIVATE = 0x08000000;
    int WS_EX_TOOLWINDOW = 0x00000080;
    int WS_EX_LAYERED = 0x00080000;
    int WS_EX_TRANSPARENT = 0x00000020;

    int WS_EX_STATICEDGE = 0x00020000;

    int MOUSEEVENTF_MOVE = 0x0001;
    int MOUSEEVENTF_LEFTDOWN = 0x0002;
    int MOUSEEVENTF_LEFTUP = 0x0004;
    int MOUSEEVENTF_RIGHTDOWN = 0x0008;
    int MOUSEEVENTF_RIGHTUP = 0x0010;
    int MOUSEEVENTF_MIDDLEDOWN = 0x0020;
    int MOUSEEVENTF_MIDDLEUP = 0x0040;
    int MOUSEEVENTF_WHEEL = 0x0800;
    int MOUSEEVENTF_HWHEEL = 0x01000;

    int LLKHF_INJECTED = 0x00000010;

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

    LRESULT CallNextHookEx(HHOOK hhk, int nCode, WPARAM wParam, WinUser.KBDLLHOOKSTRUCT lParam);
    LRESULT CallNextHookEx(HHOOK hhk, int nCode, WPARAM wParam, WinUser.MSLLHOOKSTRUCT lParam);

    class PAINTSTRUCT extends Structure {
        public WinDef.HDC hdc;
        public WinDef.BOOL fErase;
        public WinDef.RECT rcPaint;
        public WinDef.BOOL fRestore;
        public WinDef.BOOL fIncUpdate;
        public byte[] rgbReserved = new byte[32];

        @Override
        protected List<String> getFieldOrder() {
            return List.of("hdc", "fErase", "rcPaint", "fRestore", "fIncUpdate", "rgbReserved");
        }
    }

    HDC BeginPaint(HWND hWnd, PAINTSTRUCT lpPaint);
    boolean EndPaint(HWND hWnd, PAINTSTRUCT lpPaint);
    int FillRect(WinDef.HDC hDC, WinDef.RECT lprc, WinDef.HBRUSH hbr);

    boolean SetProcessDpiAwarenessContext(HANDLE dpiContext);
    long DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 = -4L;

    HKL LoadKeyboardLayoutA(String pwszKLID, int Flags);

    HCURSOR CreateCursor(HINSTANCE hInst, int xHotSpot, int yHotSpot, int nWidth,
                         int nHeight, byte[] pvANDPlane, byte[] pvXORPlane);
    HANDLE CopyImage(HANDLE hImage, UINT uType, int cxDesired, int cyDesired, UINT fuFlags);

    int IMAGE_CURSOR = 2;

    boolean SetSystemCursor(HANDLE hcur, UINT id);

    boolean SystemParametersInfoA(UINT uiAction, UINT uiParam, Object pvParam, UINT fWinIni);

    int SPI_SETCURSORS = 0x0057;
    int SPI_GETMOUSESPEED = 0x0070;
    int SPI_SETMOUSESPEED = 0x0071;
    // Constants for mouse thresholds and acceleration.
    int SPI_GETMOUSE = 0x0003;
    int SPI_SETMOUSE = 0x0004;

    HWND HWND_TOPMOST = new HWND(new Pointer(-1));

    int DrawText(HDC hdc, String lpchText, int cchText, RECT lprc, UINT format);

}