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
import io.qt.gui.QColor;
import io.qt.gui.QImage;
import io.qt.gui.QPainter;
import mousemaster.Rectangle;
import mousemaster.Screen;
import mousemaster.renderer.IndicatorRenderer;
import mousemaster.renderer.IndicatorRenderer.WindowImage;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * The indicator window on Windows: UpdateLayeredWindow moves it and replaces its pixels in
 * one call, which a Qt window cannot do. It draws images over each other, and redraws and
 * sends only where they changed. It is shown once and never hidden: a window that shows
 * reaches the screen a frame after the others are updated.
 */
final class WindowsIndicatorWindow {

    private final boolean grows;
    private final WinDef.HWND hwnd;
    // Kept in a field: the callback must outlive the registration.
    private final WinUser.WindowProc windowProc;
    private WinDef.HDC memoryDc;
    private WinNT.HANDLE previousBitmap;
    private WinDef.HBITMAP dib;
    private ByteBuffer dibBuffer;
    private int dibWidth;
    private int dibHeight;
    private QImage image;
    private Rectangle rectangle = new Rectangle(0, 0, 1, 1);
    private List<WindowImage> windowImages = List.of();
    private boolean showing;

    /** A window that grows keeps its rectangle while its images move and grow a little. */
    WindowsIndicatorWindow(boolean grows) {
        this.grows = grows;
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

    /** The images, in z order. */
    void show(List<WindowImage> windowImages) {
        Rectangle imagesRectangle = windowImages.isEmpty() ? null :
                Rectangle.union(windowImages.stream().map(WindowImage::rectangle).toList());
        Rectangle rectangle = imagesRectangle == null ?
                new Rectangle(this.rectangle.x(), this.rectangle.y(), 1, 1) :
                grows ? grownRectangle(imagesRectangle) : imagesRectangle;
        if (showing && rectangle.width() == this.rectangle.width() &&
            rectangle.height() == this.rectangle.height() &&
            sameInRectangle(windowImages, rectangle)) {
            if (!rectangle.equals(this.rectangle))
                updateLayeredWindow(rectangle, null);
            this.rectangle = rectangle;
            this.windowImages = windowImages;
            return;
        }
        Rectangle dirty = showing && rectangle.equals(this.rectangle) ?
                IndicatorRenderer.changedRectangle(windowImages, this.windowImages)
                                 .intersection(rectangle) : rectangle;
        this.rectangle = rectangle;
        this.windowImages = windowImages;
        if (dirty.isEmpty())
            return;
        reserve(rectangle.width(), rectangle.height());
        draw(dirty);
        updateLayeredWindow(rectangle, dirty.equals(rectangle) ? null : dirty);
        if (!showing) {
            showing = true;
            User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_SHOWNOACTIVATE);
        }
    }

    private boolean sameInRectangle(List<WindowImage> windowImages, Rectangle rectangle) {
        if (windowImages.size() != this.windowImages.size())
            return false;
        for (int index = 0; index < windowImages.size(); index++) {
            WindowImage windowImage = windowImages.get(index);
            WindowImage shownWindowImage = this.windowImages.get(index);
            if (windowImage.image() != shownWindowImage.image() ||
                windowImage.x() - rectangle.x() != shownWindowImage.x() - this.rectangle.x() ||
                windowImage.y() - rectangle.y() != shownWindowImage.y() - this.rectangle.y())
                return false;
        }
        return true;
    }

    private Rectangle grownRectangle(Rectangle imagesRectangle) {
        List<Rectangle> screenRectangles =
                WindowsScreen.findScreens().stream().map(Screen::rectangle).toList();
        Rectangle visibleRectangle =
                imagesRectangle.intersection(Rectangle.union(screenRectangles));
        if (!windowImages.isEmpty() && rectangle.contains(visibleRectangle) &&
            4L * visibleRectangle.width() * visibleRectangle.height() >=
            (long) rectangle.width() * rectangle.height())
            return rectangle;
        return grownRectangle(visibleRectangle, screenRectangles);
    }

