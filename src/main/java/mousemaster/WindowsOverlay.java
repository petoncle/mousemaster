package mousemaster;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.*;
import mousemaster.WindowsMouse.MouseSize;

import java.util.*;
import java.util.stream.Collectors;

public class WindowsOverlay {

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
        for (HintMeshWindow hintMeshWindow : hintMeshWindows.values())
            hwnds.add(hintMeshWindow.hwnd);
        if (indicatorWindow != null)
            hwnds.add(indicatorWindow.hwnd);
        if (gridWindow != null)
            hwnds.add(gridWindow.hwnd);
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

    private record IndicatorWindow(WinDef.HWND hwnd, WinUser.WindowProc callback) {

    }

    private record GridWindow(WinDef.HWND hwnd, WinUser.WindowProc callback) {

    }

    private record HintMeshWindow(WinDef.HWND hwnd, WinUser.WindowProc callback, List<Hint> hints) {

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
        indicatorWindow = new IndicatorWindow(hwnd, callback);
    }

    private static int scaledPixels(int originalInPixels, double scale) {
        return (int) Math.ceil(originalInPixels * scale);
    }

    private static void createGridWindow(int x, int y, int width, int height) {
        WinUser.WindowProc callback = WindowsOverlay::gridWindowCallback;
        WinDef.HWND hwnd =
                createWindow("Grid" + (gridWindow == null ? 1 : 2), x, y, width, height,
                        callback);
        gridWindow = new GridWindow(hwnd, callback);
    }

