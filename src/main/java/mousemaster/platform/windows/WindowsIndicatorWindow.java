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
import mousemaster.renderer.IndicatorRenderer.IndicatorImage;

/**
 * The indicator window on Windows: UpdateLayeredWindow moves it and replaces its pixels in
 * one call, which a Qt window cannot do.
 */
final class WindowsIndicatorWindow {

    private final WinDef.HWND hwnd;
    // Kept in a field: the callback must outlive the registration.
    private final WinUser.WindowProc windowProc;
    private WinDef.HDC memoryDc;
    private WinNT.HANDLE previousBitmap;
    private WinDef.HBITMAP dib;
    private Pointer dibBits;
    private int dibWidth;
    private int dibHeight;
    private IndicatorImage image;
    private int x;
    private int y;
    private boolean showing;

    WindowsIndicatorWindow() {
        WinUser.WNDCLASSEX windowClass = new WinUser.WNDCLASSEX();
        windowProc = (hwnd, uMsg, wParam, lParam) ->
                User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
        windowClass.lpszClassName = "MousemasterIndicatorWindow";
        windowClass.lpfnWndProc = windowProc;
        User32.INSTANCE.RegisterClassEx(windowClass);
        hwnd = User32.INSTANCE.CreateWindowEx(0, windowClass.lpszClassName,
                "MousemasterIndicator", WinUser.WS_POPUP, 0, 0, 1, 1, null, null,
                Kernel32.INSTANCE.GetModuleHandle(null), null);
    }

    WinDef.HWND hwnd() {
        return hwnd;
    }

    boolean showing() {
        return showing;
    }

    void show(IndicatorImage image, int x, int y) {
        if (showing && image == this.image && x == this.x && y == this.y)
            return;
        WinDef.POINT position = new WinDef.POINT(x, y);
        this.x = x;
        this.y = y;
        WinUser.BLENDFUNCTION blend = new WinUser.BLENDFUNCTION();
        blend.BlendOp = WinUser.AC_SRC_OVER;
        blend.SourceConstantAlpha = (byte) 255;
        blend.AlphaFormat = WinUser.AC_SRC_ALPHA;
        if (image == this.image)
            User32.INSTANCE.UpdateLayeredWindow(hwnd, null, position, null, null, null, 0,
                    blend, WinUser.ULW_ALPHA);
        else {
            this.image = image;
            copyToDib(image);
            User32.INSTANCE.UpdateLayeredWindow(hwnd, null, position,
                    new WinUser.SIZE(image.width(), image.height()), memoryDc,
                    new WinDef.POINT(0, 0), 0, blend, WinUser.ULW_ALPHA);
        }
        if (!showing) {
            showing = true;
            User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_SHOWNOACTIVATE);
        }
    }

    void hide() {
        if (!showing)
            return;
        showing = false;
        User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_HIDE);
    }

    /** The DIB only grows: an image smaller than it fills its top-left corner. */
    private void copyToDib(IndicatorImage image) {
        if (dib == null || image.width() > dibWidth || image.height() > dibHeight)
            createDib(Math.max(image.width(), dibWidth), Math.max(image.height(), dibHeight));
        for (int row = 0; row < image.height(); row++)
            dibBits.write(4L * row * dibWidth, image.argb(), row * image.width(),
                    image.width());
    }

    private void createDib(int width, int height) {
        if (dib != null) {
            GDI32.INSTANCE.SelectObject(memoryDc, previousBitmap);
            GDI32.INSTANCE.DeleteObject(dib);
            GDI32.INSTANCE.DeleteDC(memoryDc);
        }
        WinGDI.BITMAPINFO bitmapInfo = new WinGDI.BITMAPINFO();
        bitmapInfo.bmiHeader.biSize = bitmapInfo.bmiHeader.size();
        bitmapInfo.bmiHeader.biWidth = width;
        bitmapInfo.bmiHeader.biHeight = -height;
        bitmapInfo.bmiHeader.biPlanes = 1;
        bitmapInfo.bmiHeader.biBitCount = 32;
        bitmapInfo.bmiHeader.biCompression = WinGDI.BI_RGB;
        memoryDc = GDI32.INSTANCE.CreateCompatibleDC(null);
        PointerByReference bits = new PointerByReference();
        dib = ExtendedGDI32.INSTANCE.CreateDIBSection(memoryDc, bitmapInfo,
                WinGDI.DIB_RGB_COLORS, bits, null, 0);
        previousBitmap = GDI32.INSTANCE.SelectObject(memoryDc, dib);
        dibBits = bits.getValue();
        dibWidth = width;
        dibHeight = height;
    }

}
