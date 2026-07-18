package mousemaster.platform.windows;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import io.qt.gui.QImage;
import io.qt.gui.QPainter;
import io.qt.gui.QPixmap;
import io.qt.widgets.QApplication;
import mousemaster.*;
import mousemaster.platform.Overlay;
import mousemaster.qt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

public class WindowsOverlay implements Overlay {

    private static final Logger logger = LoggerFactory.getLogger(WindowsOverlay.class);

    private final WindowsMouseController mouse;
    private boolean waitForZoom;
    private IndicatorRenderer indicatorRenderer;
    private WinDef.HWND indicatorHwnd;
    private GridRenderer gridRenderer;
    private WinDef.HWND gridHwnd;
    /** Owns no QWidget, so it can be created eagerly (no QtJambi native-load ordering). */
    private final HintMeshRenderer hintMeshRenderer;
    private boolean zoomAfterHintMeshEndAnimation;
    private Zoom afterHintMeshEndAnimationZoom;
    private ZoomWindow zoomWindow, standByZoomWindow;
    private Zoom currentZoom;
    private boolean mustUpdateMagnifierSource;
    // Screenshot-based zoom animation fields.
    private ScreenshotWidget screenshotWidget;
    private WinDef.HWND screenshotHwnd;
    private QPixmap screenshotPixmap;
    private boolean screenshotAnimating;
    private boolean screenshotPendingHide;
    private Runnable messagePump;

    public WindowsOverlay(WindowsMouseController mouse) {
        this.mouse = mouse;
        hintMeshRenderer = new HintMeshRenderer(this::createStyledHintMeshWindow,
                this::hintMeshEndAnimationEndedCallback);
    }

    @Override
    public boolean waitForZoomBeforeRepainting() {
        return waitForZoom;
    }

    @Override
    public void setWaitForZoomBeforeRepainting(boolean waitForZoom) {
        this.waitForZoom = waitForZoom;
    }

    @Override
    public void setMessagePump(Runnable pump) {
        messagePump = pump;
        hintMeshRenderer.setMessagePump(pump);
    }

    /** The native handle of a hint mesh window, derived from its Qt window. */
    private WinDef.HWND hwnd(TransparentWindow window) {
        return new WinDef.HWND(new Pointer(window.winId()));
    }

    /** Runs when the hint container end-animation finishes: hides the hint mesh, then
     *  applies any zoom that was deferred until the animation finished. */
    private void hintMeshEndAnimationEndedCallback() {
        hideHintMesh();
        if (zoomAfterHintMeshEndAnimation) {
            zoomAfterHintMeshEndAnimation = false;
            setZoom(afterHintMeshEndAnimationZoom);
            afterHintMeshEndAnimationZoom = null;
        }
    }

    @Override
    public void update(double delta) {
        hintMeshRenderer.runPendingWork();
        updateZoomWindow();
        // Deferred screenshot hide: the magnifier was shown by updateZoomWindow
        // on the previous frame (or by setZoom inside endScreenshotZoomAnimation).
        // Wait one frame so DWM composites the magnifier before removing
        // the screenshot that covers it.
        if (screenshotPendingHide) {
            screenshotPendingHide = false;
            // Don't hide() the widget: showing a hidden layered window
            // briefly exposes its stale surface. Instead, clear its content
            // so it becomes transparent (WA_TranslucentBackground).
            screenshotWidget.setZoom(null);
            screenshotWidget.repaint();
            if (screenshotPixmap != null) {
                screenshotWidget.setScreenshot(null, null);
                screenshotPixmap = null;
            }
        }
    }

    private void updateZoomWindow() {
        if (screenshotAnimating)
            return;
        if (currentZoom == null)
            return;
        if (mustUpdateMagnifierSource) {
            mustUpdateMagnifierSource = false;
            WinDef.RECT sourceRect = new WinDef.RECT();
            Zoom zoom = currentZoom;
            Rectangle screenRectangle = zoom.screenRectangle();
            double zoomPercent = zoom.percent();
            sourceRect.left = (int) (zoom.center().x() - screenRectangle.width() / zoomPercent / 2);
            sourceRect.top = (int) (zoom.center().y() - screenRectangle.height() / zoomPercent / 2);
            sourceRect.right = (int) (zoom.center().x() + screenRectangle.width() / zoomPercent / 2);
            sourceRect.bottom = (int) (zoom.center().y() + screenRectangle.height() / zoomPercent / 2);
            // Calls to MagSetWindowSource are expensive and last about 10-20ms.
            if (!Magnification.INSTANCE.MagSetWindowSource(zoomWindow.hwnd(),
                    sourceRect)) {
                logger.error("Failed MagSetWindowSource: " +
                             Integer.toHexString(Native.getLastError()));
            }
        }
        User32.INSTANCE.ShowWindow(zoomWindow.hostHwnd(), WinUser.SW_SHOWNORMAL);
        if (standByZoomWindow != null)
            User32.INSTANCE.ShowWindow(standByZoomWindow.hostHwnd(), WinUser.SW_HIDE);
        User32.INSTANCE.InvalidateRect(zoomWindow.hwnd(), null, true);
        setTopmost();
    }

