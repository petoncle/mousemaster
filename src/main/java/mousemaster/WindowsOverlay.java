package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
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
    private static ZoomWindow zoomWindow;
    private static Zoom currentZoom;

    public static void update(double delta) {
        updateZoomWindow();
    }

    private static void updateZoomWindow() {
        if (currentZoom == null)
            return;
        WinDef.RECT sourceRect = new WinDef.RECT();
        Zoom zoom = currentZoom;
        Screen screen = WindowsScreen.findActiveScreen(new WinDef.POINT(zoom.center().x(),
                zoom.center().y()));
        double zoomPercent = zoom.percent();
        sourceRect.left = (int) (zoom.center().x() - screen.rectangle().width() / zoomPercent / 2);
        sourceRect.top = (int) (zoom.center().y() - screen.rectangle().height() / zoomPercent / 2);
        sourceRect.right = (int) (zoom.center().x() + screen.rectangle().width() / zoomPercent / 2);
        sourceRect.bottom = (int) (zoom.center().y() + screen.rectangle().height() / zoomPercent / 2);
        if (!Magnification.INSTANCE.MagSetWindowSource(zoomWindow.hwnd(), sourceRect)) {
            logger.error("Failed MagSetWindowSource: " +
                         Integer.toHexString(Native.getLastError()));
        }
        User32.INSTANCE.InvalidateRect(zoomWindow.hwnd(), null, true);
        User32.INSTANCE.ShowWindow(zoomWindow.hostHwnd(), WinUser.SW_SHOWNORMAL);
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
        if (zoomWindow != null)
            hwnds.add(zoomWindow.hwnd);
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
                                  List<Hint> hints,
                                  Map<HintMesh, HintMeshDraw> hintMeshDrawCache) {

    }

    private record ZoomWindow(WinDef.HWND hwnd, WinDef.HWND hostHwnd, WinUser.WindowProc callback) {

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

    private static void createIndicatorWindow(int indicatorSize) {
        WinDef.POINT mousePosition = WindowsMouse.findMousePosition();
        MouseSize mouseSize = WindowsMouse.mouseSize();
        Screen activeScreen = WindowsScreen.findActiveScreen(mousePosition);
        WinUser.WindowProc callback = WindowsOverlay::indicatorWindowCallback;
        int scaledIndicatorSize = scaledPixels(indicatorSize, activeScreen.scale());
        // +1 width and height because no line can be drawn on y = windowHeight and y = windowWidth.
        WinDef.HWND hwnd = createWindow("Indicator",
                bestIndicatorX(mousePosition.x, mouseSize.width(),
                        activeScreen.rectangle(), scaledIndicatorSize),
                bestIndicatorY(mousePosition.y, mouseSize.height(),
                        activeScreen.rectangle(), scaledIndicatorSize),
                scaledIndicatorSize + 1, scaledIndicatorSize + 1, callback);
        indicatorWindow = new IndicatorWindow(hwnd, callback, 0);
        updateZoomExcludedWindows();
    }

    private static int scaledPixels(int originalInPixels, double scale) {
        return (int) Math.floor(originalInPixels * scale);
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
                                hintsInScreen, new HashMap<>()));
            }
            updateZoomExcludedWindows();
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
        logger.info(
                "registerClassExResult = " +
                Integer.toHexString(registerClassExResult.intValue()));
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
        logger.info(
                "CreateWindowEx host = " + Integer.toHexString(Native.getLastError()));
        User32.INSTANCE.SetLayeredWindowAttributes(hostHwnd, 0, (byte) 255,
                WinUser.LWA_ALPHA);
        WinDef.HWND hwnd = User32.INSTANCE.CreateWindowEx(
                0,
                WC_MAGNIFIER, "MagnifierWindow",
                User32.WS_CHILD | MS_SHOWMAGNIFIEDCURSOR | ExtendedUser32.WS_VISIBLE,
                0, 0, 10, 10,
                hostHwnd, null,
                hInstance, null);
        logger.info("CreateWindowEx = " + Integer.toHexString(Native.getLastError()));

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
                User32.INSTANCE.SetLayeredWindowAttributes(hwnd, 0, (byte) 0, WinUser.LWA_ALPHA);
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
                              WinDef.HBITMAP hDIBitmap, int[] pixelData) {
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
        int[] pixelData = new int[width * height];
        return new DibSection(pixelPointer, hDIBitmap, pixelData);
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

    private record HintText(
            WinDef.RECT prefixRect, String prefixText,
            WinDef.RECT highlightRect, String highlightText,
            WinDef.RECT suffixRect, String suffixText
            ) {

    }

    private record HintMeshDraw(List<WinDef.RECT> boxRects,
                                List<WinDef.RECT> cellRects,
                                List<HintText> hintTexts) {
    }

    private static void drawHints(WinDef.HDC hdc,
                                  WinDef.RECT windowRect, Screen screen,
                                  HintMeshWindow hintMeshWindow,
                                  List<Hint> windowHints) {
        WinDef.HDC hdcTemp = GDI32.INSTANCE.CreateCompatibleDC(hdc);

        String fontName = currentHintMesh.fontName();
        int fontSize = currentHintMesh.fontSize();
        double fontOpacity = currentHintMesh.fontOpacity();
        double highlightFontScale = currentHintMesh.highlightFontScale();
        String boxHexColor = currentHintMesh.boxHexColor();
        double boxOpacity = currentHintMesh.boxOpacity();
        int boxBorderThickness = currentHintMesh.boxBorderThickness();
        String boxBorderHexColor = currentHintMesh.boxBorderHexColor();
        double boxBorderOpacity = currentHintMesh.boxBorderOpacity();
        String fontHexColor = currentHintMesh.fontHexColor();
        String prefixFontHexColor = currentHintMesh.prefixFontHexColor();
        List<Key> focusedHintKeySequence = currentHintMesh.focusedKeySequence();
//        int scaledDpi = (int) (screen.dpi() * screen.scale());
        int dpi = screen.dpi();
        // Convert point size to logical units.
        // 1 point = 1/72 inch. So, multiply by dpi and divide by 72 to convert to pixels.
        int fontHeight = -fontSize * dpi / 72;
        int largeFontHeight = -(int) (fontSize * highlightFontScale) * dpi / 72;
        // In Windows API, negative font size means "point size" (as opposed to pixels).
        WinDef.HFONT normalFont =
                createFont(fontHeight, fontName);
        WinDef.HFONT largeFont = highlightFontScale == 1 ? normalFont :
                createFont(largeFontHeight, fontName);
        int windowWidth = windowRect.right - windowRect.left;
        int windowHeight = windowRect.bottom - windowRect.top;
        HintMeshDraw hintMeshDraw = hintMeshWindow.hintMeshDrawCache.computeIfAbsent(currentHintMesh,
                hintMesh -> hintMeshDraw(screen, windowWidth, windowHeight, windowHints,
                        focusedHintKeySequence,
                        highlightFontScale, boxBorderThickness,
                        normalFont, largeFont, hdcTemp));

        WinDef.HBITMAP hbm = GDI32.INSTANCE.CreateCompatibleBitmap(hdc, windowWidth, windowHeight);
        GDI32.INSTANCE.SelectObject(hdcTemp, hbm);

        DibSection dibSection = createDibSection(windowWidth, windowHeight, hdcTemp);
        // Select the DIB section into the memory DC.
        WinNT.HANDLE oldDIBitmap = GDI32.INSTANCE.SelectObject(hdcTemp, dibSection.hDIBitmap);
        if (oldDIBitmap == null)
            throw new RuntimeException("Unable to select bitmap into source DC.");

        double mergedOpacity = boxOpacity + fontOpacity * (1 - boxOpacity);
        int overWhiteBoxColor = hexColorStringToRgb(blendColorOverWhite(boxHexColor,
                Math.min(boxOpacity, fontOpacity)), 1);
        if (overWhiteBoxColor == 0xFFFFFF)
            overWhiteBoxColor = 0; // Not sure why this and boxColorInt must be overwritten. If this is not done, background is opaque.
        clearWindow(hdcTemp, windowRect, overWhiteBoxColor);
        int prefixFontColorInt = hexColorStringToInt(prefixFontHexColor);
        int fontColorInt = hexColorStringToInt(fontHexColor);
        for (HintText hintText : hintMeshDraw.hintTexts()) {
            if (hintText.prefixRect != null) {
                GDI32.INSTANCE.SelectObject(hdcTemp, normalFont);
                drawHintText(hdcTemp, hintText.prefixRect,
                        hintText.prefixText, dibSection, windowWidth,
                        prefixFontColorInt);
            }
            if (hintText.suffixRect != null) {
                GDI32.INSTANCE.SelectObject(hdcTemp, normalFont);
                drawHintText(hdcTemp, hintText.suffixRect, hintText.suffixText,
                        dibSection, windowWidth, fontColorInt);
            }
            if (hintText.highlightRect != null) {
                WinNT.HANDLE largeFont0 =
                        hintText.prefixRect == null && hintText.suffixRect == null ?
                                normalFont : largeFont;
                GDI32.INSTANCE.SelectObject(hdcTemp, largeFont0);
                drawHintText(hdcTemp, hintText.highlightRect,
                        hintText.highlightText, dibSection, windowWidth,
                        fontColorInt);
            }
        }
        int boxColorInt = hexColorStringToRgb(boxHexColor, boxOpacity) |
                          (int) (boxOpacity * 255) << 24;
        if (boxColorInt == 0)
            boxColorInt = 1;
        // No cell if cellWidth/Height is not defined (e.g. non-grid hint mesh).
        int colorBetweenBoxesInt =
                hexColorStringToRgb(boxBorderHexColor, boxBorderOpacity) | ((int) (255 * boxBorderOpacity) << 24);
        dibSection.pixelPointer.read(0, dibSection.pixelData, 0, dibSection.pixelData.length);
        for (int i = 0; i < dibSection.pixelData.length; i++) {
            int pixel = dibSection.pixelData[i];
            int rgb = pixel & 0x00FFFFFF; // Keep RGB
            if (rgb == overWhiteBoxColor)
                // The previous call to clearWindow(hdcTemp, windowRect, hintMeshWindow.transparentColor)
                // has filled everything with the grey color. That call was needed
                // because the DrawText needs the grey color for the antialiasing.
                // Now we remove the grey color, and we will put it back only for the boxes.
                // (We need to clear the pixels that are not in boxes.)
                dibSection.pixelData[i] = 0;
            else if (pixel != 0) {
                int boxColor = boxColorInt & 0x00FFFFFF;
                rgb = blend(boxColor, rgb, fontOpacity);
                dibSection.pixelData[i] = alphaMultipliedChannelsColor(rgb, mergedOpacity) | ((int) (255 * mergedOpacity) << 24);
//                // Make the text pixel fully opaque.
//                double fontOpacity = 0.1d;//0.4d;
//                double mergedOpacity = boxOpacity + fontOpacity * (1 - boxOpacity); // looks smoother
////                mergedOpacity *= fontOpacity;
////                double mergedOpacity = fontOpacity;
//                rgb = blend(overWhiteBoxColor, rgb, mergedOpacity);
////                double mergedOpacity = 1;
//                dibSection.pixelData[i] = alphaMultipliedChannelsColor(rgb, mergedOpacity) | ((int) (255 * mergedOpacity) << 24);
            }
        }
        for (WinDef.RECT boxRect : hintMeshDraw.boxRects()) {
            for (int x = Math.max(0, boxRect.left); x < Math.min(windowWidth, boxRect.right); x++) {
                for (int y = Math.max(0, boxRect.top); y < Math.min(windowHeight, boxRect.bottom); y++) {
                    // The box may go past the screen dimensions.
                    if (dibSection.pixelData[y * windowWidth + x] == 0)
                        dibSection.pixelData[y * windowWidth + x] = boxColorInt;
                }
            }
        }
        for (WinDef.RECT cellRect : hintMeshDraw.cellRects()) {
            for (int x = Math.max(0, cellRect.left); x < Math.min(windowWidth, cellRect.right); x++) {
                for (int y = Math.max(0, cellRect.top); y < Math.min(windowHeight, cellRect.bottom); y++) {
                    // The cell may go past the screen dimensions.
                    if (dibSection.pixelData[y * windowWidth + x] == 0)
                        dibSection.pixelData[y * windowWidth + x] = colorBetweenBoxesInt;
                }
            }
        }
        dibSection.pixelPointer.write(0, dibSection.pixelData, 0, dibSection.pixelData.length);

        updateLayeredWindow(windowRect, hintMeshWindow, hdcTemp);

        GDI32.INSTANCE.DeleteObject(normalFont);
        GDI32.INSTANCE.DeleteObject(largeFont);

        GDI32.INSTANCE.SelectObject(hdcTemp, oldDIBitmap);
        GDI32.INSTANCE.DeleteObject(hbm);
        GDI32.INSTANCE.DeleteObject(dibSection.hDIBitmap);
        GDI32.INSTANCE.DeleteDC(hdcTemp);

    }

    private static WinDef.HFONT createFont(int fontHeight, String fontName) {
        boolean antialiased = true;
        return ExtendedGDI32.INSTANCE.CreateFontA(fontHeight, 0, 0, 0,
                ExtendedGDI32.FW_NORMAL,
                new WinDef.DWORD(0), new WinDef.DWORD(0), new WinDef.DWORD(0),
                new WinDef.DWORD(ExtendedGDI32.ANSI_CHARSET),
                new WinDef.DWORD(ExtendedGDI32.OUT_DEFAULT_PRECIS),
                new WinDef.DWORD(ExtendedGDI32.CLIP_DEFAULT_PRECIS),
                new WinDef.DWORD(antialiased ? ExtendedGDI32.DEFAULT_QUALITY : ExtendedGDI32.NONANTIALIASED_QUALITY),
                new WinDef.DWORD(
                        ExtendedGDI32.DEFAULT_PITCH | ExtendedGDI32.FF_SWISS), fontName);
    }

    private static HintMeshDraw hintMeshDraw(Screen screen, int windowWidth,
                                             int windowHeight, List<Hint> windowHints,
                                             List<Key> focusedHintKeySequence,
                                             double highlightFontScale,
                                             int boxBorderThickness,
                                             WinDef.HFONT normalFont,
                                             WinDef.HFONT largeFont, WinDef.HDC hdcTemp) {
        List<WinDef.RECT> boxRects = new ArrayList<>();
        List<WinDef.RECT> cellRects = new ArrayList<>();
        List<HintText> hintTexts = new ArrayList<>();
        double maxHintCenterX = Double.MIN_VALUE;
        double maxHintCenterY = Double.MIN_VALUE;
        for (Hint hint : windowHints) {
            if (!hint.startsWith(focusedHintKeySequence))
                continue;
            maxHintCenterX = Math.max(maxHintCenterX, hint.centerX());
            maxHintCenterY = Math.max(maxHintCenterY, hint.centerY());
        }
        for (Hint hint : windowHints) {
            if (!hint.startsWith(focusedHintKeySequence))
                continue;
            String text = hint.keySequence()
                              .stream()
                              .map(Key::hintLabel)
                              .collect(Collectors.joining());
            String prefixText;
            prefixText = focusedHintKeySequence.isEmpty() ? "" :
                    focusedHintKeySequence.stream()
                                          .map(Key::hintLabel)
                                          .collect(Collectors.joining());
            String highlightText;
            if (highlightFontScale == 1)
                highlightText = "";
            else
                highlightText = prefixText.length() == text.length() ? "" :
                        text.substring(prefixText.length(), prefixText.length() + 1);
            String suffixText = prefixText.length() == text.length() ? "" :
                    text.substring(prefixText.length() + highlightText.length());
            // Measure text size.
            WinNT.HANDLE largeFont0 = text.length() == 1 ? normalFont : largeFont;
            GDI32.INSTANCE.SelectObject(hdcTemp, largeFont0);
            WinUser.SIZE highlightTextSize = new WinUser.SIZE();
            if (!highlightText.isEmpty()) {
                ExtendedGDI32.INSTANCE.GetTextExtentPoint32A(hdcTemp, highlightText,
                        highlightText.length(),
                        highlightTextSize);
            }
            GDI32.INSTANCE.SelectObject(hdcTemp, normalFont);
            WinUser.SIZE prefixTextSize = new WinUser.SIZE();
            if (!prefixText.isEmpty()) {
                ExtendedGDI32.INSTANCE.GetTextExtentPoint32A(hdcTemp, prefixText,
                        prefixText.length(),
                        prefixTextSize);
            }
            WinUser.SIZE suffixTextSize = new WinUser.SIZE();
            if (!suffixText.isEmpty()) {
                ExtendedGDI32.INSTANCE.GetTextExtentPoint32A(hdcTemp, suffixText,
                        suffixText.length(),
                        suffixTextSize);
            }
            int textWidth = prefixTextSize.cx + highlightTextSize.cx + suffixTextSize.cx;
            int normalTextHeight = !prefixText.isEmpty() ? prefixTextSize.cy :
                    suffixTextSize.cy;
            int largeTextHeight = !highlightText.isEmpty() ? highlightTextSize.cy : normalTextHeight;
            int textX = (int) hint.centerX() - screen.rectangle().x() - textWidth / 2;
            int largeTextY =
                    (int) hint.centerY() - screen.rectangle().y() - largeTextHeight / 2;
            int normalTextY =
                    (int) hint.centerY() - screen.rectangle().y() - normalTextHeight / 2;
//            int textY = largeTextY + (largeTextSize.cy/2 - textSize.cy/2 + (largeTextSize.cy-textSize.cy)/4);
            // Try to minimize the size of the box. Looks good with Consolas 10.
            int xPadding = (int) (screen.scale() * 1);
            int yPadding = (int) (screen.scale() * -1) * 4;
            WinDef.RECT boxRect = new WinDef.RECT();
            int scaledBoxBorderThickness = scaledPixels(boxBorderThickness, screen.scale());
            boxRect.left = textX - xPadding + scaledBoxBorderThickness;
            boxRect.top = largeTextY - yPadding + scaledBoxBorderThickness;
            boxRect.right = textX + prefixTextSize.cx + highlightTextSize.cx + suffixTextSize.cx + xPadding
                            - (hint.centerX() < maxHintCenterX ? 0 : scaledBoxBorderThickness);
            boxRect.bottom = largeTextY + largeTextHeight + yPadding
                             - (hint.centerY() < maxHintCenterY ? 0 : scaledBoxBorderThickness);
            boolean isHintPartOfGrid = hint.cellWidth() != -1;
            if (!isHintPartOfGrid) {
                // Position history hints have no cellWidth/cellHeight.
                hint = new Hint(hint.centerX(), hint.centerY(),
                        textWidth, largeTextHeight, hint.keySequence());
            }
            WinDef.RECT cellRect = new WinDef.RECT();
            cellRect.left = boxRect.left - scaledBoxBorderThickness;
            cellRect.top = boxRect.top - scaledBoxBorderThickness;
            cellRect.right = boxRect.right + (hint.centerX() < maxHintCenterX ? 0 : scaledBoxBorderThickness);
            cellRect.bottom = boxRect.bottom + (hint.centerY() < maxHintCenterY ? 0 : scaledBoxBorderThickness);
            setBoxOrCellRect(boxRect, screen, boxBorderThickness,
                    hint,
                    maxHintCenterX, maxHintCenterY, windowWidth, windowHeight,
                    isHintPartOfGrid);
            setBoxOrCellRect(cellRect, screen, 0, hint,
                    maxHintCenterX, maxHintCenterY, windowWidth, windowHeight,
                    isHintPartOfGrid);
            cellRects.add(cellRect); // TODO only if between box is non transparent
            boxRects.add(boxRect);
            WinDef.RECT prefixRect;
            if (prefixText.isEmpty())
                prefixRect = null;
            else
                prefixRect = textRect(textX, normalTextY, prefixTextSize);
            WinDef.RECT highlightRect;
            if (highlightText.isEmpty())
                highlightRect = null;
            else
                highlightRect = textRect(textX + prefixTextSize.cx, largeTextY,
                        highlightTextSize);
            WinDef.RECT suffixRect;
            if (suffixText.isEmpty())
                suffixRect = null;
            else
                suffixRect =
                        textRect(textX + prefixTextSize.cx + highlightTextSize.cx,
                                normalTextY,
                                suffixTextSize);
            hintTexts.add(new HintText(prefixRect, prefixText,
                    highlightRect, highlightText,
                    suffixRect, suffixText));
        }
        return new HintMeshDraw(boxRects, cellRects, hintTexts);
    }

    private static void setBoxOrCellRect(WinDef.RECT boxRect, Screen screen, int boxBorderThickness,
                                         Hint hint,
                                         double maxHintCenterX, double maxHintCenterY,
                                         int windowWidth, int windowHeight,
                                         boolean avoidDoubleEdge) {
        double scaledBoxBorderThickness = scaledPixels(boxBorderThickness, screen.scale());
        double cellWidth = hint.cellWidth();
        double halfCellWidth = cellWidth / 2  - scaledBoxBorderThickness;
        double cellHeight = hint.cellHeight();
        double halfCellHeight = cellHeight / 2 - scaledBoxBorderThickness;
        int boxLeft = (int) Math.round(hint.centerX() - halfCellWidth) - screen.rectangle().x();
        int boxTop = (int) Math.round(hint.centerY() - halfCellHeight) - screen.rectangle().y();
        int boxRight = (int) Math.round(hint.centerX() + halfCellWidth) - screen.rectangle().x();
        // Put back the boxBorderThickness if there is a box above with shared edge to avoid double edge.
        if (avoidDoubleEdge && hint.centerX() < maxHintCenterX)
            boxRight += scaledBoxBorderThickness;
        int boxBottom = (int) Math.round(hint.centerY() + halfCellHeight) - screen.rectangle().y();
        if (avoidDoubleEdge && hint.centerY() < maxHintCenterY)
            boxBottom += scaledBoxBorderThickness;
        if (windowWidth - (boxRight - screen.rectangle().x() + scaledBoxBorderThickness) == 1)
            // This can happen because of the rounding in boxLeft = Math.round(...) (?)
            boxRight++;
        if (windowHeight - (boxBottom - screen.rectangle().y() + scaledBoxBorderThickness) == 1)
            boxBottom++;
        if (boxLeft < boxRect.left || boxTop < boxRect.top
            || boxRight > boxRect.right || boxBottom > boxRect.bottom) {
            // Screen selection hint are bigger.
            boxRect.left = boxLeft;
            boxRect.top = boxTop;
            boxRect.right = boxRight;
            boxRect.bottom = boxBottom;
        }
    }

    private static WinDef.RECT textRect(int textX, int textY, WinUser.SIZE textSize) {
        WinDef.RECT textRect = new WinDef.RECT();
        textRect.left = textX;
        textRect.top = textY;
        textRect.right = textRect.left + textSize.cx;
        textRect.bottom = textRect.top + textSize.cy;
        return textRect;
    }

    private static void drawHintText(WinDef.HDC hdc, WinDef.RECT textRect, String text,
                                     DibSection dibSection, int windowWidth,
                                     int fontColorInt) {
        ExtendedGDI32.INSTANCE.SetTextColor(hdc, fontColorInt);
        ExtendedGDI32.INSTANCE.SetBkMode(hdc, ExtendedGDI32.TRANSPARENT);
        ExtendedUser32.INSTANCE.DrawText(hdc, text, -1, textRect,
                new WinDef.UINT(ExtendedGDI32.DT_SINGLELINE | ExtendedGDI32.DT_LEFT | ExtendedGDI32.DT_TOP | ExtendedGDI32.DT_NOPREFIX));
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
        return ((int) (red * opacity) << 16) | ((int) (green * opacity) << 8) |
               (int) (blue * opacity);
    }

    public static void setIndicator(Indicator indicator) {
        Objects.requireNonNull(indicator);
        if (showingIndicator && currentIndicator != null &&
            currentIndicator.equals(indicator))
            return;
        Indicator oldIndicator = currentIndicator;
        currentIndicator = indicator;
        if (indicatorWindow == null) {
            createIndicatorWindow(indicator.size());
        }
        else if (indicator.size() != oldIndicator.size()) {
            Screen screen = WindowsScreen.findActiveScreen(new WinDef.POINT(0, 0));
            int scaledIndicatorSize = scaledPixels(indicator.size(), screen.scale());
            User32.INSTANCE.SetWindowPos(indicatorWindow.hwnd(), null, 0, 0,
                    scaledIndicatorSize + 1, scaledIndicatorSize + 1,
                    User32.SWP_NOMOVE | User32.SWP_NOZORDER);
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
        currentZoom = zoom;
        if (currentZoom == null)
            User32.INSTANCE.ShowWindow(zoomWindow.hostHwnd(), WinUser.SW_HIDE);
        else {
            if (!Magnification.INSTANCE.MagSetWindowTransform(zoomWindow.hwnd(),
                    new Magnification.MAGTRANSFORM.ByReference((float) currentZoom.percent())))
                logger.error("Failed MagSetWindowTransform: " +
                             Integer.toHexString(Native.getLastError()));
            Screen screen = WindowsScreen.findActiveScreen(new WinDef.POINT(zoom.center().x(),
                    zoom.center().y()));
            User32.INSTANCE.SetWindowPos(zoomWindow.hostHwnd(), null,
                    screen.rectangle().x(), screen.rectangle().y(),
                    screen.rectangle().width(), screen.rectangle().height(),
                    User32.SWP_NOMOVE | User32.SWP_NOZORDER);
            User32.INSTANCE.SetWindowPos(zoomWindow.hwnd(), null,
                    screen.rectangle().x(), screen.rectangle().y(),
                    screen.rectangle().width(), screen.rectangle().height(),
                    User32.SWP_NOMOVE | User32.SWP_NOZORDER);
        }
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
        switch (uMsg) {
            case WinUser.WM_PAINT:
//                ExtendedUser32.PAINTSTRUCT ps = new ExtendedUser32.PAINTSTRUCT();
//                WinDef.HDC hdc = ExtendedUser32.INSTANCE.BeginPaint(hwnd, ps);
//                clearWindow(hdc, ps.rcPaint, 0);
//                if (showingIndicator) {
//                    clearWindow(hdc, ps.rcPaint,
//                            hexColorStringToInt(currentIndicator.hexColor()));
//                }
//                ExtendedUser32.INSTANCE.EndPaint(hwnd, ps);
                break;
        }
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
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values())
            requestWindowRepaint(hintMeshWindow.hwnd);
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
        Screen activeScreen = WindowsScreen.findActiveScreen(mousePosition);
        int scaledIndicatorSize =
                scaledPixels(currentIndicator.size(), activeScreen.scale());
        MouseSize mouseSize = WindowsMouse.mouseSize();
        User32.INSTANCE.MoveWindow(indicatorWindow.hwnd,
                bestIndicatorX(mousePosition.x, mouseSize.width(),
                        activeScreen.rectangle(), scaledIndicatorSize),
                bestIndicatorY(mousePosition.y, mouseSize.height(),
                        activeScreen.rectangle(), scaledIndicatorSize),
                scaledIndicatorSize + 1, scaledIndicatorSize + 1, false);
    }

    public static boolean doesFontExist(String fontName) {
        ExtendedGDI32.EnumFontFamExProc fontEnumProc = new ExtendedGDI32.EnumFontFamExProc() {
            public int callback(ExtendedGDI32.LOGFONT lpelfe, ExtendedGDI32.TEXTMETRIC lpntme, WinDef.DWORD FontType, WinDef.LPARAM lParam) {
                int nameLength = 0;
                for (int i = 0; i < lpelfe.lfFaceName.length; i++) {
                    if (lpelfe.lfFaceName[i] == 0) {
                        nameLength = i;
                        break;
                    }
                }
                String faceName = new String(lpelfe.lfFaceName, 0, nameLength);
                if (fontName.equalsIgnoreCase(faceName)) {
                    // Font found
                    return 0; // Return 0 to stop enumeration
                }
                return 1; // Continue enumeration
            }
        };
        WinDef.HDC hdc = User32.INSTANCE.GetDC(null);
        ExtendedGDI32.LOGFONT logfont = new ExtendedGDI32.LOGFONT();
        logfont.lfCharSet = ExtendedGDI32.DEFAULT_CHARSET;
        byte[] fontBytes = fontName.getBytes();
        System.arraycopy(fontBytes, 0, logfont.lfFaceName, 0,
                Math.min(fontBytes.length, logfont.lfFaceName.length - 1));
        // lfFaceName[this.lfFaceName.length - 1] is 0, it is the null-terminator.
        boolean fontExists =
                ExtendedGDI32.INSTANCE.EnumFontFamiliesExA(hdc, logfont, fontEnumProc,
                        new WinDef.LPARAM(0), new WinDef.DWORD(0)) == 0;
        User32.INSTANCE.ReleaseDC(null, hdc);
        return fontExists;
    }

}