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
import java.util.stream.Collectors;

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
    private static final Map<HintMesh, HintMeshDraw> hintMeshDrawCache = new HashMap<>();
    private static ZoomWindow zoomWindow, standByZoomWindow;
    private static Zoom currentZoom;
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

    private record HintMeshWindow(WinDef.HWND hwnd,
                                  WinDef.HWND transparentHwnd,
                                  WinUser.WindowProc callback,
                                  List<Hint> hints) {

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
        return currentZoom.zoomedX(x);
    }

    private static double zoomedY(double y) {
        if (currentZoom == null)
            return y;
        return currentZoom.zoomedY(y);
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
                    int left = (int) Math.ceil(hint.centerX() - hint.cellWidth() / 2);
                    int right = (int) Math.floor(hint.centerX() + hint.cellWidth() / 2);
                    int top = (int) Math.ceil(hint.centerY() - hint.cellHeight() / 2);
                    int bottom = (int) Math.floor(hint.centerY() + hint.cellHeight() / 2);
                    if (left == screen.rectangle().x() + screen.rectangle().width() ||
                        right == screen.rectangle().x() ||
                        top == screen.rectangle().y() + screen.rectangle().height() ||
                        bottom == screen.rectangle().y())
                        // Assuming two screens: left and right, with right screen
                        // at x = 1024. Hint's left is 1024.
                        // Hint should be on second screen, not on left screen.
                        continue;
                    if (!screen.rectangle().contains(left, top) &&
                        !screen.rectangle().contains(right, top) &&
                        !screen.rectangle().contains(left, bottom) &&
                        !screen.rectangle().contains(right, bottom))
                        continue;
                }
                hintsByScreen.computeIfAbsent(screen, screen1 -> new ArrayList<>())
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
                int windowIndex = hintMeshWindows.size() + 1;
                WinDef.HWND hwnd = createWindow("HintMesh" + windowIndex, screen.rectangle().x(),
                        screen.rectangle().y(), screen.rectangle().width(),
                        screen.rectangle().height(), callback);
                WinDef.HWND transparentHwnd = User32.INSTANCE.CreateWindowEx(
                        User32.WS_EX_TOPMOST | ExtendedUser32.WS_EX_TOOLWINDOW |
                        ExtendedUser32.WS_EX_NOACTIVATE
                        | ExtendedUser32.WS_EX_LAYERED
                        | ExtendedUser32.WS_EX_TRANSPARENT,
                        "Mousemaster" + "HintMesh" + windowIndex + "ClassName",
                        "Mousemaster" + "TransparentHintMesh" + windowIndex + "WindowName",
                        WinUser.WS_POPUP, screen.rectangle().x(),
                        screen.rectangle().y(), screen.rectangle().width(),
                        screen.rectangle().height(),
                        null, null,
                        null, null);
                User32.INSTANCE.ShowWindow(transparentHwnd, WinUser.SW_SHOW);
                hintMeshWindows.put(screen,
                        new HintMeshWindow(hwnd, transparentHwnd, callback,
                                hintsInScreen));
                createdAtLeastOneWindow = true;
            }
            else {
                hintMeshWindows.put(screen,
                        new HintMeshWindow(existingWindow.hwnd,
                                existingWindow.transparentHwnd,
                                existingWindow.callback,
                                hintsInScreen));
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
        int windowWidth = windowRect.right - windowRect.left;
        int windowHeight = windowRect.bottom - windowRect.top;
        WinDef.HDC hdcTemp = GDI32.INSTANCE.CreateCompatibleDC(hdc);
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

        double fontSize = currentHintMesh.fontSize();
        int dpi = screen.dpi();
        float normalGdipFontSize = (float) (fontSize * dpi / 72);
        WindowsFont.WindowsFontFamilyAndStyle
                fontFamilyAndStyle = WindowsFont.fontFamilyAndStyle(currentHintMesh.fontName());
        double highlightFontScale = currentHintMesh.highlightFontScale();
        float largeGdipFontSize = (float) (highlightFontScale * normalGdipFontSize);

        PointerByReference fontFamily = new PointerByReference();
        int fontFamilyStatus = Gdiplus.INSTANCE.GdipCreateFontFamilyFromName(
                new WString(fontFamilyAndStyle.fontFamily()), null, fontFamily);

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

        List<Key> focusedHintKeySequence = currentHintMesh.focusedKeySequence();
        double boxBorderThickness = currentHintMesh.boxBorderThickness();
        boolean isHintGrid = windowHints.getFirst().cellWidth() != -1;
        HintBoundingBoxes hintBoundingBoxes =
                hintCentersAndBoundingBoxes(windowHints, focusedHintKeySequence,
                        highlightFontScale,
                        graphics,
                        normalFont, largeFont); // TODO cache?

        NormalizedHints normalizedHints =
                normalizedHints(screen, windowHints, focusedHintKeySequence,
                        highlightFontScale,
                        boxBorderThickness, isHintGrid, hintBoundingBoxes);
        HintMesh normalizedHintMesh = new HintMesh.HintMeshBuilder(currentHintMesh)
                .type(null) // last-selected-hint or screen-center can lead to same drawings.
                .hints(normalizedHints.hints())
                .fontSize(normalGdipFontSize) // DPI-dependent
                .build();

        double fontSpacingPercent = currentHintMesh.fontSpacingPercent();
        double fontOpacity = currentHintMesh.fontOpacity();
        double fontOutlineThickness = currentHintMesh.fontOutlineThickness();
        String fontOutlineHexColor = currentHintMesh.fontOutlineHexColor();
        double fontOutlineOpacity = currentHintMesh.fontOutlineOpacity();
        double fontShadowThickness = currentHintMesh.fontShadowThickness();
        double fontShadowStep = currentHintMesh.fontShadowStep();
        String fontShadowHexColor = currentHintMesh.fontShadowHexColor();
        double fontShadowOpacity = currentHintMesh.fontShadowOpacity();
        double fontShadowHorizontalOffset = currentHintMesh.fontShadowHorizontalOffset();
        double fontShadowVerticalOffset = currentHintMesh.fontShadowVerticalOffset();
        String boxHexColor = currentHintMesh.boxHexColor();
        double boxOpacity = currentHintMesh.boxOpacity();
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

        int boxColor =
                hexColorStringToRgb(boxHexColor, 1d) | ((int) (boxOpacity * 255) << 24);
        int clearStatus = Gdiplus.INSTANCE.GdipGraphicsClear(graphics.getValue(), boxColor); // ARGB
        if (clearStatus != 0) {
            throw new RuntimeException("Failed to clear graphics with box color. Status: " + clearStatus);
        }

//        List<Hint> roundedHints = new ArrayList<>(normalizedHintMesh.hints().size());
//        for (Hint hint : normalizedHintMesh.hints()) {
//            roundedHints.add(new Hint(Math.round(hint.centerX()), Math.round(hint.centerY()), Math.round(hint.cellWidth()),
//                    Math.round(hint.cellHeight()), hint.keySequence()));
//        }
//        HintMesh roundedNormalizedHintMesh = normalizedHintMesh.builder().hints(roundedHints).build();
        HintMesh roundedNormalizedHintMesh = normalizedHintMesh;
        HintMeshDraw hintMeshDraw = hintMeshDrawCache.get(
                roundedNormalizedHintMesh);
        boolean hintMeshDrawIsCached = hintMeshDraw != null;
        if (hintMeshDrawIsCached) {
            logger.trace("hintMeshDraw is cached");
            int[] pixelData;
            if (normalizedHints.offsetX != 0 || normalizedHints.offsetY != 0) {
                pixelData = offsetPixelData(hintMeshDraw, windowWidth, windowHeight,
                        normalizedHints.offsetX, normalizedHints.offsetY);
            }
            else
                pixelData = hintMeshDraw.pixelData;
            dibSection.pixelPointer.write(0, pixelData, 0, pixelData.length);
            updateLayeredWindow(windowRect, hintMeshWindow, hdcTemp);
        }
        else {
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

            PointerByReference prefixPath = new PointerByReference();
            int createPrefixPathStatus = Gdiplus.INSTANCE.GdipCreatePath(0, prefixPath); // 0 = FillModeAlternate
            if (createPrefixPathStatus != 0) {
                throw new RuntimeException("Failed to create GraphicsPath. Status: " + createPrefixPathStatus);
            }
            PointerByReference suffixPath = new PointerByReference();
            int createSuffixPathStatus = Gdiplus.INSTANCE.GdipCreatePath(0, suffixPath);
            PointerByReference highlightPath = new PointerByReference();
            int createHighlightPathStatus = Gdiplus.INSTANCE.GdipCreatePath(0, highlightPath);
            PointerByReference shadowOutlinePath = new PointerByReference();
            int createShadowOutlinePathStatus = Gdiplus.INSTANCE.GdipCreatePath(0, shadowOutlinePath);
            List<PointerByReference> outlinePens =
                    fontOutlineThickness == 0 || fontOutlineOpacity == 0 ? List.of() :
                            createOutlinePens(fontOutlineThickness, fontOutlineThickness,
                                    fontOutlineHexColor,
                                    fontOutlineOpacity);
            fontShadowStep = Math.min(fontShadowThickness, fontShadowStep);
            List<PointerByReference> shadowPens = fontShadowThickness == 0 ? List.of() :
                    createOutlinePens(fontShadowThickness, fontShadowStep, fontShadowHexColor,
                            fontShadowOpacity);

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
            PointerByReference shadowFontBrush = new PointerByReference();
            // Formula is 1 - (1 - opacity)^(number of times opacity is applied).
            // Note: the shadow body is drawn on top of boxColor, and so it appears darker than shadowFontBrushColor.
            int shadowOutlineStepCount = (int) (fontShadowThickness / fontShadowStep);
            double shadowFontBodyOpacity = shadowOutlineStepCount == 0 ? fontShadowOpacity :
                    1 - Math.pow(1 - fontShadowOpacity, shadowOutlineStepCount);
            int shadowFontBrushColor = ((int) (shadowFontBodyOpacity * 255) << 24) | hexColorStringToRgb(fontShadowHexColor, 1d);
            int shadowFontBrushStatus = Gdiplus.INSTANCE.GdipCreateSolidFill(shadowFontBrushColor, shadowFontBrush);

            PointerByReference stringFormat = stringFormat();

            // Without caching, a full screen of hints drawn with GDI+ takes some time
            // to compute, even when there is no outline.
            hintMeshDraw = hintMeshDraw(screen, normalizedHints,
                    focusedHintKeySequence,
                    highlightFontScale, expandBoxes,
                    fontSpacingPercent,
                    boxBorderThickness, windowWidth, windowHeight,
                    hintBoundingBoxes);
            boolean hintMeshMustBeCached = isHintGrid &&
                                           (hintMeshDraw.hintSequenceTexts.size() > 200 ||
                                            focusedHintKeySequence.isEmpty() ||
                                            fontShadowOpacity != 0 && shadowOutlineStepCount > 1);
            if (hintMeshMustBeCached) {
                // The pixelData is a full screen int[][]. We don't want to cache too many
                // of them.
                logger.trace("Caching new hintMeshDraw with " +
                             hintMeshDraw.hintSequenceTexts.size() + " visible hints");
                hintMeshDrawCache.put(roundedNormalizedHintMesh, hintMeshDraw);
            }

            int noColorColor = boxColor == 0 ? 1 : 0; // We need a placeholder color that is not used.
            // Shadow outline drawn first, then shadow body (which must overwrite existing shadow outline pixel),
            // then the rest.
            boolean mustDrawShadow = fontShadowOpacity != 0;
            int[] shadowBodyPixelData = mustDrawShadow ? new int[hintMeshDraw.pixelData.length] : null;
            if (mustDrawShadow) {
//                clearWindow(hdcTemp, windowRect, boxColor);
                if (fontShadowThickness > 0) {
                    // No antialiasing for the shadow body.
                    Gdiplus.INSTANCE.GdipSetSmoothingMode(graphics.getValue(),
                            0); // 2= SmoothingModeAntiAlias
                }
                for (HintSequenceText hintSequenceText : hintMeshDraw.hintSequenceTexts()) {
                    for (HintKeyText keyText : hintSequenceText.keyTexts) {
                        float gdipFontSize = keyText.isPrefix ? normalGdipFontSize :
                                (keyText.isHighlight ? largeGdipFontSize :
                                        normalGdipFontSize);
                        drawHintText(keyText.keyText,
                                keyText.left + fontShadowHorizontalOffset,
                                keyText.top + fontShadowVerticalOffset, shadowOutlinePath,
                                fontFamilyAndStyle, fontFamily, gdipFontSize,
                                stringFormat);
                    }
                }
                // If shadow body and outline are drawn at the same time,
                // some area of the font body and font outline overlaps, so they are rendered twice,
                // therefore it is darker.
                //drawAndFillPath(shadowPens, graphics, shadowOutlinePath, null);
                drawAndFillPath(List.of(), graphics, shadowOutlinePath, shadowFontBrush);
                dibSection.pixelPointer.read(0, shadowBodyPixelData, 0, shadowBodyPixelData.length);
                if (fontShadowThickness > 0) {
                    Gdiplus.INSTANCE.GdipSetSmoothingMode(graphics.getValue(),
                            2); // 2= SmoothingModeAntiAlias
                }
            }

            // No cell if cellWidth/Height is not defined (e.g. non-grid hint mesh).
            int colorBetweenBoxes =
                            hexColorStringToRgb(boxBorderHexColor, boxBorderOpacity) |
                    ((int) (255 * boxBorderOpacity) << 24);
            clearWindow(hdcTemp, windowRect, noColorColor); // Clears the shadow body.
            dibSection.pixelPointer.read(0, hintMeshDraw.pixelData, 0, hintMeshDraw.pixelData.length); // TODO remove?
            int borderLengthPixels =
                    Math.max((int) Math.floor(boxBorderLength * 1), (int) boxBorderThickness);
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
                        int i = y * windowWidth + x;
                        int pixel = hintMeshDraw.pixelData[i];
                        if (pixel == noColorColor)
                            hintMeshDraw.pixelData[i] = boxColor;
                    }
                }
                int minX = Math.max(0, cellRect.left);
                int maxX = Math.min(windowWidth, cellRect.right);
                int minY = Math.max(0, cellRect.top);
                int maxY = Math.min(windowHeight, cellRect.bottom);
                for (int x = minX; x < maxX; x++) {
                    for (int y = minY; y < maxY; y++) {
                        // The cell may go past the screen dimensions.
                        int i = y * windowWidth + x;
                        int pixel = hintMeshDraw.pixelData[i];
                        if (pixel == noColorColor) {
                            if ((x - minX < borderLengthPixels || maxX - 1 - x < borderLengthPixels)
                                && (y - minY < borderLengthPixels || maxY - 1 - y < borderLengthPixels)) {
                                hintMeshDraw.pixelData[i] = colorBetweenBoxes;
                            }
                            else
                                hintMeshDraw.pixelData[i] = boxColor;
                        }
                    }
                }
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
                int columnWidthOffset = subgridColumnExtraPixelDistribution[0] ? 1 : 0;
                for (int subgridColumnIndex = 1;
                     subgridColumnIndex < subgridColumnCount; subgridColumnIndex++) {
                    int columnCenterX = minX + columnWidthOffset + subgridColumnIndex * subWidth;
                    columnWidthOffset += subgridColumnExtraPixelDistribution[subgridColumnIndex] ? 1 : 0;
                    int centerY = minY + centerRowHeightOffset + centerRowIndex * subHeight;
                    for (int y = Math.max(minY, centerY - borderThicknessOrLength / 2);
                         y <= Math.min(maxY - 1, centerY + borderThicknessOrLength / 2 - (scaledSubgridBorderThickness - 1) % 2); y++) {
                        for (int thicknessX = 0; thicknessX < scaledSubgridBorderThickness; thicknessX++) {
                            int x = columnCenterX - scaledSubgridBorderThickness / 2 + thicknessX;
                            int i = y * windowWidth + x;
                            if (hintMeshDraw.pixelData[i] == boxColor)
                                hintMeshDraw.pixelData[i] = colorBetweenSubBoxes;
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
                            int i = y * windowWidth + x;
                            if (hintMeshDraw.pixelData[i] == boxColor)
                                hintMeshDraw.pixelData[i] = colorBetweenSubBoxes;
                        }
                    }
                }
            }
            dibSection.pixelPointer.write(0, hintMeshDraw.pixelData, 0, hintMeshDraw.pixelData.length);
            if (mustDrawShadow) {
                if (shadowOutlineStepCount > 0)
                    drawAndFillPath(shadowPens, graphics, shadowOutlinePath, null);
                dibSection.pixelPointer.read(0, hintMeshDraw.pixelData, 0,
                        hintMeshDraw.pixelData.length);
                for (int i = 0; i < hintMeshDraw.pixelData.length; i++) {
                    int shadowBodyPixel = shadowBodyPixelData[i];
                    if (shadowBodyPixel == boxColor)
                        continue;
                    hintMeshDraw.pixelData[i] = shadowBodyPixel;
                }
                dibSection.pixelPointer.write(0, hintMeshDraw.pixelData, 0,
                        hintMeshDraw.pixelData.length);
            }

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

            if (hintMeshMustBeCached || normalizedHints.offsetX != 0 ||
                normalizedHints.offsetY != 0) {
                dibSection.pixelPointer.read(0, hintMeshDraw.pixelData, 0,
                        hintMeshDraw.pixelData.length);
                int[] pixelData =
                        offsetPixelData(hintMeshDraw, windowWidth, windowHeight,
                                normalizedHints.offsetX, normalizedHints.offsetY);
                dibSection.pixelPointer.write(0, pixelData, 0, pixelData.length);
            }
            updateLayeredWindow(windowRect, hintMeshWindow, hdcTemp);

            Gdiplus.INSTANCE.GdipDeleteBrush(prefixFontBrush.getValue());
            Gdiplus.INSTANCE.GdipDeleteBrush(suffixFontBrush.getValue());
            Gdiplus.INSTANCE.GdipDeleteBrush(highlightFontBrush.getValue());
            Gdiplus.INSTANCE.GdipDeleteBrush(shadowFontBrush.getValue());
            for (PointerByReference pen : outlinePens)
                Gdiplus.INSTANCE.GdipDeletePen(pen.getValue());
            for (PointerByReference pen : shadowPens)
                Gdiplus.INSTANCE.GdipDeletePen(pen.getValue());
            Gdiplus.INSTANCE.GdipDeleteStringFormat(stringFormat.getValue());
            Gdiplus.INSTANCE.GdipDeletePath(prefixPath.getValue());
            Gdiplus.INSTANCE.GdipDeletePath(suffixPath.getValue());
            Gdiplus.INSTANCE.GdipDeletePath(highlightPath.getValue());
            Gdiplus.INSTANCE.GdipDeletePath(shadowOutlinePath.getValue());
        }

        Gdiplus.INSTANCE.GdipDeleteFont(normalFont.getValue());
        Gdiplus.INSTANCE.GdipDeleteFont(largeFont.getValue());
        Gdiplus.INSTANCE.GdipDeleteFontFamily(fontFamily.getValue());
        Gdiplus.INSTANCE.GdipDeleteGraphics(graphics.getValue());

        GDI32.INSTANCE.SelectObject(hdcTemp, oldDIBitmap);
        GDI32.INSTANCE.DeleteObject(hbm);
        GDI32.INSTANCE.DeleteObject(dibSection.hDIBitmap);
        GDI32.INSTANCE.DeleteDC(hdcTemp);

    }

    private static int[] offsetPixelData(HintMeshDraw hintMeshDraw,
                                         int windowWidth, int windowHeight,
                                         double offsetX, double offsetY) {
        int offsetXInt = (int) Math.floor(offsetX);
        int offsetYInt = (int) Math.floor(offsetY);
        int[] pixelData = new int[hintMeshDraw.pixelData.length];
        for (int x = offsetXInt; x < windowWidth; x++) {
            for (int y = offsetYInt; y < windowHeight; y++) {
                int source = (y - offsetYInt) * windowWidth + (x - offsetXInt);
                int destination = y * windowWidth + x;
                pixelData[destination] = hintMeshDraw.pixelData[source];
            }
        }
        return pixelData;
    }

    private static List<PointerByReference> createOutlinePens(double outlineThickness,
                                                              double step,
                                                              String fontOutlineHexColor,
                                                              double outlineOpacity) {
        int outlineColorRgb = hexColorStringToRgb(fontOutlineHexColor, 1d);
        int penColor = (int) (outlineOpacity * 0xFF) << 24 |
                       outlineColorRgb; //0x80000000; // ARGB
        List<PointerByReference> outlinePens = new ArrayList<>();
        for (int stepIndex = 1; stepIndex * step <= outlineThickness; stepIndex++) {
            PointerByReference pen = new PointerByReference();
            outlinePens.add(pen);
            int penStatus =
                    Gdiplus.INSTANCE.GdipCreatePen1(penColor, (float) (stepIndex * step),
                            2, pen); // 2 = UnitPixel
            if (penStatus != 0) {
                throw new RuntimeException("Failed to create Pen. Status: " + penStatus);
            }
            int setLineJoinStatus = Gdiplus.INSTANCE.GdipSetPenLineJoin(pen.getValue(),
                    2); // 2 = LineJoinRound
            if (setLineJoinStatus != 0) {
                throw new RuntimeException(
                        "Failed to set Pen LineJoin to Round. Status: " +
                        setLineJoinStatus);
            }
        }
        return outlinePens;
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
        if (fontBrush == null)
            return;
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

    /**
     * Offsets within the screen.
     */
    private record NormalizedHints(List<Hint> hints, double offsetX, double offsetY) {

    }

    private static NormalizedHints normalizedHints(Screen screen, List<Hint> originalHints,
                                                   List<Key> focusedHintKeySequence,
                                                   double highlightFontScale, double boxBorderThickness,
                                                   boolean isHintPartOfGrid,
                                                   HintBoundingBoxes hintBoundingBoxes) {
        if (!isHintPartOfGrid) {
            // No cellWidth/Height if !isHintPartOfGrid
            return new NormalizedHints(originalHints, screen.rectangle().x(),
                    screen.rectangle().y());
        }
        double minHintCellX = Double.MAX_VALUE;
        double minHintCellY = Double.MAX_VALUE;
        for (Hint hint : originalHints) {
            if (!hint.startsWith(focusedHintKeySequence))
                continue;
            HintFontBoundingBox hintFontBoundingBox =
                    hintFontBoundingBox(hint, focusedHintKeySequence, highlightFontScale,
                            hintBoundingBoxes.normalFontBoundingBoxes,
                            hintBoundingBoxes.largeFontBoundingBoxes);
            double cellWidth = Math.max(hintFontBoundingBox.hintKeyTextTotalXAdvance,
                    hint.cellWidth());
            minHintCellX = Math.min(minHintCellX, hint.centerX() - cellWidth / 2);
            double cellHeight = Math.max(hintFontBoundingBox.highestKeyHeight,
                    hint.cellHeight());
            minHintCellY = Math.min(minHintCellY, hint.centerY() - cellHeight / 2);
        }
        double offsetX = Math.max(0, Math.floor(minHintCellX - boxBorderThickness / 2d - screen.rectangle().x()));
        double offsetY = Math.max(0, Math.floor(minHintCellY - boxBorderThickness / 2d - screen.rectangle().y()));
//        offsetY = offsetX = 0;
        List<Hint> hints = new ArrayList<>();
        for (Hint hint : originalHints) {
            if (!hint.startsWith(focusedHintKeySequence))
                continue;
            hint = new Hint(hint.centerX() - offsetX - screen.rectangle().x(),
                    hint.centerY() - offsetY - screen.rectangle().y(),
                    hint.cellWidth(),
                    hint.cellHeight(), hint.keySequence());
            hints.add(hint);
        }
        return new NormalizedHints(hints, offsetX, offsetY);
    }

    private static HintMeshDraw hintMeshDraw(Screen screen,
                                             NormalizedHints normalizedHints,
                                             List<Key> focusedHintKeySequence,
                                             double highlightFontScale,
                                             boolean expandBoxes,
                                             double fontSpacingPercent,
                                             double boxBorderThickness,
                                             int windowWidth, int windowHeight,
                                             HintBoundingBoxes hintBoundingBoxes) {
        List<WinDef.RECT> boxRects = new ArrayList<>();
        List<WinDef.RECT> cellRects = new ArrayList<>();
        List<HintSequenceText> hintSequenceTexts = new ArrayList<>();
        double maxKeyBoundingBoxWidth =
                hintBoundingBoxes.maxKeyBoundingBoxWidth();
            double maxKeyBoundingBoxX = hintBoundingBoxes.maxKeyBoundingBoxX();
        Map<Key, Gdiplus.GdiplusRectF> normalFontBoundingBoxes =
                hintBoundingBoxes.normalFontBoundingBoxes();
        Map<Key, Gdiplus.GdiplusRectF> largeFontBoundingBoxes =
                hintBoundingBoxes.largeFontBoundingBoxes();
        double minHintCenterX = Double.MAX_VALUE;
        double minHintCenterY = Double.MAX_VALUE;
        double maxHintCenterX = Double.MIN_VALUE;
        double maxHintCenterY = Double.MIN_VALUE;
        double maxSimpleBoxWidth = 0;
        double highestKeyHeight = 0;
        for (Hint hint : normalizedHints.hints()) {
            double hintCenterX = hint.centerX();
            double hintCenterY = hint.centerY();
            maxHintCenterX = Math.max(maxHintCenterX, hintCenterX);
            maxHintCenterY = Math.max(maxHintCenterY, hintCenterY);
            minHintCenterX = Math.min(minHintCenterX, hintCenterX);
            minHintCenterY = Math.min(minHintCenterY, hintCenterY);
            double smallestColAlignedFontBoxWidth;
            smallestColAlignedFontBoxWidth = maxKeyBoundingBoxWidth * hint.keySequence().size()
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
            HintFontBoundingBox hintFontBoundingBox =
                    hintFontBoundingBox(hint, focusedHintKeySequence, highlightFontScale,
                            normalFontBoundingBoxes,
                            largeFontBoundingBoxes);
            highestKeyHeight =
                    Math.max(highestKeyHeight, hintFontBoundingBox.highestKeyHeight);
            double hintKeyTextTotalXAdvance = hintFontBoundingBox.hintKeyTextTotalXAdvance;
            double lastKeyWidth = hintFontBoundingBox.lastKeyWidth;
            // If adjustedFontBoxWidthPercent is too small, then we don't try to align characters
            // in columns anymore and we just place them next to each other (percent = 0
            // to smallestColAlignedFontBoxWidthPercent), centered in the box.
            double extraNotAlignedWidth = smallestColAlignedFontBoxWidth -
                                          hintKeyTextTotalXAdvance;
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
                    left = hintCenterX - (hintKeyTextTotalXAdvance + extraNotAlignedWidth) / 2
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
                    left = hintCenterX - fontBoxWidth / 2
                           + xAdvance
                           + keySubcellWidth / 2
                           - (boundingBox.width) / 2 - boundingBox.x / 2;
                    xAdvance += keySubcellWidth - boundingBox.x;
                }
                double top = hintCenterY -
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
            double hintCenterX = hint.centerX();
            double hintCenterY = hint.centerY();
            WinDef.RECT boxRect = new WinDef.RECT();
            double simpleBoxLeft =
                    hintCenterX - maxSimpleBoxWidth / 2;

            double simpleBoxTop =
                    hintCenterY - highestKeyHeight / 2;
//            boxRect.left = (int) (simpleBoxLeft + boxBorderThickness);
//            boxRect.top = (int) (simpleBoxTop + boxBorderThickness);
            boxRect.left = (int) (simpleBoxLeft + (hintCenterX > minHintCenterX ?
                    boxBorderThickness : 0));
            boxRect.top = (int) (simpleBoxTop + (hintCenterY > minHintCenterY ?
                    boxBorderThickness : 0));

            double simpleBoxRight = hintCenterX + maxSimpleBoxWidth / 2;
            boxRect.right = (int) (simpleBoxRight - (hintCenterX <
                                                     maxHintCenterX ? 0 :
                    boxBorderThickness));
            double simpleBoxBottom = simpleBoxTop + highestKeyHeight;
            boxRect.bottom = (int) (simpleBoxBottom - (hintCenterY <
                                                       maxHintCenterY ? 0 :
                    boxBorderThickness));
            boolean isHintPartOfGrid = hint.cellWidth() != -1;
            WinDef.RECT cellRect = new WinDef.RECT();
            if (!expandBoxes || !isHintPartOfGrid) {
                boxRect.left = (int) Math.round(simpleBoxLeft);
                boxRect.top = (int) Math.round(simpleBoxTop);
                boxRect.right = (int) Math.round(simpleBoxRight);
                boxRect.bottom = (int) Math.round(simpleBoxBottom);
            }
            else {
                setBoxRect(boxRect, screen, boxBorderThickness,
                        hint.cellWidth(), hint.cellHeight(), hintCenterX,
                        hintCenterY, minHintCenterX, maxHintCenterX,
                        minHintCenterY,
                        maxHintCenterY, normalizedHints.offsetX, normalizedHints.offsetY);
            }
            cellRect.left = (int) (boxRect.left - boxBorderThickness);
            cellRect.top = (int) (boxRect.top - boxBorderThickness);
            cellRect.right = (int) (boxRect.right + boxBorderThickness);
            cellRect.bottom = (int) (boxRect.bottom + boxBorderThickness);
            cellRects.add(cellRect); // TODO only if between box is non transparent
            boxRects.add(boxRect);
        }
        return new HintMeshDraw(boxRects, cellRects, hintSequenceTexts, new int[windowWidth * windowHeight]);
    }

    private static HintBoundingBoxes hintCentersAndBoundingBoxes(
            List<Hint> hints, List<Key> focusedHintKeySequence,
            double highlightFontScale, PointerByReference graphics,
            PointerByReference normalFont,
            PointerByReference largeFont) {
        Map<Key, Gdiplus.GdiplusRectF> normalFontBoundingBoxes = new HashMap<>();
        Map<Key, Gdiplus.GdiplusRectF> largeFontBoundingBoxes = new HashMap<>();
        double maxKeyBoundingBoxWidth = 0;
        double maxKeyBoundingBoxX = 0;
        PointerByReference stringFormat = stringFormat();
        PointerByReference region = new PointerByReference();
        Gdiplus.INSTANCE.GdipCreateRegion(region);
        Gdiplus.GdiplusRectF layoutRect = new Gdiplus.GdiplusRectF(0, 0, 1000, 1000);
        for (Hint hint : hints) {
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
//                if (maxKeyBoundingBoxWidth != 0 &&
//                    maxKeyBoundingBoxWidth != boundingBox.width
//                    || maxKeyBoundingBoxX != 0 && maxKeyBoundingBoxX != boundingBox.x)
//                    isFixedSizeWidthFont = false;
                if (boundingBox.width + boundingBox.x >
                    maxKeyBoundingBoxWidth + maxKeyBoundingBoxX) {
                    maxKeyBoundingBoxWidth = boundingBox.width;
                    maxKeyBoundingBoxX = boundingBox.x;
                }
            }
        }
        Gdiplus.INSTANCE.GdipDeleteStringFormat(stringFormat.getValue());
        Gdiplus.INSTANCE.GdipDeleteRegion(region.getValue());
        return new HintBoundingBoxes(
                normalFontBoundingBoxes, largeFontBoundingBoxes,
                maxKeyBoundingBoxWidth, maxKeyBoundingBoxX);
    }

    private record HintBoundingBoxes(
            Map<Key, Gdiplus.GdiplusRectF> normalFontBoundingBoxes,
                                               Map<Key, Gdiplus.GdiplusRectF> largeFontBoundingBoxes,
                                               double maxKeyBoundingBoxWidth, double maxKeyBoundingBoxX
    ) {
    }

    private static HintFontBoundingBox hintFontBoundingBox(Hint hint, List<Key> focusedHintKeySequence,
                                                           double highlightFontScale,
                                                           Map<Key, Gdiplus.GdiplusRectF> normalFontBoundingBoxes,
                                                           Map<Key, Gdiplus.GdiplusRectF> largeFontBoundingBoxes) {
        double lastKeyWidth = 0;
        double hintKeyTextTotalXAdvance = 0;
        double highestKeyHeight = 0;
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
                lastKeyWidth = boundingBox.width + boundingBox.x;
            }
        }
        return new HintFontBoundingBox(hintKeyTextTotalXAdvance, lastKeyWidth, highestKeyHeight);
    }

    private record HintFontBoundingBox(double hintKeyTextTotalXAdvance, double lastKeyWidth,
                                       double highestKeyHeight) {
    }

    private static Gdiplus.GdiplusRectF hintKeyBoundingBox(PointerByReference graphics,
                                                           PointerByReference normalFont,
                                                           Key key,
                                                           Map<Key, Gdiplus.GdiplusRectF> boundingBoxes,
                                                           Gdiplus.GdiplusRectF layoutRect,
                                                           PointerByReference stringFormat,
                                                           PointerByReference region) {
        return boundingBoxes.computeIfAbsent(key,
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

    private static void setBoxRect(WinDef.RECT boxRect, Screen screen, double boxBorderThickness,
                                   double cellWidth, double cellHeight,
                                   double hintCenterX, double hintCenterY,
                                   double minHintCenterX, double maxHintCenterX,
                                   double minHintCenterY, double maxHintCenterY,
                                   double offsetX, double offsetY) {
        double halfCellWidth = cellWidth / 2;
        double halfCellHeight = cellHeight / 2;

        halfCellWidth -= boxBorderThickness / 2;
        halfCellHeight -= boxBorderThickness / 2;

        double boxLeft = hintCenterX - halfCellWidth;
        double boxTop = hintCenterY - halfCellHeight;
        double boxRight = hintCenterX + halfCellWidth;
        double boxBottom = hintCenterY + halfCellHeight;
        int roundedBoxLeft = (int) Math.ceil(boxLeft);
        int roundedBoxTop = (int) Math.ceil(boxTop);
        int roundedBoxRight = (int) Math.ceil(boxRight);
        int roundedBoxBottom = (int) Math.ceil(boxBottom);

        if (Math.abs(offsetX + roundedBoxLeft - (Math.ceil(boxBorderThickness / 2))) < 1e-1)
            boxLeft += boxBorderThickness / 2;
        if (Math.abs(offsetX + roundedBoxRight - (screen.rectangle().width() - Math.floor(boxBorderThickness / 2))) < 1e-1)
            boxRight -= boxBorderThickness / 2;
        if (Math.abs(offsetY + roundedBoxTop - (Math.ceil(boxBorderThickness / 2))) < 1e-1)
            boxTop += boxBorderThickness / 2;
        if (Math.abs(offsetY + roundedBoxBottom - (screen.rectangle().height() - Math.floor(boxBorderThickness / 2))) < 1e-1)
            boxBottom -= boxBorderThickness / 2;

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