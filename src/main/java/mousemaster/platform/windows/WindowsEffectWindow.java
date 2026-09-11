package mousemaster.platform.windows;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.PointerByReference;
import mousemaster.renderer.EffectRenderer;

import java.nio.ByteBuffer;

/**
 * The window the effects are drawn in on Windows: a layered, click-through, topmost
 * popup owned by this class rather than by Qt, updated in place with
 * UpdateLayeredWindow and no destination position. A Qt widget flush hands the
 * window's position to UpdateLayeredWindow, which Windows treats as a move and
 * re-picks the cursor for; an animation played over a window edge then flickered
 * between the resize cursor and the arrow, frame after frame. Here the window is
 * only moved (SetWindowPos) when the effects actually move.
 */
final class WindowsEffectWindow implements EffectRenderer.NativeSink {

    private final WinDef.HWND hwnd;
    // Kept in a field: the callback must outlive the registration.
    private final WinUser.WindowProc windowProc;
    private WinDef.HDC memoryDc;
    private WinDef.HBITMAP dib;
    private WinNT.HANDLE previousBitmap;
    private Pointer dibBits;
    private int dibWidth, dibHeight;
    private int left = Integer.MIN_VALUE, top, width, height;
    private boolean shown;
    private byte[] copyBuffer = new byte[0];

    WindowsEffectWindow() {
        WinUser.WNDCLASSEX wClass = new WinUser.WNDCLASSEX();
        windowProc = (hwnd, uMsg, wParam, lParam) ->
                User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
        wClass.lpszClassName = "MousemasterEffectWindow";
        wClass.lpfnWndProc = windowProc;
        User32.INSTANCE.RegisterClassEx(wClass);
        hwnd = User32.INSTANCE.CreateWindowEx(
                ExtendedUser32.WS_EX_TOOLWINDOW | ExtendedUser32.WS_EX_NOACTIVATE |
                ExtendedUser32.WS_EX_LAYERED | ExtendedUser32.WS_EX_TRANSPARENT |
                ExtendedUser32.WS_EX_TOPMOST,
                wClass.lpszClassName, "MousemasterEffects", WinUser.WS_POPUP,
                0, 0, 1, 1, null, null,
                Kernel32.INSTANCE.GetModuleHandle(null), null);
    }

    WinDef.HWND hwnd() {
        return hwnd;
    }

    @Override
    public void show(int leftPixels, int topPixels, int width, int height,
                     ByteBuffer bgraPremultiplied) {
        ensureDib(width, height);
        int byteCount = width * height * 4;
        if (copyBuffer.length < byteCount)
            copyBuffer = new byte[byteCount];
        bgraPremultiplied.rewind();
        bgraPremultiplied.get(copyBuffer, 0, byteCount);
        dibBits.write(0, copyBuffer, 0, byteCount);
        if (leftPixels != left || topPixels != top || width != this.width ||
            height != this.height) {
            left = leftPixels;
            top = topPixels;
            this.width = width;
            this.height = height;
            User32.INSTANCE.SetWindowPos(hwnd, null, left, top, width, height,
                    WinUser.SWP_NOACTIVATE | WinUser.SWP_NOZORDER);
        }
        WinUser.SIZE size = new WinUser.SIZE(width, height);
        WinDef.POINT source = new WinDef.POINT(0, 0);
        WinUser.BLENDFUNCTION blend = new WinUser.BLENDFUNCTION();
        blend.BlendOp = WinUser.AC_SRC_OVER;
        blend.BlendFlags = 0;
        blend.SourceConstantAlpha = (byte) 255;
        blend.AlphaFormat = WinUser.AC_SRC_ALPHA;
        // No destination point: the window is not moved, so the cursor under it is
        // left alone.
        User32.INSTANCE.UpdateLayeredWindow(hwnd, null, null, size, memoryDc, source, 0,
                blend, WinUser.ULW_ALPHA);
        if (!shown) {
            shown = true;
            User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_SHOWNOACTIVATE);
        }
    }

    @Override
    public void move(int leftPixels, int topPixels) {
        if (leftPixels == left && topPixels == top)
            return;
        left = leftPixels;
        top = topPixels;
        User32.INSTANCE.SetWindowPos(hwnd, null, left, top, 0, 0,
                WinUser.SWP_NOACTIVATE | WinUser.SWP_NOZORDER | WinUser.SWP_NOSIZE);
    }

    @Override
    public void hide() {
        if (!shown)
            return;
        shown = false;
        User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_HIDE);
        left = Integer.MIN_VALUE;
    }

    private void ensureDib(int width, int height) {
        if (dib != null && width == dibWidth && height == dibHeight)
            return;
        releaseDib();
        WinGDI.BITMAPINFO bitmapInfo = new WinGDI.BITMAPINFO();
        bitmapInfo.bmiHeader.biSize = bitmapInfo.bmiHeader.size();
        bitmapInfo.bmiHeader.biWidth = width;
        bitmapInfo.bmiHeader.biHeight = -height; // Top-down, like the QImage.
        bitmapInfo.bmiHeader.biPlanes = 1;
        bitmapInfo.bmiHeader.biBitCount = 32;
        bitmapInfo.bmiHeader.biCompression = WinGDI.BI_RGB;
        memoryDc = GDI32.INSTANCE.CreateCompatibleDC(null);
        PointerByReference bitsRef = new PointerByReference();
        dib = ExtendedGDI32.INSTANCE.CreateDIBSection(memoryDc, bitmapInfo,
                WinGDI.DIB_RGB_COLORS, bitsRef, null, 0);
        previousBitmap = GDI32.INSTANCE.SelectObject(memoryDc, dib);
        dibBits = bitsRef.getValue();
        dibWidth = width;
        dibHeight = height;
    }

    private void releaseDib() {
        if (dib == null)
            return;
        GDI32.INSTANCE.SelectObject(memoryDc, previousBitmap);
        GDI32.INSTANCE.DeleteObject(dib);
        GDI32.INSTANCE.DeleteDC(memoryDc);
        dib = null;
        memoryDc = null;
        dibBits = null;
    }

}