    @Override
    public void flushCache() {
        hintMeshRenderer.flushCache();
    }

    @Override
    public Rectangle activeWindowRectangle(double windowWidthPercent,
                                                  double windowHeightPercent,
                                                  int scaledTopInset,
                                                  int scaledBottomInset,
                                                  int scaledLeftInset,
                                                  int scaledRightInset) {
        WinDef.HWND foregroundWindow = User32.INSTANCE.GetForegroundWindow();
        // https://stackoverflow.com/a/65605845
        WinDef.RECT excludeShadow = windowRectExcludingShadow(foregroundWindow);
        int windowWidth = excludeShadow.right - excludeShadow.left;
        int windowHeight = excludeShadow.bottom - excludeShadow.top;
        int noInsetGridWidth = Math.max(1, (int) (windowWidth * windowWidthPercent));
        int gridWidth =
                Math.max(1, noInsetGridWidth - scaledLeftInset - scaledRightInset);
        int noInsetGridHeight = Math.max(1, (int) (windowHeight * windowHeightPercent));
        int gridHeight =
                Math.max(1, noInsetGridHeight - scaledTopInset - scaledBottomInset);
        return new Rectangle(Math.min(excludeShadow.right,
                excludeShadow.left + scaledLeftInset +
                (windowWidth - noInsetGridWidth) / 2), Math.min(excludeShadow.bottom,
                excludeShadow.top + scaledTopInset +
                (windowHeight - noInsetGridHeight) / 2), gridWidth, gridHeight);
    }

    static WinDef.RECT windowRectExcludingShadow(WinDef.HWND hwnd) {
        // On Windows 10+, DwmGetWindowAttribute() returns the extended frame bounds excluding shadow.
        WinDef.RECT rect = new WinDef.RECT();
        Dwmapi.INSTANCE.DwmGetWindowAttribute(hwnd, Dwmapi.DWMWA_EXTENDED_FRAME_BOUNDS,
                rect, rect.size());
        return rect;
    }

    @Override
    public void setTopmost() {
        List<WinDef.HWND> hwnds = new ArrayList<>();
        // First in the hwnds list means drawn on top.
        if (gridHwnd != null)
            hwnds.add(gridHwnd);
        for (TransparentWindow window : hintMeshRenderer.windows())
            hwnds.add(hwnd(window));
        if (indicatorHwnd != null)
            hwnds.add(indicatorHwnd);
        if (screenshotAnimating) {
            if (screenshotHwnd != null)
                hwnds.add(screenshotHwnd);
        }
        else {
            // During pending hide, keep screenshot above magnifier so it covers
            // the magnifier while it renders its first frame.
            if (screenshotPendingHide && screenshotHwnd != null)
                hwnds.add(screenshotHwnd);
            if (zoomWindow != null)
                hwnds.add(zoomWindow.hostHwnd);
        }
        if (hwnds.isEmpty())
            return;
        if (currentZoom != null || screenshotAnimating) {
            // During zoom, use relative positioning to maintain z-order.
            // Avoid SetWindowPos(hwnd, HWND_TOPMOST) which causes a DWM
            // recomposition glitch visible as a brief indicator flicker.
            for (int i = 1; i < hwnds.size(); i++)
                User32.INSTANCE.SetWindowPos(hwnds.get(i), hwnds.get(i - 1),
                        0, 0, 0, 0,
                        WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE | WinUser.SWP_NOACTIVATE);
            return;
        }
        setWindowTopmost(hwnds.getFirst(), ExtendedUser32.HWND_TOPMOST);
        boolean allOtherWindowsAreBelowInOrder = true;
        for (int windowIndex = 0; windowIndex < hwnds.size() - 1; windowIndex++) {
            if (windowBelow(hwnds.get(windowIndex)).equals(hwnds.get(windowIndex + 1)))
                // For example, windowBelow(indicator).equals(grid).
                continue;
            allOtherWindowsAreBelowInOrder = false;
            break;
        }
        if (allOtherWindowsAreBelowInOrder)
            return;
        for (int windowIndex = hwnds.size() - 1; windowIndex >= 0; windowIndex--)
            setWindowTopmost(hwnds.get(windowIndex), ExtendedUser32.HWND_TOPMOST);
    }