    /**
     * The rectangle of the visible images with a margin, inside the screens. It never covers a
     * whole screen the images do not cover: the shell would demote the taskbar under it.
     */
    static Rectangle grownRectangle(Rectangle visibleRectangle,
                                    List<Rectangle> screenRectangles) {
        Rectangle screens = Rectangle.union(screenRectangles);
        int margin = Math.max(visibleRectangle.width(), visibleRectangle.height()) / 4;
        int left = Math.max(visibleRectangle.x() - margin, screens.x());
        int top = Math.max(visibleRectangle.y() - margin, screens.y());
        int right = Math.min(visibleRectangle.x() + visibleRectangle.width() + margin,
                screens.x() + screens.width());
        int bottom = Math.min(visibleRectangle.y() + visibleRectangle.height() + margin,
                screens.y() + screens.height());
        for (Rectangle screen : screenRectangles) {
            Rectangle rectangle = new Rectangle(left, top, right - left, bottom - top);
            if (!rectangle.contains(screen) || visibleRectangle.contains(screen))
                continue;
            if (visibleRectangle.y() > screen.y())
                top = screen.y() + 1;
            else if (visibleRectangle.y() + visibleRectangle.height() <
                     screen.y() + screen.height())
                bottom = screen.y() + screen.height() - 1;
            else if (visibleRectangle.x() > screen.x())
                left = screen.x() + 1;
            else
                right = screen.x() + screen.width() - 1;
        }
        return new Rectangle(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
    }

    /** Draws the images where the area is, and copies that to the DIB. */
    private void draw(Rectangle area) {
        int left = area.x() - rectangle.x();
        int top = area.y() - rectangle.y();
        QPainter painter = new QPainter(image);
        painter.setClipRect(left, top, area.width(), area.height());
        painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_Source);
        QColor transparent = new QColor(0, 0, 0, 0);
        painter.fillRect(left, top, area.width(), area.height(), transparent);
        transparent.dispose();
        painter.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceOver);
        for (WindowImage windowImage : windowImages)
            if (!windowImage.rectangle().intersection(area).isEmpty())
                painter.drawImage(windowImage.x() - rectangle.x(),
                        windowImage.y() - rectangle.y(), windowImage.image());
        painter.end();
        painter.dispose();
        ByteBuffer bits = image.bits();
        for (int row = top; row < top + area.height(); row++) {
            int offset = 4 * (row * dibWidth + left);
            dibBuffer.put(offset, bits, offset, 4 * area.width());
        }
    }

    /** The whole window when dirty is null. */
    private void updateLayeredWindow(Rectangle rectangle, Rectangle dirty) {
        WinDef.POINT position = new WinDef.POINT(rectangle.x(), rectangle.y());
        WinUser.BLENDFUNCTION blend = new WinUser.BLENDFUNCTION();
        blend.BlendOp = WinUser.AC_SRC_OVER;
        blend.SourceConstantAlpha = (byte) 255;
        blend.AlphaFormat = WinUser.AC_SRC_ALPHA;
        if (dirty == null) {
            User32.INSTANCE.UpdateLayeredWindow(hwnd, null, position,
                    new WinUser.SIZE(rectangle.width(), rectangle.height()), memoryDc,
                    new WinDef.POINT(0, 0), 0, blend, WinUser.ULW_ALPHA);
            return;
        }
        WinUser.SIZE size = new WinUser.SIZE(rectangle.width(), rectangle.height());
        WinDef.POINT source = new WinDef.POINT(0, 0);
        WinDef.RECT dirtyRect = new WinDef.RECT();
        dirtyRect.left = dirty.x() - rectangle.x();
        dirtyRect.top = dirty.y() - rectangle.y();
        dirtyRect.right = dirtyRect.left + dirty.width();
        dirtyRect.bottom = dirtyRect.top + dirty.height();
        position.write();
        size.write();
        source.write();
        blend.write();
        dirtyRect.write();
        ExtendedUser32.UPDATELAYEREDWINDOWINFO info =
                new ExtendedUser32.UPDATELAYEREDWINDOWINFO();
        info.cbSize = info.size();
        info.pptDst = position.getPointer();
        info.psize = size.getPointer();
        info.hdcSrc = memoryDc;
        info.pptSrc = source.getPointer();
        info.pblend = blend.getPointer();
        info.dwFlags = WinUser.ULW_ALPHA;
        info.prcDirty = dirtyRect.getPointer();
        ExtendedUser32.INSTANCE.UpdateLayeredWindowIndirect(hwnd, info);
    }

    /** The DIB, and the image drawn into before it is copied there, only grow. */
    private void reserve(int width, int height) {
        if (dib != null && width <= dibWidth && height <= dibHeight)
            return;
        width = Math.max(width, dibWidth);
        height = Math.max(height, dibHeight);
        if (dib != null) {
            GDI32.INSTANCE.SelectObject(memoryDc, previousBitmap);
            GDI32.INSTANCE.DeleteObject(dib);
            GDI32.INSTANCE.DeleteDC(memoryDc);
            image.dispose();
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
        Pointer dibBits = bits.getValue();
        dibBuffer = dibBits.getByteBuffer(0, 4L * width * height);
        dibWidth = width;
        dibHeight = height;
        image = new QImage(width, height, QImage.Format.Format_ARGB32_Premultiplied);
    }

}
