package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.PointerByReference;
import mousemaster.WindowsMouse.MouseSize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class WindowsOverlay {

    private static final Logger logger = LoggerFactory.getLogger(WindowsOverlay.class);

    private static final int indicatorEdgeThreshold = 100; // in pixels
    public static boolean waitForZoomBeforeRepainting;

    private static IndicatorWindow indicatorWindow;
    private static boolean showingIndicator;
    private static Indicator currentIndicator;
    private static GridWindow gridWindow, standByGridWindow;
    private static boolean standByGridCanBeHidden;
    private static boolean showingGrid;
    private static Grid currentGrid;
    private static final Map<Screen, HintMeshWindow> hintMeshWindows =
            new LinkedHashMap<>(); // Ordered for topmost handling.
    private static boolean showingHintMesh;
    private static HintMesh currentHintMesh;
    private static ZoomWindow zoomWindow, standByZoomWindow;
    private static Zoom currentZoom;
    private static Screen currentZoomScreen;
    private static boolean mustUpdateMagnifierSource;

    public static void update(double delta) {
        updateZoomWindow();
    }

    private static void updateZoomWindow() {
        if (currentZoom == null)
            return;
        if (mustUpdateMagnifierSource) {
            mustUpdateMagnifierSource = false;
            WinDef.RECT sourceRect = new WinDef.RECT();
            Zoom zoom = currentZoom;
            Screen screen = currentZoomScreen;
            double zoomPercent = zoom.percent();
            sourceRect.left = (int) (zoom.center().x() - screen.rectangle().width() / zoomPercent / 2);
            sourceRect.top = (int) (zoom.center().y() - screen.rectangle().height() / zoomPercent / 2);
            sourceRect.right = (int) (zoom.center().x() + screen.rectangle().width() / zoomPercent / 2);
            sourceRect.bottom = (int) (zoom.center().y() + screen.rectangle().height() / zoomPercent / 2);
            // Calls to MagSetWindowSource are expensive and last about 10-20ms.
            if (!Magnification.INSTANCE.MagSetWindowSource(zoomWindow.hwnd(),
                    sourceRect)) {
                logger.error("Failed MagSetWindowSource: " +
                             Integer.toHexString(Native.getLastError()));
            }
        }
        User32.INSTANCE.ShowWindow(zoomWindow.hostHwnd(), WinUser.SW_SHOWNORMAL);
        User32.INSTANCE.InvalidateRect(zoomWindow.hwnd(), null, true);
        if (standByZoomWindow != null)
            User32.INSTANCE.ShowWindow(standByZoomWindow.hostHwnd(), WinUser.SW_HIDE);
        // Without a setTopmost() call here, the Zoom window would be displayed on top
        // of the indicator window for a single frame.
        setTopmost();
    }

    public static Rectangle activeWindowRectangle(double windowWidthPercent,
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

    private static WinDef.RECT windowRectExcludingShadow(WinDef.HWND hwnd) {
        // On Windows 10+, DwmGetWindowAttribute() returns the extended frame bounds excluding shadow.
        WinDef.RECT rect = new WinDef.RECT();
        Dwmapi.INSTANCE.DwmGetWindowAttribute(hwnd, Dwmapi.DWMWA_EXTENDED_FRAME_BOUNDS,
                rect, rect.size());
        return rect;
    }

    public static void setTopmost() {
        List<WinDef.HWND> hwnds = new ArrayList<>();
        // First in the hwnds list means drawn on top.
        if (gridWindow != null)
            hwnds.add(gridWindow.hwnd);
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values()) {
            hwnds.add(hintMeshWindow.hwnd);
            hwnds.add(hintMeshWindow.transparentHwnd);
        }
        if (indicatorWindow != null)
            hwnds.add(indicatorWindow.hwnd);
        if (zoomWindow != null) {
            hwnds.add(zoomWindow.hostHwnd);
        }
        if (hwnds.isEmpty())
            return;
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

    private static WinDef.HWND windowBelow(WinDef.HWND hwnd) {
        WinDef.HWND nextHwnd =
                User32.INSTANCE.GetWindow(hwnd, new WinDef.DWORD(User32.GW_HWNDNEXT));
        return nextHwnd;
    }

    private static void setWindowTopmost(WinDef.HWND hwnd, WinDef.HWND hwndTopmost) {
        User32.INSTANCE.SetWindowPos(hwnd, hwndTopmost, 0, 0, 0, 0,
                WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE);
    }

    private record IndicatorWindow(WinDef.HWND hwnd, WinUser.WindowProc callback, int transparentColor) {

    }

    private record GridWindow(WinDef.HWND hwnd, WinUser.WindowProc callback, int transparentColor) {

    }

    private record ZoomHintMesh(HintMesh hintMesh, Zoom zoom) {

    }

    private record HintMeshWindow(WinDef.HWND hwnd,
                                  WinDef.HWND transparentHwnd,
                                  WinUser.WindowProc callback,
                                  List<Hint> hints,
                                  Map<ZoomHintMesh, HintMeshDraw> hintMeshDrawCache) {

    }

    private record ZoomWindow(WinDef.HWND hwnd, WinDef.HWND hostHwnd, WinUser.WindowProc callback) {

    }

    private static int indicatorSize() {
        WinDef.POINT mousePosition = WindowsMouse.findMousePosition();
        Screen activeScreen = WindowsScreen.findActiveScreen(mousePosition);
        return scaledPixels(currentIndicator.size(), activeScreen.scale());
    }

    private static int bestIndicatorX() {
        WinDef.POINT mousePosition = WindowsMouse.findMousePosition();
        Screen activeScreen = WindowsScreen.findActiveScreen(mousePosition);
        MouseSize mouseSize = WindowsMouse.mouseSize();
        return (int) Math.round(zoomedX(bestIndicatorX(mousePosition.x, mouseSize.width(),
                activeScreen.rectangle(), indicatorSize())));
    }

    private static double zoomedX(double x) {
        if (currentZoom == null)
            return x;
        return currentZoomScreen.rectangle().width() / 2d +
                      (x - currentZoom.center().x()) *
                      currentZoom.percent();
    }

    private static double zoomedY(double y) {
        if (currentZoom == null)
            return y;
        return currentZoomScreen.rectangle().height() / 2d +
                      (y - currentZoom.center().y()) *
                      currentZoom.percent();
    }

    private static int bestIndicatorY() {
        WinDef.POINT mousePosition = WindowsMouse.findMousePosition();
        Screen activeScreen = WindowsScreen.findActiveScreen(mousePosition);
        MouseSize mouseSize = WindowsMouse.mouseSize();
        return (int) Math.round(zoomedY(bestIndicatorY(mousePosition.y, mouseSize.height(),
                activeScreen.rectangle(), indicatorSize())));
    }

    private static int bestIndicatorX(int mouseX, int cursorWidth, Rectangle screenRectangle,
                                      int scaledIndicatorSize) {
        mouseX = Math.min(screenRectangle.x() + screenRectangle.width(),
                Math.max(screenRectangle.x(), mouseX));
        boolean isNearLeftEdge = mouseX <= (screenRectangle.x() + indicatorEdgeThreshold);
        boolean isNearRightEdge = mouseX >=
                                  (screenRectangle.x() + screenRectangle.width() -
                                   indicatorEdgeThreshold);
        if (isNearRightEdge)
            return mouseX - scaledIndicatorSize;
        return mouseX + cursorWidth / 2;
    }

    private static int bestIndicatorY(int mouseY, int cursorHeight, Rectangle screenRectangle,
                                      int scaledIndicatorSize) {
        mouseY = Math.min(screenRectangle.y() + screenRectangle.height(),
                Math.max(screenRectangle.y(), mouseY));
        boolean isNearBottomEdge = mouseY >=
                                   (screenRectangle.y() + screenRectangle.height() -
                                    indicatorEdgeThreshold);
        boolean isNearTopEdge = mouseY <= (screenRectangle.y() + indicatorEdgeThreshold);
        if (isNearBottomEdge)
            return mouseY - scaledIndicatorSize;
        return mouseY + cursorHeight / 2;
    }

    private static void createIndicatorWindow() {
        WinUser.WindowProc callback = WindowsOverlay::indicatorWindowCallback;
        // +1 width and height because no line can be drawn on y = windowHeight and y = windowWidth.
        WinDef.HWND hwnd = createWindow("Indicator",
                bestIndicatorX(),
                bestIndicatorY(),
                indicatorSize() + 1,
                indicatorSize() + 1, callback);
        indicatorWindow = new IndicatorWindow(hwnd, callback, 0);
        updateZoomExcludedWindows();
    }

    private static int scaledPixels(double originalInPixels, double scale) {
        return (int) Math.floor(originalInPixels * scale * zoomPercent());
    }

    private static double zoomPercent() {
        if (currentZoom == null)
            return 1;
        return currentZoom.percent();
    }

    private static void createGridWindow(int x, int y, int width, int height) {
        WinUser.WindowProc callback = WindowsOverlay::gridWindowCallback;
        WinDef.HWND hwnd =
                createWindow("Grid" + (gridWindow == null ? 1 : 2), x, y, width, height,
                        callback);
        gridWindow = new GridWindow(hwnd, callback, 0);
        updateZoomExcludedWindows();
    }

    private static void createOrUpdateHintMeshWindows(List<Hint> hints) {
        Set<Screen> screens = WindowsScreen.findScreens();
        Map<Screen, List<Hint>> hintsByScreen = new HashMap<>();
        for (Hint hint : hints) {
            for (Screen screen : screens) {
                if (hint.cellWidth() == -1) {
                    if (!screen.rectangle().contains((int) hint.centerX(), (int) hint.centerY()))
                        continue;
                }
                else {
                    double left = hint.centerX() - hint.cellWidth() / 2;
                    double right = hint.centerX() + hint.cellWidth() / 2;
                    double top = hint.centerY() - hint.cellHeight() / 2;
                    double bottom = hint.centerY() + hint.cellHeight() / 2;
                    if (!screen.rectangle().contains(left, top) &&
                        !screen.rectangle().contains(right, top) &&
                        !screen.rectangle().contains(left, bottom) &&
                        !screen.rectangle().contains(right, bottom))
                        continue;
                }
                hintsByScreen.computeIfAbsent(screen, monitor1 -> new ArrayList<>())
                              .add(hint);
                break;
            }
        }
        for (Map.Entry<Screen, HintMeshWindow> entry : hintMeshWindows.entrySet()) {
            Screen screen = entry.getKey();
            HintMeshWindow window = entry.getValue();
            if (!hintsByScreen.containsKey(screen))
                window.hints.clear();
        }
        boolean createdAtLeastOneWindow = false;
        for (Map.Entry<Screen, List<Hint>> entry : hintsByScreen.entrySet()) {
            Screen screen = entry.getKey();
            List<Hint> hintsInScreen = entry.getValue();
            HintMeshWindow existingWindow = hintMeshWindows.get(screen);
            if (existingWindow == null) {
                WinUser.WindowProc callback = WindowsOverlay::hintMeshWindowCallback;
                WinDef.HWND hwnd = createWindow("HintMesh", screen.rectangle().x(),
                        screen.rectangle().y(), screen.rectangle().width(),
                        screen.rectangle().height(), callback);

                WinDef.HWND transparentHwnd = User32.INSTANCE.CreateWindowEx(
                        User32.WS_EX_TOPMOST | ExtendedUser32.WS_EX_TOOLWINDOW |
                        ExtendedUser32.WS_EX_NOACTIVATE
                        | ExtendedUser32.WS_EX_LAYERED
                        | ExtendedUser32.WS_EX_TRANSPARENT,
                        "Mousemaster" + "HintMesh" + "ClassName",
                        "Mousemaster" + "TransparentHintMesh" + "WindowName",
                        WinUser.WS_POPUP, screen.rectangle().x(),
                        screen.rectangle().y(), screen.rectangle().width(),
                        screen.rectangle().height(),
                        null, null,
                        null, null);
                User32.INSTANCE.ShowWindow(transparentHwnd, WinUser.SW_SHOW);
                hintMeshWindows.put(screen,
                        new HintMeshWindow(hwnd, transparentHwnd, callback,
                                hintsInScreen, new HashMap<>()));
                createdAtLeastOneWindow = true;
            }
            else {
                hintMeshWindows.put(screen,
                        new HintMeshWindow(existingWindow.hwnd,
                                existingWindow.transparentHwnd,
                                existingWindow.callback,
                                hintsInScreen, existingWindow.hintMeshDrawCache));
            }
        }
        if (createdAtLeastOneWindow)
            updateZoomExcludedWindows();
    }

    private static WinDef.HWND createWindow(String windowName, int windowX, int windowY,
                                            int windowWidth, int windowHeight,
                                            WinUser.WindowProc windowCallback) {
        WinUser.WNDCLASSEX wClass = new WinUser.WNDCLASSEX();
        wClass.hbrBackground = null;
        wClass.lpszClassName = "Mousemaster" + windowName + "ClassName";
        wClass.lpfnWndProc = windowCallback;
        WinDef.ATOM registerClassExResult = User32.INSTANCE.RegisterClassEx(wClass);
        WinDef.HWND hwnd = User32.INSTANCE.CreateWindowEx(
                User32.WS_EX_TOPMOST | ExtendedUser32.WS_EX_TOOLWINDOW | ExtendedUser32.WS_EX_NOACTIVATE
                | ExtendedUser32.WS_EX_LAYERED | ExtendedUser32.WS_EX_TRANSPARENT,
                wClass.lpszClassName, "Mousemaster" + windowName + "WindowName",
                WinUser.WS_POPUP, windowX, windowY, windowWidth, windowHeight, null, null,
                wClass.hInstance, null);
        // Will be overwritten for hint mesh to something other than 0.
        User32.INSTANCE.SetLayeredWindowAttributes(hwnd, 0, (byte) 0, WinUser.LWA_COLORKEY);
        User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_SHOWNORMAL);
        return hwnd;
    }

    private static WinDef.HWND createZoomWindow() {
        if (!Magnification.INSTANCE.MagInitialize())
            logger.error("Failed MagInitialize: " +
                         Integer.toHexString(Native.getLastError()));
        WinUser.WNDCLASSEX wClass = new WinUser.WNDCLASSEX();
        WinUser.WindowProc callback = WindowsOverlay::zoomWindowCallback;
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

    private static WinDef.LRESULT indicatorWindowCallback(WinDef.HWND hwnd, int uMsg,
                                                          WinDef.WPARAM wParam,
                                                          WinDef.LPARAM lParam) {
        switch (uMsg) {
            case WinUser.WM_PAINT:
                ExtendedUser32.PAINTSTRUCT ps = new ExtendedUser32.PAINTSTRUCT();
                WinDef.HDC hdc = ExtendedUser32.INSTANCE.BeginPaint(hwnd, ps);
                clearWindow(hdc, ps.rcPaint, 0);
                if (showingIndicator) {
                    clearWindow(hdc, ps.rcPaint,
                            hexColorStringToInt(currentIndicator.hexColor()));
                }
                ExtendedUser32.INSTANCE.EndPaint(hwnd, ps);
                break;
        }
        return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
    }

    private static WinDef.LRESULT gridWindowCallback(WinDef.HWND hwnd, int uMsg,
                                                     WinDef.WPARAM wParam,
                                                     WinDef.LPARAM lParam) {
        switch (uMsg) {
            case WinUser.WM_PAINT:
                boolean isStandByGridWindow = standByGridWindow != null &&
                                              hwnd.equals(standByGridWindow.hwnd());
                ExtendedUser32.PAINTSTRUCT ps = new ExtendedUser32.PAINTSTRUCT();
                if (!showingGrid || (isStandByGridWindow && standByGridCanBeHidden)) {
                    WinDef.HDC hdc = ExtendedUser32.INSTANCE.BeginPaint(hwnd, ps);
                    // The area has to be cleared otherwise the previous drawings will be drawn.
                    clearWindow(hdc, ps.rcPaint, 0);
                    ExtendedUser32.INSTANCE.EndPaint(hwnd, ps);
                    break;
                }
                WinDef.HDC hdc = ExtendedUser32.INSTANCE.BeginPaint(hwnd, ps);
                WinDef.HDC memDC = GDI32.INSTANCE.CreateCompatibleDC(hdc);
                // We may want to use the window's full dimension (GetClientRect) instead of rcPaint?
                int width = ps.rcPaint.right - ps.rcPaint.left;
                int height = ps.rcPaint.bottom - ps.rcPaint.top;
                WinDef.HBITMAP
                        hBitmap = GDI32.INSTANCE.CreateCompatibleBitmap(hdc, width, height);
                WinNT.HANDLE oldBitmap = GDI32.INSTANCE.SelectObject(memDC, hBitmap);
                clearWindow(memDC, ps.rcPaint, 0);
                drawGrid(memDC, ps.rcPaint);
                // Copy (blit) the off-screen buffer to the screen.
                GDI32.INSTANCE.BitBlt(hdc, 0, 0, width, height, memDC, 0, 0,
                        GDI32.SRCCOPY);
                GDI32.INSTANCE.SelectObject(memDC, oldBitmap);
                GDI32.INSTANCE.DeleteObject(hBitmap);
                GDI32.INSTANCE.DeleteDC(memDC);
                ExtendedUser32.INSTANCE.EndPaint(hwnd, ps);
                // Stand-by grid can be hidden right after the new grid is visible (drawn at least once).
                if (standByGridWindow != null && !standByGridCanBeHidden) {
                    standByGridCanBeHidden = true;
                    requestWindowRepaint(standByGridWindow.hwnd); // Drawings will be cleared.
                }
                break;
        }
        return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
    }

    private static WinDef.LRESULT hintMeshWindowCallback(WinDef.HWND hwnd, int uMsg,
                                                         WinDef.WPARAM wParam,
                                                         WinDef.LPARAM lParam) {
        switch (uMsg) {
            case WinUser.WM_PAINT:
                ExtendedUser32.PAINTSTRUCT ps = new ExtendedUser32.PAINTSTRUCT();
                // I was unable to successfully pass screen as an argument of the method.
                // With 2 screens, there are 2 callbacks, but JNA would always pass the screen reference
                // corresponding to the first ever created callback.
                // As a workaround, we don't pass the screen as an argument and find it using the hwnd.
                Screen screen = null;
                HintMeshWindow hintMeshWindow = null;
                for (Map.Entry<Screen, HintMeshWindow> entry : hintMeshWindows.entrySet()) {
                    if (entry.getValue().hwnd.equals(hwnd)) {
                        screen = entry.getKey();
                        hintMeshWindow = entry.getValue();
                        break;
                    }
                }
                if (hintMeshWindow == null) {
                    // The transparent (layered) window HWND would be the hwnd.
                    // It happens when I open PowerToys Run.
                    // Not sure why.
                    logger.debug("HintMeshWindow hwnd match not found: " + hwnd);
                    return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
                }
                if (!showingHintMesh) {
                    WinDef.HDC hdc = ExtendedUser32.INSTANCE.BeginPaint(hwnd, ps);
                    clearLayeredWindow(hdc, ps, hintMeshWindow);
                    ExtendedUser32.INSTANCE.EndPaint(hwnd, ps);
                    break;
                }
                //User32.INSTANCE.SetLayeredWindowAttributes(hwnd, 0, (byte) 0, WinUser.LWA_ALPHA);
                WinDef.HDC hdc = ExtendedUser32.INSTANCE.BeginPaint(hwnd, ps);
                if (hintMeshWindow.hints.isEmpty())
                    clearLayeredWindow(hdc, ps, hintMeshWindow);
                else
                    drawHints(hdc, ps.rcPaint, screen, hintMeshWindow,
                            hintMeshWindow.hints);
                ExtendedUser32.INSTANCE.EndPaint(hwnd, ps);
                break;
        }
        return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
    }

    private static void clearLayeredWindow(WinDef.HDC hdc, ExtendedUser32.PAINTSTRUCT ps,
                                  HintMeshWindow hintMeshWindow) {
        // The area has to be cleared otherwise the previous drawings will be drawn.
        clearWindow(hdc, ps.rcPaint, 0);

        // Clear layered window.
        int width = ps.rcPaint.right - ps.rcPaint.left;
        int height = ps.rcPaint.bottom - ps.rcPaint.top;
        WinDef.HDC hdcTemp = GDI32.INSTANCE.CreateCompatibleDC(hdc);
        WinDef.HBITMAP hbm = GDI32.INSTANCE.CreateCompatibleBitmap(hdc, width, height);
        GDI32.INSTANCE.SelectObject(hdcTemp, hbm);

        DibSection dibSection = createDibSection(width, height, hdcTemp);
        // Select the DIB section into the memory DC.
        WinNT.HANDLE oldDIBitmap = GDI32.INSTANCE.SelectObject(hdcTemp, dibSection.hDIBitmap);
        updateLayeredWindow(ps.rcPaint, hintMeshWindow, hdcTemp);

        GDI32.INSTANCE.SelectObject(hdcTemp, oldDIBitmap);
        GDI32.INSTANCE.DeleteObject(hbm);
        GDI32.INSTANCE.DeleteObject(dibSection.hDIBitmap);
        GDI32.INSTANCE.DeleteDC(hdcTemp);
    }

    private static void updateLayeredWindow(WinDef.RECT windowRect,
                                            HintMeshWindow hintMeshWindow, WinDef.HDC hdcTemp) {
        int width = windowRect.right - windowRect.left;
        int height = windowRect.bottom - windowRect.top;
        WinUser.BLENDFUNCTION blend = new WinUser.BLENDFUNCTION();
        blend.BlendOp = WinUser.AC_SRC_OVER;
        blend.SourceConstantAlpha = (byte) 255;
        blend.AlphaFormat = WinUser.AC_SRC_ALPHA;

        WinDef.POINT ptSrc = new WinDef.POINT(0, 0);
        // Position of the (layered) window was chosen when creating it. We don't want to change it.
        WinDef.POINT ptDst = null;
        // Not sure why this is necessary: the size of the (layered) window was chosen when creating it.
        WinUser.SIZE psize = new WinUser.SIZE(width, height);
        boolean updateLayeredWindow = User32.INSTANCE.UpdateLayeredWindow(
                hintMeshWindow.transparentHwnd, null, ptDst,
                psize, hdcTemp, ptSrc, 0, blend,
                WinUser.ULW_ALPHA);
    }

    private record DibSection(Pointer pixelPointer,
                              WinDef.HBITMAP hDIBitmap) {
    }

    private static DibSection createDibSection(int width, int height, WinDef.HDC hdcTemp) {
        // Create a DIB section to allow drawing with transparency.
        WinGDI.BITMAPINFO bmi = new WinGDI.BITMAPINFO();
        bmi.bmiHeader.biWidth = width;
        bmi.bmiHeader.biHeight = -height;// Rectangle height (top-down).
        bmi.bmiHeader.biPlanes = 1;
        bmi.bmiHeader.biBitCount = 32; // 32-bit color depth
        bmi.bmiHeader.biCompression = WinGDI.BI_RGB;
        PointerByReference pBits = new PointerByReference();
        WinDef.HBITMAP hDIBitmap = GDI32.INSTANCE.CreateDIBSection(hdcTemp, bmi, WinGDI.DIB_RGB_COLORS, pBits, null, 0);
        if (hDIBitmap == null)
            throw new RuntimeException("Unable to create DIB section: " +
                                       Integer.toHexString(Native.getLastError()));
        Pointer pixelPointer = pBits.getValue();
        return new DibSection(pixelPointer, hDIBitmap);
    }

    private static void clearWindow(WinDef.HDC hdc, WinDef.RECT windowRect, int color) {
        WinDef.HBRUSH hbrBackground = ExtendedGDI32.INSTANCE.CreateSolidBrush(color);
        ExtendedUser32.INSTANCE.FillRect(hdc, windowRect, hbrBackground);
        GDI32.INSTANCE.DeleteObject(hbrBackground);
    }

    private static void drawGrid(WinDef.HDC hdc, WinDef.RECT windowRect) {
        int rowCount = currentGrid.rowCount();
        int columnCount = currentGrid.columnCount();
        int cellWidth = currentGrid.width() / columnCount;
        int cellHeight = currentGrid.height() / rowCount;
        int[] polyCounts = new int[rowCount + 1 + columnCount + 1];
        WinDef.POINT[] points =
                (WinDef.POINT[]) new WinDef.POINT().toArray(polyCounts.length * 2);
        Screen screen = WindowsScreen.findActiveScreen(
                new WinDef.POINT(currentGrid.x(), currentGrid.y()));
        int scaledLineThickness = scaledPixels(currentGrid.lineThickness(), screen.scale());
        // Vertical lines
        for (int lineIndex = 0; lineIndex <= columnCount; lineIndex++) {
            int x = lineIndex == columnCount ? windowRect.right :
                    lineIndex * cellWidth;
            if (x == windowRect.left)
                x += scaledLineThickness / 2;
            else if (x == windowRect.right)
                x -= scaledLineThickness / 2 + scaledLineThickness % 2;
            points[2 * lineIndex].x = x;
            points[2 * lineIndex].y = 0;
            points[2 * lineIndex + 1].x = x;
            points[2 * lineIndex + 1].y = currentGrid.height();
            polyCounts[lineIndex] = 2;
        }
        // Horizontal lines
        int polyCountsOffset = columnCount + 1;
        int pointsOffset = 2 * polyCountsOffset;
        for (int lineIndex = 0; lineIndex <= rowCount; lineIndex++) {
            int y = lineIndex == rowCount ? windowRect.bottom :
                    lineIndex * cellHeight;
            if (y == windowRect.top)
                y += scaledLineThickness / 2;
            else if (y == windowRect.bottom)
                y -= scaledLineThickness / 2 + scaledLineThickness % 2;
            points[pointsOffset + 2 * lineIndex].x = 0;
            points[pointsOffset + 2 * lineIndex].y = y;
            points[pointsOffset + 2 * lineIndex + 1].x = currentGrid.width();
            points[pointsOffset + 2 * lineIndex + 1].y = y;
            polyCounts[polyCountsOffset + lineIndex] = 2;
        }
        String lineColor = currentGrid.lineHexColor();
        WinUser.HPEN gridPen =
                ExtendedGDI32.INSTANCE.CreatePen(ExtendedGDI32.PS_SOLID, scaledLineThickness,
                        hexColorStringToInt(lineColor));
        if (gridPen == null)
            throw new IllegalStateException("Unable to create grid pen");
        WinNT.HANDLE oldPen = GDI32.INSTANCE.SelectObject(hdc, gridPen);
        boolean polyPolylineResult = ExtendedGDI32.INSTANCE.PolyPolyline(hdc, points, polyCounts,
                polyCounts.length);
        if (!polyPolylineResult) {
            int lastError = Native.getLastError();
            throw new IllegalStateException(
                    "PolyPolyline failed with error code " + lastError);
        }
        GDI32.INSTANCE.SelectObject(hdc, oldPen);
        GDI32.INSTANCE.DeleteObject(gridPen);
    }

    /**
     * Returns the color that is drawn when a transparent color (the input color
     * with the opacity applied) is drawn on top of a white background.
     * This helps for improving the text antialiasing. Text antialiasing combines the
     * window's background color (which is ARGB transparent, but the antialiasing takes the
     * RGB non-transparent component).
     * We want the hint text to be antialiased with the effective color of the hint box
     * when the (transparent) hint box is above a white background.
     */
    public static String blendColorOverWhite(String hexColor, double opacity) {
        if (hexColor.startsWith("#"))
            hexColor = hexColor.substring(1);
        int colorInt = Integer.parseUnsignedInt(hexColor, 16);
        int inputRed = (colorInt >> 16) & 0xFF;
        int inputGreen = (colorInt >> 8) & 0xFF;
        int inputBlue = colorInt & 0xFF;
        int whiteRed = 255;
        int whiteGreen = 255;
        int whiteBlue = 255;
        int blendedRed = (int) Math.round((inputRed * opacity) + (whiteRed * (1 - opacity)));
        int blendedGreen = (int) Math.round((inputGreen * opacity) + (whiteGreen * (1 - opacity)));
        int blendedBlue = (int) Math.round((inputBlue * opacity) + (whiteBlue * (1 - opacity)));
        return String.format("%02X%02X%02X", blendedRed, blendedGreen, blendedBlue);
    }

    // color1 is background, color2 is foreground
    private static int blend(int color1, int color2, double color2Opacity) {
        int red1 = (color1 >> 16) & 0xFF;
        int green1 = (color1 >> 8) & 0xFF;
        int blue1 = color1 & 0xFF;
        int red2 = (color2 >> 16) & 0xFF;
        int green2 = (color2 >> 8) & 0xFF;
        int blue2 = color2 & 0xFF;
        int blendedRed = (int) Math.round((red2 * color2Opacity) + (red1 * (1 - color2Opacity)));
        int blendedGreen = (int) Math.round((green2 * color2Opacity) + (green1 * (1 - color2Opacity)));
        int blendedBlue = (int) Math.round((blue2 * color2Opacity) + (blue1 * (1 - color2Opacity)));
        return (blendedRed << 16) | (blendedGreen << 8) | blendedBlue;
    }

    private record HintSequenceText(Hint hint, List<HintKeyText> keyTexts) {

    }

    private record HintKeyText(String keyText, double left, double top, boolean isPrefix,
                               boolean isHighlight, boolean isSuffix) {
    }

    private record HintMeshDraw(List<WinDef.RECT> boxRects,
                                List<WinDef.RECT> cellRects,
                                List<HintSequenceText> hintSequenceTexts,
                                int[] pixelData) {
    }

    static {
        PointerByReference gdiplusToken = new PointerByReference();
        int status = Gdiplus.INSTANCE.GdiplusStartup(gdiplusToken,
                new Gdiplus.GdiplusStartupInput(), null);
        if (status != 0) {
            throw new RuntimeException("GDI+ initialization failed with status: " + status);
        }
    }

    private static void drawHints(WinDef.HDC hdc,
                                  WinDef.RECT windowRect, Screen screen,
                                  HintMeshWindow hintMeshWindow,
                                  List<Hint> windowHints) {
        WinDef.HDC hdcTemp = GDI32.INSTANCE.CreateCompatibleDC(hdc);

        WindowsFont.WindowsFontFamilyAndStyle
                fontFamilyAndStyle = WindowsFont.fontFamilyAndStyle(currentHintMesh.fontName());
        double fontSize = currentHintMesh.fontSize();
        double fontSpacingPercent = currentHintMesh.fontSpacingPercent();
        double fontOpacity = currentHintMesh.fontOpacity();
        double fontOutlineThickness = currentHintMesh.fontOutlineThickness();
        String fontOutlineHexColor = currentHintMesh.fontOutlineHexColor();
        double fontOutlineOpacity = currentHintMesh.fontOutlineOpacity();
        double highlightFontScale = currentHintMesh.highlightFontScale();
        String boxHexColor = currentHintMesh.boxHexColor();
        double boxOpacity = currentHintMesh.boxOpacity();
        double boxBorderThickness = currentHintMesh.boxBorderThickness();
        double boxBorderLength = currentHintMesh.boxBorderLength();
        String boxBorderHexColor = currentHintMesh.boxBorderHexColor();
        double boxBorderOpacity = currentHintMesh.boxBorderOpacity();
        boolean expandBoxes = currentHintMesh.expandBoxes();
        String fontHexColor = currentHintMesh.fontHexColor();
        String prefixFontHexColor = currentHintMesh.prefixFontHexColor();
        int subgridRowCount = currentHintMesh.subgridRowCount();
        int subgridColumnCount = currentHintMesh.subgridColumnCount();
        double subgridBorderThickness = currentHintMesh.subgridBorderThickness();
        double subgridBorderLength = currentHintMesh.subgridBorderLength();
        String subgridBorderHexColor = currentHintMesh.subgridBorderHexColor();
        double subgridBorderOpacity = currentHintMesh.subgridBorderOpacity();
        List<Key> focusedHintKeySequence = currentHintMesh.focusedKeySequence();
//        int scaledDpi = (int) (screen.dpi() * screen.scale());
        int dpi = screen.dpi();
        int windowWidth = windowRect.right - windowRect.left;
        int windowHeight = windowRect.bottom - windowRect.top;

        ZoomHintMesh zoomHintMesh = new ZoomHintMesh(currentHintMesh, currentZoom);
        WinDef.HBITMAP hbm = GDI32.INSTANCE.CreateCompatibleBitmap(hdc, windowWidth, windowHeight);
        GDI32.INSTANCE.SelectObject(hdcTemp, hbm);
        DibSection dibSection = createDibSection(windowWidth, windowHeight, hdcTemp);
        // Select the DIB section into the memory DC.
        WinNT.HANDLE oldDIBitmap = GDI32.INSTANCE.SelectObject(hdcTemp, dibSection.hDIBitmap);
        if (oldDIBitmap == null)
            throw new RuntimeException("Unable to select bitmap into source DC.");

        PointerByReference graphics = new PointerByReference();
        int graphicsStatus = Gdiplus.INSTANCE.GdipCreateFromHDC(hdcTemp, graphics);
        if (graphicsStatus != 0) {
            throw new RuntimeException("Failed to create Graphics object. Status: " + graphicsStatus);
        }

        int boxColor =
                hexColorStringToRgb(boxHexColor, 1d) | ((int) (boxOpacity * 255) << 24);
        int clearStatus = Gdiplus.INSTANCE.GdipGraphicsClear(graphics.getValue(), boxColor); // ARGB
        if (clearStatus != 0) {
            throw new RuntimeException("Failed to clear graphics with box color. Status: " + clearStatus);
        }

        // https://stackoverflow.com/questions/1203087/why-is-graphics-measurestring-returning-a-higher-than-expected-number
        // Does not look like it is useful when used with GdipAddPathString (it is useful with GdipDrawString)
        int textRenderingStatus = Gdiplus.INSTANCE.GdipSetTextRenderingHint(graphics.getValue(), 3); // 3 = AntiAliasGridFit, 4 = TextRenderingHintAntiAlias
        if (textRenderingStatus != 0) {
            throw new RuntimeException("Failed to set TextRenderingHint. Status: " + textRenderingStatus);
        }

        int smoothingModeStatus = Gdiplus.INSTANCE.GdipSetSmoothingMode(graphics.getValue(), 2); // 2= SmoothingModeAntiAlias
        if (smoothingModeStatus != 0) {
            throw new RuntimeException("Failed to set SmoothingMode. Status: " + smoothingModeStatus);
        }

        int interpolationModeStatus = Gdiplus.INSTANCE.GdipSetInterpolationMode(graphics.getValue(), 7); // InterpolationModeHighQualityBicubic
        if (interpolationModeStatus != 0) {
            throw new RuntimeException("Failed to set SmoothingMode. Status: " + interpolationModeStatus);
        }

        PointerByReference fontFamily = new PointerByReference();
        int fontFamilyStatus = Gdiplus.INSTANCE.GdipCreateFontFamilyFromName(
                new WString(fontFamilyAndStyle.fontFamily()), null, fontFamily);

        float normalGdipFontSize = (float) (fontSize * dpi * zoomPercent() / 72);
        float largeGdipFontSize = (float) (fontSize * highlightFontScale * dpi * zoomPercent() / 72);
//        float normalGdipFontSize = (float) (-fontHeight / zoomPercent());
//        float largeGdipFontSize = (float) (-largeFontHeight / zoomPercent());

        PointerByReference prefixPath = new PointerByReference();
        int createPrefixPathStatus = Gdiplus.INSTANCE.GdipCreatePath(0, prefixPath); // 0 = FillModeAlternate
        if (createPrefixPathStatus != 0) {
            throw new RuntimeException("Failed to create GraphicsPath. Status: " + createPrefixPathStatus);
        }
        PointerByReference suffixPath = new PointerByReference();
        int createSuffixPathStatus = Gdiplus.INSTANCE.GdipCreatePath(0, suffixPath);
        PointerByReference highlightPath = new PointerByReference();
        int createHighlightPathStatus = Gdiplus.INSTANCE.GdipCreatePath(0, highlightPath);

        double outlineThickness = fontOutlineThickness;
        int glowRadius = outlineThickness == 0 ? 0 : 1;
        float penWidthMultiplier = (float) outlineThickness;
        double outlineOpacity = fontOutlineOpacity;
        int outlineColorRgb = hexColorStringToRgb(fontOutlineHexColor, 1d);
        int penColor = (int) (outlineOpacity * 0xFF) << 24 | outlineColorRgb; //0x80000000; // ARGB
        List<PointerByReference> outlinePens = new ArrayList<>();
        for (int radius = 1; radius <= glowRadius; radius++) {
            PointerByReference pen = new PointerByReference();
            outlinePens.add(pen);
            int penStatus = Gdiplus.INSTANCE.GdipCreatePen1(penColor, radius * penWidthMultiplier, 2, pen); // 2 = UnitPixel
            if (penStatus != 0) {
                throw new RuntimeException("Failed to create Pen. Status: " + penStatus);
            }
            int setLineJoinStatus = Gdiplus.INSTANCE.GdipSetPenLineJoin(pen.getValue(), 2); // 2 = LineJoinRound
            if (setLineJoinStatus != 0) {
                throw new RuntimeException("Failed to set Pen LineJoin to Round. Status: " + setLineJoinStatus);
            }
        }

        PointerByReference prefixFontBrush = new PointerByReference();
        int prefixFontBrushColor = ((int) (fontOpacity * 255) << 24) | hexColorStringToRgb(prefixFontHexColor, 1d);
        int prefixFontBrushStatus = Gdiplus.INSTANCE.GdipCreateSolidFill(prefixFontBrushColor, prefixFontBrush);
        if (prefixFontBrushStatus != 0) {
            throw new RuntimeException("Failed to create Brush. Status: " + prefixFontBrushStatus);
        }
        PointerByReference suffixFontBrush = new PointerByReference();
        int suffixFontBrushColor = ((int) (fontOpacity * 255) << 24) | hexColorStringToRgb(fontHexColor, 1d);
        int suffixFontBrushStatus = Gdiplus.INSTANCE.GdipCreateSolidFill(suffixFontBrushColor, suffixFontBrush);
        PointerByReference highlightFontBrush = new PointerByReference();
        int highlightFontBrushColor = ((int) (fontOpacity * 255) << 24) | hexColorStringToRgb(fontHexColor, 1d);
        int highlightFontBrushStatus = Gdiplus.INSTANCE.GdipCreateSolidFill(highlightFontBrushColor, highlightFontBrush);

        PointerByReference stringFormat = stringFormat();

        PointerByReference normalFont = new PointerByReference();
        int fontStyle = 0; // Regular style
        int unit = 2; // UnitPixel
        int normalFontStatus = Gdiplus.INSTANCE.GdipCreateFont(fontFamily.getValue(), normalGdipFontSize, fontStyle, unit, normalFont);
        if (normalFontStatus != 0) {
            throw new RuntimeException("Failed to create Font. Status: " + normalFontStatus);
        }
        PointerByReference largeFont = new PointerByReference();
        int largeFontStatus = Gdiplus.INSTANCE.GdipCreateFont(fontFamily.getValue(), largeGdipFontSize, fontStyle, unit, largeFont);
        if (largeFontStatus != 0) {
            throw new RuntimeException("Failed to create Font. Status: " + largeFontStatus);
        }

        HintMeshDraw hintMeshDraw = hintMeshWindow.hintMeshDrawCache.get(
                zoomHintMesh);
        boolean hintMeshDrawIsCached = hintMeshDraw != null;
        if (hintMeshDrawIsCached) {
            logger.trace("hintMeshDraw is cached");
            dibSection.pixelPointer.write(0, hintMeshDraw.pixelData, 0, hintMeshDraw.pixelData.length);
        }
        else {
            // Without caching, a full screen of hints drawn with GDI+ takes some time
            // to compute, even when there is no outline.
            int[] pixelData = new int[windowWidth * windowHeight];
            hintMeshDraw = hintMeshDraw(screen, windowWidth, windowHeight, windowHints,
                    focusedHintKeySequence,
                    highlightFontScale, boxBorderThickness, expandBoxes,
                    graphics, normalFont, fontSize, fontSpacingPercent, largeFont, pixelData);
            if (hintMeshDraw.hintSequenceTexts.size() > 200) {
                // The pixelData is a full screen int[][]. We don't want to cache too many
                // of them.
                logger.trace("Caching new hintMeshDraw with " +
                             hintMeshDraw.hintSequenceTexts.size() + " visible hints");
                hintMeshWindow.hintMeshDrawCache.put(zoomHintMesh, hintMeshDraw);
            }

            // No cell if cellWidth/Height is not defined (e.g. non-grid hint mesh).
            int colorBetweenBoxes =
                            hexColorStringToRgb(boxBorderHexColor, boxBorderOpacity) |
                    ((int) (255 * boxBorderOpacity) << 24);
            int noColorColor = boxColor == 0 ? 1 : 0; // We need a placeholder color that is not used.
            clearWindow(hdcTemp, windowRect, noColorColor);
            dibSection.pixelPointer.read(0, hintMeshDraw.pixelData, 0, hintMeshDraw.pixelData.length);
            int scaledBoxBorderThickness = scaledPixels(boxBorderThickness, 1);
            int borderLengthPixels =
                    Math.max((int) Math.floor(boxBorderLength * 1), scaledBoxBorderThickness);
            int colorBetweenSubBoxes =
                    hexColorStringToRgb(subgridBorderHexColor, subgridBorderOpacity) |
                    ((int) (255 * subgridBorderOpacity) << 24);
            int scaledSubgridBorderThickness = (int) Math.floor(subgridBorderThickness);
            int subgridBorderLengthPixels =
                    Math.max((int) Math.floor(subgridBorderLength * 1), scaledSubgridBorderThickness);
            for (int cellIndex = 0; cellIndex < hintMeshDraw.cellRects().size(); cellIndex++) {
                // A box and its corresponding cell are handled within the same iteration.
                // If we handle all the boxes first, then all the cells, the problem is that
                // with small grids, the cell border will not be rendered because the color
                // will be boxColor instead of noColorColor.
                WinDef.RECT cellRect = hintMeshDraw.cellRects().get(cellIndex);
                WinDef.RECT boxRect = hintMeshDraw.boxRects().get(cellIndex);
                for (int x = Math.max(0, boxRect.left);
                     x < Math.min(windowWidth, boxRect.right); x++) {
                    for (int y = Math.max(0, boxRect.top);
                         y < Math.min(windowHeight, boxRect.bottom); y++) {
                        int pixel = hintMeshDraw.pixelData[y * windowWidth + x];
                        if (pixel == noColorColor)
                            hintMeshDraw.pixelData[y * windowWidth + x] = boxColor;
                    }
                }
                int minX = Math.max(0, cellRect.left);
                int maxX = Math.min(windowWidth, cellRect.right);
                int minY = Math.max(0, cellRect.top);
                int maxY = Math.min(windowHeight, cellRect.bottom);
                int subWidth = (int) Math.floor((double) (maxX - minX) / subgridColumnCount);
                int spareWidth = (maxX - minX) - subWidth * subgridColumnCount;
                boolean[] subgridColumnExtraPixelDistribution =
                        HintManager.distributeTrueUniformly(subgridColumnCount,
                                spareWidth);
                int subHeight = (int) Math.floor((double) (maxY - minY) / subgridRowCount);
                int spareHeight = (maxY - minY) - subHeight * subgridRowCount;
                boolean[] subgridRowExtraPixelDistribution =
                        HintManager.distributeTrueUniformly(subgridRowCount,
                                spareHeight);
                for (int x = minX; x < maxX; x++) {
                    for (int y = minY; y < maxY; y++) {
                        // The cell may go past the screen dimensions.
                        int pixel = hintMeshDraw.pixelData[y * windowWidth + x];
                        if (pixel == noColorColor) {
                            if ((x - minX < borderLengthPixels || maxX - 1 - x < borderLengthPixels)
                                && (y - minY < borderLengthPixels || maxY - 1 - y < borderLengthPixels)) {
                                hintMeshDraw.pixelData[y * windowWidth + x] = colorBetweenBoxes;
                            }
                            else
                                hintMeshDraw.pixelData[y * windowWidth + x] = boxColor;
                        }
                    }
                }
                int columnWidthOffset = subgridColumnExtraPixelDistribution[0] ? 1 : 0;
                int centerRowIndex = subgridRowCount / 2;
                int centerRowHeightOffset = 0;
                for (int i = 0; i < centerRowIndex; i++) {
                    centerRowHeightOffset += subgridRowExtraPixelDistribution[i] ? 1 : 0;
                }
                int centerColumnIndex = subgridColumnCount / 2;
                int centerColumnWidthOffset = 0;
                for (int i = 0; i < centerColumnIndex; i++) {
                    centerColumnWidthOffset += subgridColumnExtraPixelDistribution[i] ? 1 : 0;
                }
                int borderThicknessOrLength =
                        Math.max(scaledSubgridBorderThickness, subgridBorderLengthPixels);
                for (int subgridColumnIndex = 1;
                     subgridColumnIndex < subgridColumnCount; subgridColumnIndex++) {
                    int columnCenterX = minX + columnWidthOffset + subgridColumnIndex * subWidth;
                    columnWidthOffset += subgridColumnExtraPixelDistribution[subgridColumnIndex] ? 1 : 0;
                    int centerY = minY + centerRowHeightOffset + centerRowIndex * subHeight;
                    for (int y = Math.max(minY, centerY - borderThicknessOrLength / 2);
                         y <= Math.min(maxY - 1, centerY + borderThicknessOrLength / 2 - (scaledSubgridBorderThickness - 1) % 2); y++) {
                        for (int thicknessX = 0; thicknessX < scaledSubgridBorderThickness; thicknessX++) {
                            int x = columnCenterX - scaledSubgridBorderThickness / 2 + thicknessX;
                            if (hintMeshDraw.pixelData[y * windowWidth + x] == boxColor)
                                hintMeshDraw.pixelData[y * windowWidth + x] = colorBetweenSubBoxes;
                        }
                    }
                }
                int rowHeightOffset = subgridRowExtraPixelDistribution[0] ? 1 : 0;
                for (int subgridRowIndex = 1;
                     subgridRowIndex < subgridRowCount; subgridRowIndex++) {
                    int rowCenterY = minY + rowHeightOffset + subgridRowIndex * subHeight;
                    rowHeightOffset += subgridRowExtraPixelDistribution[subgridRowIndex] ? 1 : 0;
                    int centerX = minX + centerColumnWidthOffset + centerColumnIndex * subWidth;
                    for (int x = Math.max(minX, centerX - borderThicknessOrLength / 2);
                         x <= Math.min(maxX - 1, centerX + borderThicknessOrLength / 2 - (scaledSubgridBorderThickness - 1) % 2); x++) {
                        for (int thicknessY = 0; thicknessY < scaledSubgridBorderThickness; thicknessY++) {
                            int y = rowCenterY - scaledSubgridBorderThickness / 2 + thicknessY;
                            if (hintMeshDraw.pixelData[y * windowWidth + x] == boxColor)
                                hintMeshDraw.pixelData[y * windowWidth + x] = colorBetweenSubBoxes;
                        }
                    }
                }
            }
            dibSection.pixelPointer.write(0, hintMeshDraw.pixelData, 0, hintMeshDraw.pixelData.length);
            boolean mustDrawPrefix = false;
            boolean mustDrawSuffix = false;
            boolean mustDrawHighlight = false;
            for (HintSequenceText hintSequenceText : hintMeshDraw.hintSequenceTexts()) {
                for (HintKeyText keyText : hintSequenceText.keyTexts) {
                    PointerByReference path = keyText.isPrefix ? prefixPath :
                            (keyText.isHighlight ? highlightPath : suffixPath);
                    float gdipFontSize = keyText.isPrefix ? normalGdipFontSize :
                            (keyText.isHighlight ? largeGdipFontSize :
                                    normalGdipFontSize);
                    mustDrawPrefix |= keyText.isPrefix;
                    mustDrawHighlight |= keyText.isHighlight;
                    mustDrawSuffix |= keyText.isSuffix;
                    // Color is defined in the brush.
                    drawHintText(keyText.keyText, keyText.left, keyText.top, path,
                            fontFamilyAndStyle, fontFamily, gdipFontSize, stringFormat);
                }
            }
            if (mustDrawPrefix)
                drawAndFillPath(outlinePens, graphics, prefixPath, prefixFontBrush);
            if (mustDrawSuffix)
                drawAndFillPath(outlinePens, graphics, suffixPath, suffixFontBrush);
            if (mustDrawHighlight)
                drawAndFillPath(outlinePens, graphics, highlightPath, highlightFontBrush);
            dibSection.pixelPointer.read(0, hintMeshDraw.pixelData, 0, hintMeshDraw.pixelData.length);
        }

        updateLayeredWindow(windowRect, hintMeshWindow, hdcTemp);

        // Step 7: Cleanup
        Gdiplus.INSTANCE.GdipDeleteBrush(prefixFontBrush.getValue());
        Gdiplus.INSTANCE.GdipDeleteBrush(suffixFontBrush.getValue());
        Gdiplus.INSTANCE.GdipDeleteBrush(highlightFontBrush.getValue());
        Gdiplus.INSTANCE.GdipDeleteFont(normalFont.getValue());
        Gdiplus.INSTANCE.GdipDeleteFont(largeFont.getValue());
        for (PointerByReference pen : outlinePens)
            Gdiplus.INSTANCE.GdipDeletePen(pen.getValue());
        Gdiplus.INSTANCE.GdipDeleteStringFormat(stringFormat.getValue());
        Gdiplus.INSTANCE.GdipDeletePath(prefixPath.getValue());
        Gdiplus.INSTANCE.GdipDeletePath(suffixPath.getValue());
        Gdiplus.INSTANCE.GdipDeletePath(highlightPath.getValue());
        Gdiplus.INSTANCE.GdipDeleteFontFamily(fontFamily.getValue());
        Gdiplus.INSTANCE.GdipDeleteGraphics(graphics.getValue());

        GDI32.INSTANCE.SelectObject(hdcTemp, oldDIBitmap);
        GDI32.INSTANCE.DeleteObject(hbm);
        GDI32.INSTANCE.DeleteObject(dibSection.hDIBitmap);
        GDI32.INSTANCE.DeleteDC(hdcTemp);

    }

    private static PointerByReference stringFormat() {
        // Create StringFormat
        PointerByReference stringFormat = new PointerByReference();
        // GenericTypographic: https://stackoverflow.com/questions/65559919/gdi-graphics-measurestring-either-too-wide-or-too-narrow
        int StringFormatFlagsNoClip = 0x4000;
        int StringFormatFlagsLineLimit = 0x2000;
        int StringFormatFlagsNoFitBlackBox = 0x4;
        int flags = StringFormatFlagsNoClip | StringFormatFlagsLineLimit |
                    StringFormatFlagsNoFitBlackBox;
//        flags = 0;
        int stringFormatStatus = Gdiplus.INSTANCE.GdipCreateStringFormat(0, null, stringFormat);
        if (stringFormatStatus != 0) {
            throw new RuntimeException("Failed to create StringFormat. Status: " + stringFormatStatus);
        }

        Gdiplus.INSTANCE.GdipSetStringFormatFlags(stringFormat.getValue(), flags);
//        Gdiplus.INSTANCE.GdipSetStringFormatFlags(stringFormat.getValue(), 0);

        // Set Horizontal Alignment (DT_LEFT -> StringAlignmentNear)
        int setAlignStatus = Gdiplus.INSTANCE.GdipSetStringFormatAlign(stringFormat.getValue(), 0); // 0 = StringAlignmentNear
        if (setAlignStatus != 0) {
            throw new RuntimeException("Failed to set StringFormat alignment. Status: " + setAlignStatus);
        }

        // Set Vertical Alignment (DT_TOP -> StringAlignmentNear)
        int setLineAlignStatus = Gdiplus.INSTANCE.GdipSetStringFormatLineAlign(stringFormat.getValue(), 0); // 0 = StringAlignmentNear
        if (setLineAlignStatus != 0) {
            throw new RuntimeException("Failed to set StringFormat line alignment. Status: " + setLineAlignStatus);
        }
        return stringFormat;
    }

    private static void drawAndFillPath(List<PointerByReference> outlinePens,
                                  PointerByReference graphics, PointerByReference path,
                                  PointerByReference fontBrush) {
        for (PointerByReference pen : outlinePens) {
            // Draw Path
            int drawPathStatus = Gdiplus.INSTANCE.GdipDrawPath(graphics.getValue(), pen.getValue(), path.getValue());
            if (drawPathStatus != 0) {
                throw new RuntimeException("Failed to draw path. Status: " + drawPathStatus);
            }
        }
        int fillPathStatus = Gdiplus.INSTANCE.GdipFillPath(graphics.getValue(), fontBrush.getValue(), path.getValue());
        if (fillPathStatus != 0) {
            throw new RuntimeException("Failed to fill path. Status: " + fillPathStatus);
        }
    }

    private static void drawHintText(String text, double left, double top,
                                     PointerByReference path,
                                     WindowsFont.WindowsFontFamilyAndStyle fontFamilyAndStyle,
                                     PointerByReference fontFamily,
                                     float gdipFontSize,
                                     PointerByReference stringFormat) {
        Gdiplus.GdiplusRectF layoutRect = new Gdiplus.GdiplusRectF();
//        layoutRect.x = (float) left;
//        layoutRect.y = (float) top;
//        layoutRect.x = Math.round(left);
//        layoutRect.y = Math.round(top);
        layoutRect.x = (int) Math.ceil(left);
        layoutRect.y = (int) Math.ceil(top);
        layoutRect.width = Float.MAX_VALUE;
        layoutRect.height = Float.MAX_VALUE;
        // https://learn.microsoft.com/en-us/windows/win32/api/gdiplusenums/ne-gdiplusenums-fontstyle
        int fontStyle = switch (fontFamilyAndStyle.style()) {
            case REGULAR -> 0;
            case BOLD -> 1;
            case ITALIC -> 2;
            case BOLD_ITALIC -> 3;
        };
        int addPathStringStatus = Gdiplus.INSTANCE.GdipAddPathString(
                path.getValue(),
                new WString(text),
                -1, // Automatically calculate the length of the string.
                fontFamily.getValue(),
                fontStyle,
                gdipFontSize,
                layoutRect,
                stringFormat.getValue() // Use default string format.
        );
        if (addPathStringStatus != 0) {
            throw new RuntimeException("Failed to add path string to GraphicsPath. Status: " + addPathStringStatus);
        }
    }

    private static HintMeshDraw hintMeshDraw(Screen screen, int windowWidth,
                                             int windowHeight, List<Hint> windowHints,
                                             List<Key> focusedHintKeySequence,
                                             double highlightFontScale,
                                             double boxBorderThickness,
                                             boolean expandBoxes, PointerByReference graphics,
                                             PointerByReference normalFont,
                                             double fontSize, double fontSpacingPercent,
                                             PointerByReference largeFont,
                                             int[] pixelData) {
        List<WinDef.RECT> boxRects = new ArrayList<>();
        List<WinDef.RECT> cellRects = new ArrayList<>();
        List<HintSequenceText> hintSequenceTexts = new ArrayList<>();
        double minHintCenterX = Double.MAX_VALUE;
        double minHintCenterY = Double.MAX_VALUE;
        double maxHintCenterX = Double.MIN_VALUE;
        double maxHintCenterY = Double.MIN_VALUE;
        List<Hint> zoomedWindowHints = currentZoom == null ? windowHints : new ArrayList<>();

//        stringFormat = new PointerByReference();
//        Gdiplus.INSTANCE.GdipCreateStringFormat(0, null, stringFormat);
        PointerByReference stringFormat = stringFormat();
        PointerByReference region = new PointerByReference();
        Gdiplus.INSTANCE.GdipCreateRegion(region);

        Map<Key, Gdiplus.GdiplusRectF> normalFontBoundingBoxes = new HashMap<>();
        Map<Key, Gdiplus.GdiplusRectF> largeFontBoundingBoxes = new HashMap<>();
        double maxKeyBoundingBoxWidth = 0;
        double maxKeyBoundingBoxX = 0;
        boolean isFixedSizeWidthFont = true;
        Gdiplus.GdiplusRectF layoutRect = new Gdiplus.GdiplusRectF(0, 0, 1000, 1000);
        for (Hint hint : windowHints) {
            if (currentZoom != null) {
                hint = new Hint(zoomedX(hint.centerX()), zoomedY(hint.centerY()),
                        zoomPercent() * hint.cellWidth(),
                        zoomPercent() * hint.cellHeight(), hint.keySequence());
                if (hint.startsWith(focusedHintKeySequence))
                    zoomedWindowHints.add(hint);
            }
            if (hint.startsWith(focusedHintKeySequence)) {
                maxHintCenterX = Math.max(maxHintCenterX, hint.centerX());
                maxHintCenterY = Math.max(maxHintCenterY, hint.centerY());
                minHintCenterX = Math.min(minHintCenterX, hint.centerX());
                minHintCenterY = Math.min(minHintCenterY, hint.centerY());
            }
            for (int keyIndex = 0; keyIndex < hint.keySequence().size(); keyIndex++) {
                Key key = hint.keySequence().get(keyIndex);
                boolean isPrefix = keyIndex < focusedHintKeySequence.size();
                boolean isHighlight =
                        highlightFontScale != 1 && hint.keySequence().size() != 1 &&
                        keyIndex == focusedHintKeySequence.size();
                // Compute all bounding boxes.
                Gdiplus.GdiplusRectF boundingBox =
                        hintKeyBoundingBox(graphics,
                                isHighlight ? largeFont : normalFont,
                                key,
                                isHighlight ? largeFontBoundingBoxes :
                                        normalFontBoundingBoxes,
                                layoutRect, stringFormat, region
                        );
                if (maxKeyBoundingBoxWidth != 0 &&
                    maxKeyBoundingBoxWidth != boundingBox.width
                    || maxKeyBoundingBoxX != 0 && maxKeyBoundingBoxX != boundingBox.x)
                    isFixedSizeWidthFont = false;
                if (boundingBox.width + boundingBox.x >
                    maxKeyBoundingBoxWidth + maxKeyBoundingBoxX) {
                    maxKeyBoundingBoxWidth = boundingBox.width;
                    maxKeyBoundingBoxX = boundingBox.x;
                }
            }
        }
        double maxSimpleBoxWidth = 0;
        double highestKeyHeight = 0;
        for (Hint hint : zoomedWindowHints) {
            double smallestColAlignedFontBoxWidth;
            smallestColAlignedFontBoxWidth = (maxKeyBoundingBoxWidth) * hint.keySequence().size()
//                + 2*maxKeyBoundingBoxX; // 1 for first 1 for last
                                             + maxKeyBoundingBoxX * hint.keySequence().size();
            // This matches the hintKeyTextTotalXAdvance calculation:
            if (hint.keySequence().size() == 1)
                smallestColAlignedFontBoxWidth += maxKeyBoundingBoxX;
//            else
//                smallestColAlignedFontBoxWidth += 2*maxKeyBoundingBoxX; // 1 for first 1 for last
            double smallestColAlignedFontBoxWidthPercent = (hint.cellWidth() == -1 ? 0 :
                    smallestColAlignedFontBoxWidth /
                    hint.cellWidth());
            if (smallestColAlignedFontBoxWidthPercent > 1)
                // screen-selection mode: cell size is not defined in the config and is too small.
                smallestColAlignedFontBoxWidthPercent = 1;
            // We want font spacing percent 0.5 be the min spacing that keeps column alignment.
            double adjustedFontBoxWidthPercent = fontSpacingPercent < 0.5d ?
                    (fontSpacingPercent * 2) * smallestColAlignedFontBoxWidthPercent
                    : smallestColAlignedFontBoxWidthPercent + (fontSpacingPercent - 0.5d) * 2 * (1 - smallestColAlignedFontBoxWidthPercent) ;
            if (hint.cellWidth() == -1)
                adjustedFontBoxWidthPercent = 1;
            boolean doNotColAlign = hint.cellWidth() == -1 || hint.keySequence().size() == 1 ||
                    adjustedFontBoxWidthPercent < smallestColAlignedFontBoxWidthPercent;
            List<HintKeyText> keyTexts = new ArrayList<>();
            double xAdvance = 0;
            double lastKeyBoundingBoxX = 0;
            double lastKeyWidth = 0;
            double hintKeyTextTotalXAdvance = 0;
            for (int keyIndex = 0; keyIndex < hint.keySequence().size(); keyIndex++) {
                Key key = hint.keySequence().get(keyIndex);
                boolean isHighlight =
                        highlightFontScale != 1 && hint.keySequence().size() != 1 &&
                        keyIndex == focusedHintKeySequence.size();
                Gdiplus.GdiplusRectF boundingBox =
                        isHighlight ? largeFontBoundingBoxes.get(key) :
                                normalFontBoundingBoxes.get(key);
                hintKeyTextTotalXAdvance += boundingBox.width;
                if (keyIndex == 0)
                    hintKeyTextTotalXAdvance += boundingBox.x;
                // Added twice if key is both first and last (1-character).
                if (keyIndex == hint.keySequence().size() - 1)
                    hintKeyTextTotalXAdvance += boundingBox.x;
                if (boundingBox.height > highestKeyHeight) {
                    highestKeyHeight = boundingBox.height;
                }
                if (keyIndex == hint.keySequence().size() - 1) {
                    lastKeyBoundingBoxX = boundingBox.x;
                    lastKeyWidth = boundingBox.width + boundingBox.x;
                }
            }
            // If adjustedFontBoxWidthPercent is too small, then we don't try to align characters
            // in columns anymore and we just place them next to each other (percent = 0
            // to smallestColAlignedFontBoxWidthPercent), centered in the box.
            double extraNotAlignedWidth = smallestColAlignedFontBoxWidth - hintKeyTextTotalXAdvance;
            extraNotAlignedWidth = adjustedFontBoxWidthPercent * extraNotAlignedWidth;
            for (int keyIndex = 0; keyIndex < hint.keySequence().size(); keyIndex++) {
                Key key = hint.keySequence().get(keyIndex);
                boolean isPrefix = keyIndex < focusedHintKeySequence.size();
                boolean isHighlight =
                        highlightFontScale != 1 && hint.keySequence().size() != 1 &&
                        keyIndex == focusedHintKeySequence.size();
                boolean isSuffix = !isPrefix && !isHighlight;
                Gdiplus.GdiplusRectF boundingBox =
                        isHighlight ? largeFontBoundingBoxes.get(key) :
                                normalFontBoundingBoxes.get(key);
                String keyText = key.hintLabel();
                double left;
                if (doNotColAlign) {
                    // Extra is added between each letter (not to the left of the leftmost letter,
                    // nor to the right of the rightmost letter).
                    left = hint.centerX() - screen.rectangle().x() - (hintKeyTextTotalXAdvance + extraNotAlignedWidth) / 2
                           + xAdvance
                           ;
                    xAdvance += boundingBox.width;// + boundingBox.x;
                    if (keyIndex != hint.keySequence().size() - 1)
                        xAdvance += extraNotAlignedWidth / (hint.keySequence().size() - 1);
                }
                else {
                    // 0.8d adjustedFontBoxWidthPercent means characters spread over 80% of the cell width.
                    // If we are here, hint.keySequence().size() is 2 or more (else, doNotColAlign would be true).
                    double fontBoxWidth = hint.cellWidth() * adjustedFontBoxWidthPercent
                                          - (hint.keySequence().size()-2) * boundingBox.x; // Similar to hintKeyTextTotalXAdvance
                    double fontBoxWidth2 = hint.cellWidth() * adjustedFontBoxWidthPercent;
                    double keySubcellWidth = fontBoxWidth2 / hint.keySequence().size();
                    left = hint.centerX() - screen.rectangle().x() - fontBoxWidth / 2
                           + xAdvance
                           + keySubcellWidth / 2
                           - (boundingBox.width) / 2 - boundingBox.x / 2;
                    xAdvance += keySubcellWidth - boundingBox.x;
                }
                double top = hint.centerY() - screen.rectangle().y() -
                             boundingBox.height / 2;
                keyTexts.add(new HintKeyText(keyText, left, top, isPrefix, isSuffix,
                        isHighlight));
            }
            double simpleBoxLeft = keyTexts.getFirst().left;
            double simpleBoxRight = keyTexts.getLast().left + lastKeyWidth;// + lastKeyBoundingBoxX;
            double simpleBoxWidth = simpleBoxRight - simpleBoxLeft;
            // If we don't want to expand the box widths, then we will use a common width
            // which will be the largest of the widths.
            if (simpleBoxWidth > maxSimpleBoxWidth) {
                maxSimpleBoxWidth = simpleBoxWidth;
            }
            if (hint.startsWith(focusedHintKeySequence))
                hintSequenceTexts.add(new HintSequenceText(hint, keyTexts));
        }
        // Hint boxes (assuming non-expanding) are centered on the hint, and of same width.
        for (HintSequenceText hintSequenceText : hintSequenceTexts) {
            Hint hint = hintSequenceText.hint;
            WinDef.RECT boxRect = new WinDef.RECT();
            int scaledBoxBorderThickness = scaledPixels(boxBorderThickness, 1);
            if (boxBorderThickness > 0 && scaledBoxBorderThickness == 0)
                scaledBoxBorderThickness = 1;
            double simpleBoxLeft =
                    hint.centerX() - screen.rectangle().x() - maxSimpleBoxWidth / 2;

            double simpleBoxTop =
                    hint.centerY() - screen.rectangle().y() - highestKeyHeight / 2;
            boxRect.left = (int) (simpleBoxLeft + scaledBoxBorderThickness);
            boxRect.top = (int) (simpleBoxTop + scaledBoxBorderThickness);

            double simpleBoxRight = hint.centerX() - screen.rectangle().x() + maxSimpleBoxWidth / 2;
            boxRect.right = (int) (simpleBoxRight - (hint.centerX() < maxHintCenterX ? 0 : scaledBoxBorderThickness));
            double simpleBoxBottom = simpleBoxTop + highestKeyHeight;
            boxRect.bottom = (int) (simpleBoxBottom - (hint.centerY() < maxHintCenterY ? 0 : scaledBoxBorderThickness));
            boolean isHintPartOfGrid = hint.cellWidth() != -1;
            WinDef.RECT cellRect = new WinDef.RECT();
            if (!expandBoxes || !isHintPartOfGrid) {
                boxRect.left = (int) Math.round(simpleBoxLeft);
                boxRect.top = (int) Math.round(simpleBoxTop);
                boxRect.right = (int) Math.round(simpleBoxRight);
                boxRect.bottom = (int) Math.round(simpleBoxBottom);
            }
            else {
                setBoxOrCellRect(boxRect, screen, scaledBoxBorderThickness, hint);
            }
            cellRect.left = boxRect.left - scaledBoxBorderThickness;
            cellRect.top = boxRect.top - scaledBoxBorderThickness;
            cellRect.right = boxRect.right + scaledBoxBorderThickness;
            cellRect.bottom = boxRect.bottom + scaledBoxBorderThickness;
            cellRects.add(cellRect); // TODO only if between box is non transparent
            boxRects.add(boxRect);
        }
        Gdiplus.INSTANCE.GdipDeleteStringFormat(stringFormat.getValue());
        Gdiplus.INSTANCE.GdipDeleteRegion(region.getValue());
        return new HintMeshDraw(boxRects, cellRects, hintSequenceTexts, pixelData);
    }

    private static Gdiplus.GdiplusRectF hintKeyBoundingBox(PointerByReference graphics,
                                                     PointerByReference normalFont,
                                                     Key key,
                                                     Map<Key, Gdiplus.GdiplusRectF> normalFontBoundingBoxes,
                                                     Gdiplus.GdiplusRectF layoutRect,
                                                     PointerByReference stringFormat,
                                                     PointerByReference region) {
        return normalFontBoundingBoxes.computeIfAbsent(key,
                key1 -> {
                    Gdiplus.GdiplusRectF boundingBox = new Gdiplus.GdiplusRectF();
                    measureString(key.hintLabel(), normalFont, graphics,
                            layoutRect, boundingBox,
                            stringFormat, region);
                    return boundingBox;
                });
    }

    private static void measureString(String text, PointerByReference font,
                                      PointerByReference graphics,
                                      Gdiplus.GdiplusRectF layoutRect,
                                      Gdiplus.GdiplusRectF boundingBox,
                                      PointerByReference stringFormat,
                                      PointerByReference region) {
//        int measureStatus = Gdiplus.INSTANCE.GdipMeasureString(
//                graphics.getValue(),
//                new WString(text),
//                text.length(),
//                font.getValue(),
//                layoutRect, // Layout rectangle
//                stringFormat.getValue(),
//                boundingBox, // Output bounding rectangle
//                null,
//                null
//        );
//        if (measureStatus != 0) {
//            throw new RuntimeException("Failed to measure string. Status: " + measureStatus);
//        }
        float GdipMeasureStringWidth = boundingBox.width;
        String measuredText = text;
//        String measuredText = "L";
        Gdiplus.CharacterRange range1 = new Gdiplus.CharacterRange(0, measuredText.length());
        Gdiplus.INSTANCE.GdipSetStringFormatMeasurableCharacterRanges(
                stringFormat.getValue(), 1, new Gdiplus.CharacterRange[]{range1});
        Gdiplus.INSTANCE.GdipMeasureCharacterRanges(
                graphics.getValue(),
                new WString(measuredText), measuredText.length(), font.getValue(),
                layoutRect,
                stringFormat.getValue(),
                1,
                new Pointer[]{region.getValue()}
        );
        Gdiplus.INSTANCE.GdipGetRegionBounds(region.getValue(), graphics.getValue(), boundingBox);
//        boundingBox.width *= text.length();
//        boundingBox.width += boundingBox.x;
//        System.out.println(text + ": GdipSetStringFormatMeasurableCharacterRanges width = " + boundingBox.width + ", GdipMeasureString width = " + GdipMeasureStringWidth);
//        boundingBox.x = 0;
//        boundingBox.width -= 3*16/6f;
//        ExtendedGDI32.ABC[] abcWidths = (ExtendedGDI32.ABC[]) new ExtendedGDI32.ABC().toArray(1);
    }

    private static void setBoxOrCellRect(WinDef.RECT boxRect, Screen screen, double scaledBoxBorderThickness,
                                         Hint hint) {
        double cellWidth = hint.cellWidth();
        double halfCellWidth = cellWidth / 2;
        double cellHeight = hint.cellHeight();
        double halfCellHeight = cellHeight / 2;

        halfCellWidth -= scaledBoxBorderThickness / 2;
        halfCellHeight -= scaledBoxBorderThickness / 2;

        double boxLeft = hint.centerX() - halfCellWidth - screen.rectangle().x();
        double boxTop = hint.centerY() - halfCellHeight - screen.rectangle().y();
        double boxRight = hint.centerX() + halfCellWidth - screen.rectangle().x();
        double boxBottom = hint.centerY() + halfCellHeight - screen.rectangle().y();
        int roundedBoxLeft = (int) Math.ceil(boxLeft);
        int roundedBoxTop = (int) Math.ceil(boxTop);
        int roundedBoxRight = (int) Math.ceil(boxRight);
        int roundedBoxBottom = (int) Math.ceil(boxBottom);

        if (roundedBoxLeft == 0)
            boxLeft += scaledBoxBorderThickness / 2;
        if (roundedBoxTop == 0)
            boxTop += scaledBoxBorderThickness / 2;
        if (roundedBoxRight == screen.rectangle().width())
            boxRight -= scaledBoxBorderThickness / 2;
        if (roundedBoxBottom == screen.rectangle().height())
            boxBottom -= scaledBoxBorderThickness / 2;

        roundedBoxLeft = (int) Math.ceil(boxLeft);
        roundedBoxTop = (int) Math.ceil(boxTop);
        roundedBoxRight = (int) Math.ceil(boxRight);
        roundedBoxBottom = (int) Math.ceil(boxBottom);
        if (roundedBoxLeft < boxRect.left)
            boxRect.left = roundedBoxLeft;
        if (roundedBoxTop < boxRect.top)
            boxRect.top = roundedBoxTop;
        if (roundedBoxRight > boxRect.right)
            boxRect.right = roundedBoxRight;
        if (roundedBoxBottom > boxRect.bottom)
            boxRect.bottom = roundedBoxBottom;
    }

    private static Gdiplus.GdiplusRectF textRect(float textX, float textY, Gdiplus.GdiplusRectF textSize) {
        return new Gdiplus.GdiplusRectF(textX, textY, textSize.width, textSize.height);
    }

    private static int hexColorStringToInt(String hexColor) {
        if (hexColor.startsWith("#"))
            hexColor = hexColor.substring(1);
        int colorInt = Integer.parseUnsignedInt(hexColor, 16);
        // In COLORREF, the order is 0x00BBGGRR, so we need to reorder the components.
        int red = (colorInt >> 16) & 0xFF;
        int green = (colorInt >> 8) & 0xFF;
        int blue = colorInt & 0xFF;
        return (blue << 16) | (green << 8) | red;
    }

    private static int hexColorStringToRgb(String hexColor, double opacity) {
        // https://learn.microsoft.com/en-us/windows/win32/api/wingdi/ns-wingdi-blendfunction
        // Note that the APIs use premultiplied alpha, which means that the red, green
        // and blue channel values in the bitmap must be premultiplied with the alpha channel value.
        if (hexColor.startsWith("#"))
            hexColor = hexColor.substring(1);
        int colorInt = Integer.parseUnsignedInt(hexColor, 16);
        // In COLORREF, the order is 0x00BBGGRR, so we need to reorder the components.
        int red = (int) (((colorInt >> 16) & 0xFF) * opacity);
        int green = (int) (((colorInt >> 8) & 0xFF) * opacity);
        int blue = (int) ((colorInt & 0xFF) * opacity);
        return (red << 16) | (green << 8) | blue;
    }

    private static int alphaMultipliedChannelsColor(int color, double opacity) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        return ((int) Math.round(red * opacity) << 16) | ((int) Math.round(green * opacity) << 8) |
               (int) Math.round(blue * opacity);
    }

    public static void setIndicator(Indicator indicator) {
        Objects.requireNonNull(indicator);
        if (showingIndicator && currentIndicator != null &&
            currentIndicator.equals(indicator))
            return;
        Indicator oldIndicator = currentIndicator;
        currentIndicator = indicator;
        if (indicatorWindow == null) {
            createIndicatorWindow();
        }
        else if (indicator.size() != oldIndicator.size()) {
            User32.INSTANCE.SetWindowPos(indicatorWindow.hwnd(), null, bestIndicatorX(),
                    bestIndicatorY(),
                    indicatorSize() + 1,
                    indicatorSize() + 1,
                    User32.SWP_NOZORDER);
        }
        showingIndicator = true;
        requestWindowRepaint(indicatorWindow.hwnd);
    }

    public static void setZoom(Zoom zoom) {
        if (currentZoom != null && currentZoom.equals(zoom))
            return;
        if (zoomWindow == null) {
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
            Screen screen = WindowsScreen.findActiveScreen(new WinDef.POINT(
                    (int) zoom.center().x(),
                    (int) zoom.center().y()));
            currentZoomScreen = screen;
            if (oldZoom == null || oldZoom.percent() != zoom.percent()) {
                if (standByZoomWindow == null) {
                    standByZoomWindow = zoomWindow;
                    createZoomWindow();
                }
                else {
                    ZoomWindow newStandByZoomWindow = zoomWindow;
                    zoomWindow = standByZoomWindow;
                    standByZoomWindow = newStandByZoomWindow;
                }
                // MagSetWindowTransform() can take 10-20ms.
                if (!Magnification.INSTANCE.MagSetWindowTransform(zoomWindow.hwnd(),
                        new Magnification.MAGTRANSFORM.ByReference(
                                (float) zoomPercent())))
                    logger.error("Failed MagSetWindowTransform: " +
                                 Integer.toHexString(Native.getLastError()));
            }
            User32.INSTANCE.SetWindowPos(zoomWindow.hostHwnd(), null,
                    screen.rectangle().x(), screen.rectangle().y(),
                    screen.rectangle().width(), screen.rectangle().height(),
                    User32.SWP_NOZORDER);
            User32.INSTANCE.SetWindowPos(zoomWindow.hwnd(), null,
                    screen.rectangle().x(), screen.rectangle().y(),
                    screen.rectangle().width(), screen.rectangle().height(),
                    User32.SWP_NOZORDER);
        }
        if (indicatorWindow != null) {
            User32.INSTANCE.SetWindowPos(indicatorWindow.hwnd(), null, bestIndicatorX(),
                    bestIndicatorY(),
                    indicatorSize() + 1,
                    indicatorSize() + 1,
                    User32.SWP_NOZORDER);
            if (showingIndicator) {
                User32.INSTANCE.InvalidateRect(indicatorWindow.hwnd, null, true);
            }
        }
        if (showingHintMesh) {
            for (HintMeshWindow hintMeshWindow : hintMeshWindows.values()) {
                User32.INSTANCE.InvalidateRect(hintMeshWindow.hwnd, null, true);
            }
        }
        updateZoomWindow();
    }

    private static void updateZoomExcludedWindows() {
        if (zoomWindow == null)
            return;
        List<WinDef.HWND> hwnds = new ArrayList<>();
        if (gridWindow != null)
            hwnds.add(gridWindow.hwnd);
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values()) {
            hwnds.add(hintMeshWindow.hwnd);
            hwnds.add(hintMeshWindow.transparentHwnd);
        }
        if (indicatorWindow != null)
            hwnds.add(indicatorWindow.hwnd);
        if (standByZoomWindow != null)
            hwnds.add(standByZoomWindow.hwnd);
        if (hwnds.isEmpty())
            return;
        if (!Magnification.INSTANCE.MagSetWindowFilterList(zoomWindow.hwnd(),
                Magnification.MW_FILTERMODE_EXCLUDE, hwnds.size(),
                hwnds.toArray(new WinDef.HWND[0])))
            logger.error("Failed to set the zoom excluded window list: " +
                         Integer.toHexString(Native.getLastError()));
    }

    private static WinDef.LRESULT zoomWindowCallback(WinDef.HWND hwnd, int uMsg,
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

    public static void hideIndicator() {
        if (!showingIndicator)
            return;
        showingIndicator = false;
        requestWindowRepaint(indicatorWindow.hwnd);
    }

    public static void setGrid(Grid grid) {
        Objects.requireNonNull(grid);
        if (showingGrid && currentGrid != null && currentGrid.equals(grid))
            return;
        Grid oldGrid = currentGrid;
        currentGrid = grid;
        // +1 width and height because no line can be drawn on y = windowHeight and y = windowWidth.
        if (gridWindow == null)
            createGridWindow(currentGrid.x(), currentGrid.y(), currentGrid.width(),
                    currentGrid.height());
        else {
            if (grid.x() != oldGrid.x() || grid.y() != oldGrid.y() ||
                grid.width() != oldGrid.width() || grid.height() != oldGrid.height()) {
                // When going from a window grid to a screen grid, we don't want to:
                // 1. Resize. 2. Draw old grid in resized window. 3. Draw new grid. Instead, we want to:
                // 1. Clear old grid. 2. Resize. 3. Draw new grid.
                // However, clearing then resizing introduces a "blank" frame,
                // that is why we use 2 grid windows.
                if (standByGridWindow == null) {
                    standByGridWindow = gridWindow;
                    createGridWindow(currentGrid.x(), currentGrid.y(), currentGrid.width(),
                            currentGrid.height());
                    standByGridCanBeHidden = false;
                }
                else {
                    GridWindow newStandByGridWindow = gridWindow;
                    gridWindow = standByGridWindow;
                    standByGridWindow = newStandByGridWindow;
                    User32.INSTANCE.SetWindowPos(gridWindow.hwnd(), null, grid.x(), grid.y(),
                            grid.width(), grid.height(), User32.SWP_NOZORDER);
                    standByGridCanBeHidden = false;
                }
            }
        }
        showingGrid = true;
        requestWindowRepaint(gridWindow.hwnd);
    }

    public static void setHintMesh(HintMesh hintMesh) {
        Objects.requireNonNull(hintMesh);
        if (!hintMesh.visible()) {
            hideHintMesh();
            return;
        }
        if (showingHintMesh && currentHintMesh != null && currentHintMesh.equals(hintMesh))
            return;
        currentHintMesh = hintMesh;
        createOrUpdateHintMeshWindows(currentHintMesh.hints());
        showingHintMesh = true;
        if (!waitForZoomBeforeRepainting) {
            for (HintMeshWindow hintMeshWindow : hintMeshWindows.values())
                requestWindowRepaint(hintMeshWindow.hwnd);
        }
    }

    public static void hideGrid() {
        if (!showingGrid)
            return;
        showingGrid = false;
        requestWindowRepaint(gridWindow.hwnd);
    }

    public static void hideHintMesh() {
        if (!showingHintMesh)
            return;
        showingHintMesh = false;
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values())
            requestWindowRepaint(hintMeshWindow.hwnd);
    }

    private static void requestWindowRepaint(WinDef.HWND hwnd) {
        User32.INSTANCE.InvalidateRect(hwnd, null, true);
        User32.INSTANCE.UpdateWindow(hwnd);
    }

    static void mouseMoved(WinDef.POINT mousePosition) {
        if (indicatorWindow == null)
            return;
        User32.INSTANCE.MoveWindow(indicatorWindow.hwnd,
                bestIndicatorX(),
                bestIndicatorY(),
                indicatorSize() + 1,
                indicatorSize() + 1, false);
    }

}