    private WinDef.HWND windowBelow(WinDef.HWND hwnd) {
        WinDef.HWND nextHwnd =
                User32.INSTANCE.GetWindow(hwnd, new WinDef.DWORD(User32.GW_HWNDNEXT));
        return nextHwnd;
    }

    private void setWindowTopmost(WinDef.HWND hwnd, WinDef.HWND hwndTopmost) {
        User32.INSTANCE.SetWindowPos(hwnd, hwndTopmost, 0, 0, 0, 0,
                WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE);
    }


    private record ZoomWindow(WinDef.HWND hwnd, WinDef.HWND hostHwnd, WinUser.WindowProc callback) {

    }

    private void moveAndResizeIndicatorWindow() {
        moveAndResizeIndicatorWindow(mouse.findMousePosition());
    }

    private void moveAndResizeIndicatorWindow(WinDef.POINT mousePosition) {
        indicatorRenderer.reposition(mouseRectangle(mousePosition), mouse.cursorVisualCenter(),
                WindowsScreen.findActiveScreen(mousePosition), currentZoom);
    }

    /** The cursor's bounding rectangle (position + size) at the given mouse position. */
    private Rectangle mouseRectangle(WinDef.POINT mousePosition) {
        WindowsMouseController.MouseSize mouseSize = mouse.mouseSize();
        return new Rectangle(mousePosition.x, mousePosition.y,
                mouseSize.width(), mouseSize.height());
    }

    private void createIndicatorWindow() {
        indicatorRenderer = new IndicatorRenderer();
        indicatorHwnd = new WinDef.HWND(new Pointer(indicatorRenderer.window().winId()));
        applyOverlayExStyles(indicatorHwnd);
        updateZoomExcludedWindows();
    }

    private int scaledPixels(double originalInPixels, double scale) {
        return (int) Math.floor(originalInPixels * scale * zoomPercent());
    }

    private double zoomPercent() {
        if (currentZoom == null)
            return 1;
        return currentZoom.percent();
    }

    private void applyOverlayExStyles(WinDef.HWND hwnd) {
        long currentStyle =
                User32.INSTANCE.GetWindowLongPtr(hwnd, WinUser.GWL_EXSTYLE)
                               .longValue();
        long newStyle = currentStyle | User32.WS_EX_TOPMOST |
                        ExtendedUser32.WS_EX_NOACTIVATE |
                        ExtendedUser32.WS_EX_TOOLWINDOW |
                        ExtendedUser32.WS_EX_LAYERED | ExtendedUser32.WS_EX_TRANSPARENT;
        User32.INSTANCE.SetWindowLongPtr(hwnd, WinUser.GWL_EXSTYLE,
                new Pointer(newStyle));
    }

    private Rectangle virtualDesktopBounds() {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Screen screen : WindowsScreen.findScreens()) {
            Rectangle r = screen.rectangle();
            minX = Math.min(minX, r.x());
            minY = Math.min(minY, r.y());
            maxX = Math.max(maxX, r.x() + r.width());
            maxY = Math.max(maxY, r.y() + r.height());
        }
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    /** The window factory the renderer uses: a styled, transparent, click-through window. */
    private TransparentWindow createStyledHintMeshWindow() {
        TransparentWindow window = new TransparentWindow();
        WinDef.HWND hwnd = new WinDef.HWND(new Pointer(window.winId()));
        long currentStyle =
                User32.INSTANCE.GetWindowLongPtr(hwnd, WinUser.GWL_EXSTYLE)
                               .longValue();
        long newStyle = currentStyle | ExtendedUser32.WS_EX_NOACTIVATE |
                        ExtendedUser32.WS_EX_TOOLWINDOW |
                        ExtendedUser32.WS_EX_LAYERED | ExtendedUser32.WS_EX_TRANSPARENT;
        User32.INSTANCE.SetWindowLongPtr(hwnd, WinUser.GWL_EXSTYLE,
                new Pointer(newStyle));
        return window;
    }