    private static void createOrUpdateHintMeshWindows(List<Hint> hints) {
        Set<Screen> screens = WindowsScreen.findScreens();
        Map<Screen, List<Hint>> hintsByScreen = new HashMap<>();
        for (Hint hint : hints) {
            for (Screen screen : screens) {
                if (!Rectangle.rectangleContains(screen.rectangle().x(),
                        screen.rectangle().y(), screen.rectangle().width(),
                        screen.rectangle().height(), hint.centerX(), hint.centerY()))
                    continue;
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
        for (Map.Entry<Screen, List<Hint>> entry : hintsByScreen.entrySet()) {
            Screen screen = entry.getKey();
            List<Hint> hintsInScreen = entry.getValue();
            HintMeshWindow existingWindow = hintMeshWindows.get(screen);
            if (existingWindow == null) {
                WinUser.WindowProc callback = WindowsOverlay::hintMeshWindowCallback;
                WinDef.HWND hwnd = createWindow("HintMesh", screen.rectangle().x(),
                        screen.rectangle().y(), screen.rectangle().width() + 1,
                        screen.rectangle().height() + 1, callback);
                hintMeshWindows.put(screen,
                        new HintMeshWindow(hwnd, callback, hintsInScreen));
            }
            else {
                hintMeshWindows.put(screen,
                        new HintMeshWindow(existingWindow.hwnd, existingWindow.callback,
                                hintsInScreen));
            }
        }
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
        User32.INSTANCE.SetLayeredWindowAttributes(hwnd, 0, (byte) 0,
                WinUser.LWA_COLORKEY);
        User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_SHOWNORMAL);
        return hwnd;
    }

    private static WinDef.LRESULT indicatorWindowCallback(WinDef.HWND hwnd, int uMsg,
                                                          WinDef.WPARAM wParam,
                                                          WinDef.LPARAM lParam) {
        switch (uMsg) {
            case WinUser.WM_PAINT:
                ExtendedUser32.PAINTSTRUCT ps = new ExtendedUser32.PAINTSTRUCT();
                WinDef.HDC hdc = ExtendedUser32.INSTANCE.BeginPaint(hwnd, ps);
                clearWindow(hdc, ps.rcPaint);
                if (showingIndicator) {
                    WinDef.HBRUSH hbrBackground = ExtendedGDI32.INSTANCE.CreateSolidBrush(
                            hexColorStringToInt(currentIndicator.hexColor()));
                    ExtendedUser32.INSTANCE.FillRect(hdc, ps.rcPaint, hbrBackground);
                    GDI32.INSTANCE.DeleteObject(hbrBackground);
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
                    clearWindow(hdc, ps.rcPaint);
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
                clearWindow(memDC, ps.rcPaint);
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
                if (hintMeshWindow == null)
                    throw new IllegalStateException();
                if (!showingHintMesh) {
                    WinDef.HDC hdc = ExtendedUser32.INSTANCE.BeginPaint(hwnd, ps);
                    // The area has to be cleared otherwise the previous drawings will be drawn.
                    clearWindow(hdc, ps.rcPaint);
                    ExtendedUser32.INSTANCE.EndPaint(hwnd, ps);
                    break;
                }
                WinDef.HDC hdc = ExtendedUser32.INSTANCE.BeginPaint(hwnd, ps);
                WinDef.HDC memDC = GDI32.INSTANCE.CreateCompatibleDC(hdc);
                // We may want to use the window's full dimension (GetClientRect) instead of rcPaint?
                int width = ps.rcPaint.right - ps.rcPaint.left;
                int height = ps.rcPaint.bottom - ps.rcPaint.top;
                WinDef.HBITMAP hBitmap =
                        GDI32.INSTANCE.CreateCompatibleBitmap(hdc, width, height);
                WinNT.HANDLE oldBitmap = GDI32.INSTANCE.SelectObject(memDC, hBitmap);
                clearWindow(memDC, ps.rcPaint);
                drawHints(memDC, ps.rcPaint, screen, hintMeshWindow.hints);
                // Copy (blit) the off-screen buffer to the screen.
                GDI32.INSTANCE.BitBlt(hdc, 0, 0, width, height, memDC, 0, 0,
                        GDI32.SRCCOPY);
                GDI32.INSTANCE.SelectObject(memDC, oldBitmap);
                GDI32.INSTANCE.DeleteObject(hBitmap);
                GDI32.INSTANCE.DeleteDC(memDC);
                ExtendedUser32.INSTANCE.EndPaint(hwnd, ps);
                break;
        }
        return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
    }

    private static void clearWindow(WinDef.HDC hdc, WinDef.RECT windowRect) {
        WinDef.HBRUSH hbrBackground = ExtendedGDI32.INSTANCE.CreateSolidBrush(0);
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

    private static void drawHints(WinDef.HDC hdc, WinDef.RECT windowRect, Screen screen,
                                  List<Hint> windowHints) {
        String fontName = currentHintMesh.fontName();
        int fontSize = currentHintMesh.fontSize();
        String fontHexColor = currentHintMesh.fontHexColor();
        String selectedPrefixFontHexColor = currentHintMesh.selectedPrefixFontHexColor();
        String boxHexColor = currentHintMesh.boxHexColor();
        List<Key> focusedHintKeySequence = currentHintMesh.focusedKeySequence();
        int scaledDpi = (int) (screen.dpi() * screen.scale());
        // Convert point size to logical units.
        // 1 point = 1/72 inch. So, multiply by scaledDpi and divide by 72 to convert to pixels.
        int fontHeight = -fontSize * scaledDpi / 72;
        // In Windows API, negative font size means "point size" (as opposed to pixels).
        WinDef.HFONT hintFont =
                ExtendedGDI32.INSTANCE.CreateFontA(fontHeight, 0, 0, 0, ExtendedGDI32.FW_BOLD,
                        new WinDef.DWORD(0), new WinDef.DWORD(0), new WinDef.DWORD(0),
                        new WinDef.DWORD(ExtendedGDI32.ANSI_CHARSET),
                        new WinDef.DWORD(ExtendedGDI32.OUT_DEFAULT_PRECIS),
                        new WinDef.DWORD(ExtendedGDI32.CLIP_DEFAULT_PRECIS),
                        new WinDef.DWORD(ExtendedGDI32.DEFAULT_QUALITY),
                        new WinDef.DWORD(
                                ExtendedGDI32.DEFAULT_PITCH | ExtendedGDI32.FF_SWISS), fontName);
        WinNT.HANDLE oldFont = GDI32.INSTANCE.SelectObject(hdc, hintFont);
        WinDef.HBRUSH boxBrush =
                ExtendedGDI32.INSTANCE.CreateSolidBrush(hexColorStringToInt(boxHexColor));
        for (Hint hint : windowHints) {
            if (!hint.startsWith(focusedHintKeySequence))
                continue;
            // Measure text size
            WinUser.SIZE textSize = new WinUser.SIZE();
            String text = hint.keySequence()
                              .stream()
                              .map(Key::hintLabel)
                              .collect(Collectors.joining());
            ExtendedGDI32.INSTANCE.GetTextExtentPoint32A(hdc, text, text.length(),
                    textSize);
            int textX = hint.centerX() - screen.rectangle().x() - textSize.cx / 2;
            int textY = hint.centerY() - screen.rectangle().y() - textSize.cy / 2;
            WinDef.RECT textRect = new WinDef.RECT();
            textRect.left = textX;
            textRect.top = textY;
            textRect.right = textX + textSize.cx;
            textRect.bottom = textY + textSize.cy;
            // Try to minimize the size of the box. Looks good with Arial 10.
            int xPadding = (int) (screen.scale() * 1);
            int yPadding = (int) (screen.scale() * -1);
            WinDef.RECT boxRect = new WinDef.RECT();
            boxRect.left = textX - xPadding;
            boxRect.top = textY - yPadding;
            boxRect.right = textX + textSize.cx + xPadding;
            boxRect.bottom = textY + textSize.cy + yPadding;
            ExtendedUser32.INSTANCE.FillRect(hdc, boxRect, boxBrush);
            drawHintText(hdc, fontHexColor, textRect, text);
            if (!focusedHintKeySequence.isEmpty()) {
                String selectedPrefixText =
                        focusedHintKeySequence.stream()
                                              .map(Key::hintLabel)
                                              .collect(Collectors.joining());
                drawHintText(hdc, selectedPrefixFontHexColor, textRect,
                        selectedPrefixText);
            }
        }
        GDI32.INSTANCE.SelectObject(hdc, oldFont);
        GDI32.INSTANCE.DeleteObject(hintFont);
        GDI32.INSTANCE.DeleteObject(boxBrush);
    }

    private static void drawHintText(WinDef.HDC hdc, String fontHexColor, WinDef.RECT textRect, String text) {
        ExtendedGDI32.INSTANCE.SetTextColor(hdc, hexColorStringToInt(fontHexColor));
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

    public static void setIndicator(Indicator indicator) {
        Objects.requireNonNull(indicator);
        if (showingIndicator && currentIndicator != null &&
            currentIndicator.equals(indicator))
            return;
        Indicator oldIndicator = currentIndicator;
        currentIndicator = indicator;
        if (indicatorWindow == null)
            createIndicatorWindow(indicator.size());
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
            createGridWindow(currentGrid.x(), currentGrid.y(), currentGrid.width() + 1,
                    currentGrid.height() + 1);
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
                    createGridWindow(currentGrid.x(), currentGrid.y(), currentGrid.width() + 1,
                            currentGrid.height() + 1);
                    standByGridCanBeHidden = false;
                }
                else {
                    GridWindow newStandByGridWindow = gridWindow;
                    gridWindow = standByGridWindow;
                    standByGridWindow = newStandByGridWindow;
                    User32.INSTANCE.SetWindowPos(gridWindow.hwnd(), null, grid.x(), grid.y(),
                            grid.width() + 1, grid.height() + 1, User32.SWP_NOZORDER);
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