    @Override
    public void preWarmHintMeshWindows() {
        hintMeshRenderer.preWarmHintMeshWindows(WindowsScreen.findScreens());
        updateZoomExcludedWindows();
    }

    /**
     * Pre-warms the GDI font engine with all hint fonts from the configuration.
     * The first QFontMetrics.horizontalAdvance() call for a given font triggers lazy
     * GDI font engine initialization (~130ms). By doing this at startup, we shift that
     * cost away from the first hint mesh render.
     */
    @Override
    public void preWarmFontStyles(Set<HintMeshConfiguration> hintMeshConfigurations) {
        QtHintFont.preWarm(hintMeshConfigurations);
    }

    private WinDef.HWND createZoomWindow() {
        if (!Magnification.INSTANCE.MagInitialize())
            logger.error("Failed MagInitialize: " +
                         Integer.toHexString(Native.getLastError()));
        WinUser.WNDCLASSEX wClass = new WinUser.WNDCLASSEX();
        WinUser.WindowProc callback = this::zoomWindowCallback;
        wClass.hbrBackground = null;
        String WC_MAGNIFIER = "Magnifier";
        wClass.lpszClassName = "MagnifierWindow";
        wClass.lpfnWndProc = callback;
        WinDef.ATOM registerClassExResult = User32.INSTANCE.RegisterClassEx(wClass);
        int MS_SHOWMAGNIFIEDCURSOR = 0x0001;
        WinDef.HMODULE hInstance = Kernel32.INSTANCE.GetModuleHandle(null);
        WinDef.HWND hostHwnd = User32.INSTANCE.CreateWindowEx(
                User32.WS_EX_TOPMOST | ExtendedUser32.WS_EX_LAYERED |
                ExtendedUser32.WS_EX_TOOLWINDOW | ExtendedUser32.WS_EX_NOACTIVATE |
                ExtendedUser32.WS_EX_TRANSPARENT,
                wClass.lpszClassName, "MousemasterMagnifierHostName",
                WinUser.WS_POPUP,
                0, 0, 10, 10, null, null,
                hInstance, null);
        // When uncommenting this SetLayeredWindowAttributes call, a black frame is
        // drawn the first time the zoom is used.
//        User32.INSTANCE.SetLayeredWindowAttributes(hostHwnd, 0, (byte) 255,
//                WinUser.LWA_ALPHA);
        WinDef.HWND hwnd = User32.INSTANCE.CreateWindowEx(
                0,
                WC_MAGNIFIER, "MagnifierWindow",
                User32.WS_CHILD | MS_SHOWMAGNIFIEDCURSOR | ExtendedUser32.WS_VISIBLE,
                0, 0, 10, 10,
                hostHwnd, null,
                hInstance, null);
        zoomWindow = new ZoomWindow(hwnd, hostHwnd, callback);
        updateZoomExcludedWindows();
        return hostHwnd;
    }

    @Override
    public void setIndicator(Indicator indicator,
                                    boolean fadeAnimationEnabled,
                                    Duration fadeAnimationDuration,
                                    boolean allowFade) {
        Objects.requireNonNull(indicator);
        if (mouse.tryFindMousePosition() == null) {
            logger.warn("Unable to find mouse position for indicator");
            return;
        }
        if (indicatorHwnd == null)
            createIndicatorWindow();
        WinDef.POINT mousePosition = mouse.findMousePosition();
        indicatorRenderer.setIndicator(indicator, fadeAnimationEnabled,
                fadeAnimationDuration, allowFade, mouseRectangle(mousePosition),
                mouse.cursorVisualCenter(),
                WindowsScreen.findActiveScreen(mousePosition), currentZoom);
    }

    private void createScreenshotWindow() {
        screenshotWidget = new ScreenshotWidget();
        screenshotHwnd = new WinDef.HWND(new Pointer(screenshotWidget.winId()));
        long currentStyle =
                User32.INSTANCE.GetWindowLongPtr(screenshotHwnd, WinUser.GWL_EXSTYLE)
                               .longValue();
        long newStyle = currentStyle | ExtendedUser32.WS_EX_NOACTIVATE |
                        ExtendedUser32.WS_EX_TOOLWINDOW |
                        ExtendedUser32.WS_EX_LAYERED |
                        ExtendedUser32.WS_EX_TRANSPARENT;
        User32.INSTANCE.SetWindowLongPtr(screenshotHwnd, WinUser.GWL_EXSTYLE,
                new Pointer(newStyle));
        // Make topmost so it's in the same z-band as the magnifier.
        User32.INSTANCE.SetWindowPos(screenshotHwnd, ExtendedUser32.HWND_TOPMOST,
                0, 0, 0, 0,
                WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE | WinUser.SWP_NOACTIVATE);
    }

    @Override
    public void startScreenshotZoomAnimation(Rectangle screenRect,
                                                      Zoom beginZoom) {
        boolean interruptingMidAnimation = screenshotAnimating;
        if (screenshotAnimating || screenshotPendingHide) {
            screenshotAnimating = false;
            screenshotPendingHide = false;
        }
        if (screenshotWidget == null)
            createScreenshotWindow();
        screenshotAnimating = true;
        screenshotWidget.move(screenRect.x(), screenRect.y());
        screenshotWidget.resize(screenRect.width(), screenRect.height());
        if (interruptingMidAnimation && screenshotPixmap != null) {
            // Reuse existing pixmap only during mid-animation interruption:
            // the screenshot widget is already visible with the correct
            // 1x desktop content.
            return;
        }
        // Exclude all our windows from capture via WDA so grabWindow
        // sees only the desktop content underneath.
        boolean magnifierVisible = zoomWindow != null && currentZoom != null;
        if (magnifierVisible) {
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                    zoomWindow.hostHwnd(),
                    ExtendedUser32.WDA_EXCLUDEFROMCAPTURE);
            if (standByZoomWindow != null)
                ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                        standByZoomWindow.hostHwnd(),
                        ExtendedUser32.WDA_EXCLUDEFROMCAPTURE);
        }
        if (indicatorRenderer != null && indicatorRenderer.showing())
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                    indicatorHwnd,
                    ExtendedUser32.WDA_EXCLUDEFROMCAPTURE);
        for (TransparentWindow window : hintMeshRenderer.windows())
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                    hwnd(window),
                    ExtendedUser32.WDA_EXCLUDEFROMCAPTURE);
        if (screenshotHwnd != null)
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                    screenshotHwnd, ExtendedUser32.WDA_EXCLUDEFROMCAPTURE);
        QPixmap capture = QApplication.primaryScreen().grabWindow(
                0, screenRect.x(), screenRect.y(),
                screenRect.width(), screenRect.height());
        // Restore WDA on all windows.
        if (magnifierVisible) {
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                    zoomWindow.hostHwnd(), ExtendedUser32.WDA_NONE);
            if (standByZoomWindow != null)
                ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                        standByZoomWindow.hostHwnd(), ExtendedUser32.WDA_NONE);
        }
        if (indicatorRenderer != null && indicatorRenderer.showing())
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                    indicatorHwnd, ExtendedUser32.WDA_NONE);
        for (TransparentWindow window : hintMeshRenderer.windows())
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                    hwnd(window), ExtendedUser32.WDA_NONE);
        if (screenshotHwnd != null)
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(
                    screenshotHwnd, ExtendedUser32.WDA_NONE);
        drawCursorOnto(capture, screenRect);
        screenshotPixmap = capture;
        screenshotWidget.setScreenshot(capture, screenRect);
        currentZoom = beginZoom;
        screenshotWidget.setZoom(beginZoom);
        if (!screenshotWidget.isVisible())
            screenshotWidget.show();
        screenshotWidget.repaint();
        setTopmost();
    }

    private void drawCursorOnto(QPixmap pixmap, Rectangle screenRect) {
        ExtendedUser32.CURSORINFO cursorInfo = new ExtendedUser32.CURSORINFO();
        if (!ExtendedUser32.INSTANCE.GetCursorInfo(cursorInfo) ||
            cursorInfo.hCursor == null)
            return;
        WinGDI.ICONINFO iconInfo = new WinGDI.ICONINFO();
        if (!User32.INSTANCE.GetIconInfo(
                new WinDef.HICON(cursorInfo.hCursor), iconInfo))
            return;
        try {
            WinGDI.BITMAP bmpInfo = new WinGDI.BITMAP();
            WinDef.HBITMAP sizeBmp = iconInfo.hbmColor != null
                    ? iconInfo.hbmColor : iconInfo.hbmMask;
            GDI32.INSTANCE.GetObject(sizeBmp, bmpInfo.size(),
                    bmpInfo.getPointer());
            bmpInfo.read();
            int width = bmpInfo.bmWidth.intValue();
            int height = bmpInfo.bmHeight.intValue();
            if (iconInfo.hbmColor == null)
                height /= 2; // Monochrome: double-height (AND + XOR).
            if (width <= 0 || height <= 0)
                return;
            int drawX = cursorInfo.ptScreenPos.x - iconInfo.xHotspot
                    - screenRect.x();
            int drawY = cursorInfo.ptScreenPos.y - iconInfo.yHotspot
                    - screenRect.y();
            if (iconInfo.hbmColor != null) {
                // Read color bitmap as 32-bit BGRA.
                byte[] colorData = readBitmap32(iconInfo.hbmColor, width, height);
                if (colorData == null)
                    return;
                // Check if this is a standard alpha cursor or an XOR cursor.
                boolean hasAlpha = false;
                for (int i = 3; i < colorData.length; i += 4) {
                    if (colorData[i] != 0) {
                        hasAlpha = true;
                        break;
                    }
                }
                if (hasAlpha) {
                    // Standard alpha cursor — draw directly.
                    QImage cursorImage = new QImage(colorData, width, height,
                            QImage.Format.Format_ARGB32);
                    QPainter painter = new QPainter(pixmap);
                    painter.drawImage(drawX, drawY, cursorImage);
                    painter.end();
                    painter.dispose();
                    cursorImage.dispose();
                }
                else {
                    // XOR cursor (e.g. I-beam): alpha=0, non-black RGB is XOR mask.
                    // Read AND mask to determine opaque vs XOR pixels.
                    byte[] maskData = iconInfo.hbmMask != null
                            ? readBitmap32(iconInfo.hbmMask, width, height)
                            : null;
                    // Read background from pixmap for XOR blending.
                    QImage bgImage = pixmap.toImage();
                    byte[] resultData = new byte[width * height * 4];
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int idx = (y * width + x) * 4;
                            int cB = colorData[idx] & 0xFF;
                            int cG = colorData[idx + 1] & 0xFF;
                            int cR = colorData[idx + 2] & 0xFF;
                            // AND mask: 0x000000=opaque, 0xFFFFFF=transparent/XOR.
                            boolean andTransparent = true;
                            if (maskData != null) {
                                andTransparent =
                                        (maskData[idx] & 0xFF) != 0 ||
                                        (maskData[idx + 1] & 0xFF) != 0 ||
                                        (maskData[idx + 2] & 0xFF) != 0;
                            }
                            if (!andTransparent) {
                                // AND=0: opaque pixel, use color directly.
                                resultData[idx] = (byte) cB;
                                resultData[idx + 1] = (byte) cG;
                                resultData[idx + 2] = (byte) cR;
                                resultData[idx + 3] = (byte) 0xFF;
                            }
                            else if (cB != 0 || cG != 0 || cR != 0) {
                                // AND=1, color!=0: XOR with background.
                                int px = drawX + x;
                                int py = drawY + y;
                                if (px >= 0 && px < bgImage.width() &&
                                    py >= 0 && py < bgImage.height()) {
                                    int bgPixel = bgImage.pixel(px, py);
                                    int bgR = (bgPixel >> 16) & 0xFF;
                                    int bgG = (bgPixel >> 8) & 0xFF;
                                    int bgB = bgPixel & 0xFF;
                                    resultData[idx] = (byte) (bgB ^ cB);
                                    resultData[idx + 1] = (byte) (bgG ^ cG);
                                    resultData[idx + 2] = (byte) (bgR ^ cR);
                                    resultData[idx + 3] = (byte) 0xFF;
                                }
                            }
                            // else AND=1, color=0: transparent, leave as zero.
                        }
                    }
                    QImage resultImage = new QImage(resultData, width, height,
                            QImage.Format.Format_ARGB32);
                    QPainter painter = new QPainter(pixmap);
                    painter.drawImage(drawX, drawY, resultImage);
                    painter.end();
                    painter.dispose();
                    resultImage.dispose();
                    bgImage.dispose();
                }
            }
            // else: mask-only (monochrome) cursor — skip for now.
        }
        finally {
            if (iconInfo.hbmColor != null)
                GDI32.INSTANCE.DeleteObject(iconInfo.hbmColor);
            if (iconInfo.hbmMask != null)
                GDI32.INSTANCE.DeleteObject(iconInfo.hbmMask);
        }
    }

    private byte[] readBitmap32(WinDef.HBITMAP bitmap, int width,
                                       int height) {
        WinGDI.BITMAPINFO bi = new WinGDI.BITMAPINFO();
        bi.bmiHeader.biWidth = width;
        bi.bmiHeader.biHeight = -height; // Top-down.
        bi.bmiHeader.biPlanes = 1;
        bi.bmiHeader.biBitCount = 32;
        Memory pixels = new Memory((long) width * height * 4);
        WinDef.HDC hdc = GDI32.INSTANCE.CreateCompatibleDC(null);
        int result = GDI32.INSTANCE.GetDIBits(hdc, bitmap, 0, height,
                pixels, bi, WinGDI.DIB_RGB_COLORS);
        GDI32.INSTANCE.DeleteDC(hdc);
        if (result == 0)
            return null;
        return pixels.getByteArray(0, width * height * 4);
    }

    @Override
    public void updateScreenshotZoom(Zoom zoom) {
        if (!screenshotAnimating)
            return;
        currentZoom = zoom;
        screenshotWidget.setZoom(zoom);
        screenshotWidget.repaint();
        if (indicatorHwnd != null)
            moveAndResizeIndicatorWindow();
        setTopmost();
    }

    @Override
    public void endScreenshotZoomAnimation(Zoom finalZoom) {
        if (!screenshotAnimating)
            return;
        screenshotAnimating = false;
        // Reset so setZoom(null) doesn't early-return with stale currentZoom.
        currentZoom = null;
        if (finalZoom != null) {
            // Defer screenshot hide by one frame so magnifier renders first.
            screenshotPendingHide = true;
            setZoom(finalZoom);
        }
        else {
            setZoom(null);
            // Don't hide() the widget: showing a hidden layered window
            // briefly exposes its stale surface. Instead, clear its content
            // so it becomes transparent (WA_TranslucentBackground).
            screenshotWidget.setZoom(null);
            screenshotWidget.repaint();
            if (screenshotPixmap != null) {
                screenshotWidget.setScreenshot(null, null);
                screenshotPixmap = null;
            }
        }
    }

    @Override
    public void setZoom(Zoom zoom) {
        if (currentZoom != null && currentZoom.equals(zoom))
            return;
        if (hintMeshRenderer.isHintMeshEndAnimation()) {
            if (!zoomAfterHintMeshEndAnimation) {
                zoomAfterHintMeshEndAnimation = true;
                afterHintMeshEndAnimationZoom = zoom;
                return;
            }
            else {
                // We skip the enqueued zoom.
                zoomAfterHintMeshEndAnimation = false;
                afterHintMeshEndAnimationZoom = null;
            }
        }
        if (zoomWindow == null) {
            if (zoom == null)
                return;
            createZoomWindow();
        }
        Zoom oldZoom = currentZoom;
        currentZoom = zoom;
        mustUpdateMagnifierSource = true;
        if (currentZoom == null) {
            User32.INSTANCE.ShowWindow(zoomWindow.hostHwnd(), WinUser.SW_HIDE);
        }
        else {
            // We use a second zoom window to keep the already open zoom window visible,
            // until the second zoom is ready.
            // Because MagSetWindowTransform() will immediately show the new zoom area,
            // except only the zoom percent has been set so far (the new source will be updated by
            // MagSetWindowSource, next frame only).
            Rectangle screenRectangle = zoom.screenRectangle();
            if (oldZoom == null || oldZoom.percent() != zoom.percent()) {
                if (standByZoomWindow == null) {
                    standByZoomWindow = zoomWindow;
                    createZoomWindow();
                }
                else {
                    ZoomWindow newStandByZoomWindow = zoomWindow;
                    zoomWindow = standByZoomWindow;
                    standByZoomWindow = newStandByZoomWindow;
                    updateZoomExcludedWindows();
                }
                // MagSetWindowTransform() can take 10-20ms.
                if (!Magnification.INSTANCE.MagSetWindowTransform(zoomWindow.hwnd(),
                        new Magnification.MAGTRANSFORM.ByReference(
                                (float) zoomPercent())))
                    logger.error("Failed MagSetWindowTransform: " +
                                 Integer.toHexString(Native.getLastError()));
            }
            User32.INSTANCE.SetWindowPos(zoomWindow.hostHwnd(), null,
                    screenRectangle.x(), screenRectangle.y(),
                    screenRectangle.width(), screenRectangle.height(),
                    User32.SWP_NOZORDER);
            User32.INSTANCE.SetWindowPos(zoomWindow.hwnd(), null,
                    0, 0,
                    screenRectangle.width(), screenRectangle.height(),
                    User32.SWP_NOZORDER);
        }
        if (indicatorHwnd != null) {
            moveAndResizeIndicatorWindow();
        }
        if (hintMeshRenderer.showing()) {
            for (TransparentWindow window : hintMeshRenderer.windows())
                User32.INSTANCE.InvalidateRect(hwnd(window), null, true);
        }
        updateZoomWindow();
    }

    private void updateZoomExcludedWindows() {
        if (zoomWindow == null)
            return;
        List<WinDef.HWND> hwnds = new ArrayList<>();
        if (gridHwnd != null)
            hwnds.add(gridHwnd);
        for (TransparentWindow window : hintMeshRenderer.windows())
            hwnds.add(hwnd(window));
        if (indicatorHwnd != null)
            hwnds.add(indicatorHwnd);
        if (standByZoomWindow != null)
            hwnds.add(standByZoomWindow.hwnd);
        if (screenshotHwnd != null)
            hwnds.add(screenshotHwnd);
        if (hwnds.isEmpty())
            return;
        if (!Magnification.INSTANCE.MagSetWindowFilterList(zoomWindow.hwnd(),
                Magnification.MW_FILTERMODE_EXCLUDE, hwnds.size(),
                hwnds.toArray(new WinDef.HWND[0])))
            logger.error("Failed to set the zoom excluded window list: " +
                         Integer.toHexString(Native.getLastError()));
    }

    private WinDef.LRESULT zoomWindowCallback(WinDef.HWND hwnd, int uMsg,
                                                     WinDef.WPARAM wParam,
                                                     WinDef.LPARAM lParam) {
//        switch (uMsg) {
//            case WinUser.WM_PAINT:
//                if (currentZoom == null) {
//                    ExtendedUser32.PAINTSTRUCT ps = new ExtendedUser32.PAINTSTRUCT();
//                    WinDef.HDC hdc = ExtendedUser32.INSTANCE.BeginPaint(hwnd, ps);
//                    clearWindow(hdc, ps.rcPaint, 0);
//                    ExtendedUser32.INSTANCE.EndPaint(hwnd, ps);
//                }
//                break;
//        }
        return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
    }

    @Override
    public void hideIndicator(boolean allowFade) {
        if (indicatorRenderer != null)
            indicatorRenderer.hide(allowFade);
    }

    @Override
    public void setGrid(Grid grid) {
        Objects.requireNonNull(grid);
        boolean firstCreation = gridHwnd == null;
        if (firstCreation) {
            gridRenderer = new GridRenderer();
            gridHwnd = new WinDef.HWND(new Pointer(gridRenderer.widget().winId()));
            applyOverlayExStyles(gridHwnd);
        }
        gridRenderer.setGrid(grid, virtualDesktopBounds(),
                scaledPixels(grid.lineThickness(), 1));
        if (firstCreation)
            updateZoomExcludedWindows();
    }

    /**
     * The reason we don't call setHintMesh with the match hint is because
     * that does not keep the prefix box borders of the previous hint mesh.
     */
    @Override
    public void animateHintMatch(Hint hint) {
        hintMeshRenderer.animateHintMatch(hint, WindowsScreen.findScreens());
    }

    @Override
    public void setHintMesh(HintMesh hintMesh, Zoom zoom) {
        setHintMesh(hintMesh, zoom, false);
    }

    @Override
    public void setHintMesh(HintMesh hintMesh, Zoom zoom, boolean hintMatch) {
        int windowsBefore = hintMeshRenderer.windows().size();
        boolean nonMatchShown = hintMeshRenderer.setHintMesh(hintMesh, zoom, hintMatch,
                WindowsScreen.findScreens());
        if (nonMatchShown && zoomAfterHintMeshEndAnimation) {
            zoomAfterHintMeshEndAnimation = false;
            setZoom(afterHintMeshEndAnimationZoom);
            afterHintMeshEndAnimationZoom = null;
        }
        if (hintMeshRenderer.windows().size() > windowsBefore)
            updateZoomExcludedWindows();
    }

    @Override
    public void hideGrid() {
        if (gridRenderer != null)
            gridRenderer.hide();
    }

    @Override
    public void hideHintMesh() {
        hintMeshRenderer.hideHintMesh();
    }

    void mouseMoved(WinDef.POINT mousePosition) {
        if (indicatorHwnd == null)
             return;
        // During zoom, currentZoom still has the previous frame's zoom center
        // (it will be updated right after by ZoomManager in WindowsPlatform.sleep).
        // Positioning here would use that stale center, causing a brief
        // mispositioning until setZoom() corrects it.
        if (currentZoom != null)
            return;
        moveAndResizeIndicatorWindow(mousePosition);
    }

}
