package jmouseable.jmouseable;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.*;
import jmouseable.jmouseable.Grid.GridBuilder;
import jmouseable.jmouseable.WindowsMouse.CursorPositionAndSize;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static jmouseable.jmouseable.ExtendedGDI32.*;

public class WindowsOverlay {

    private static final int indicatorEdgeThreshold = 100; // in pixels
    private static final int indicatorSize = 16;

    private static WinDef.HWND indicatorWindowHwnd;
    private static GridWindow gridWindow;
    private static boolean showingIndicator;
    private static String currentIndicatorHexColor;
    private static boolean showingGrid;
    private static Grid currentGrid;

    public static GridBuilder gridFittingActiveWindow(GridBuilder grid,
                                                      double windowWidthPercent,
                                                      double windowHeightPercent) {
        WinDef.HWND foregroundWindow = User32.INSTANCE.GetForegroundWindow();
        // https://stackoverflow.com/a/65605845
        WinDef.RECT excludeShadow = windowRectExcludingShadow(foregroundWindow);
        int windowWidth = excludeShadow.right - excludeShadow.left;
        int windowHeight = excludeShadow.bottom - excludeShadow.top;
        int gridWidth = (int) (windowWidth * windowWidthPercent);
        int gridHeight = (int) (windowHeight * windowHeightPercent);
        return grid.x(excludeShadow.left + (windowWidth - gridWidth) / 2)
                   .y(excludeShadow.top + (windowHeight - gridHeight) / 2)
                   .width(gridWidth)
                   .height(gridHeight);
    }

    private static WinDef.RECT windowRectExcludingShadow(WinDef.HWND hwnd) {
        // On Windows 10+, DwmGetWindowAttribute() returns the extended frame bounds excluding shadow.
        WinDef.RECT rect = new WinDef.RECT();
        Dwmapi.INSTANCE.DwmGetWindowAttribute(hwnd, Dwmapi.DWMWA_EXTENDED_FRAME_BOUNDS,
                rect, rect.size());
        return rect;
    }

    public static void setTopmost() {
        if (indicatorWindowHwnd != null)
            setWindowTopmost(indicatorWindowHwnd);
        if (gridWindow != null)
            setWindowTopmost(gridWindow.hwnd);
    }

    private static void setWindowTopmost(WinDef.HWND hwnd) {
        User32.INSTANCE.SetWindowPos(hwnd, ExtendedUser32.HWND_TOPMOST, 0, 0, 0, 0,
                WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE);
    }

    private record GridWindow(WinDef.HWND hwnd) {

    }

    private static int bestIndicatorX(int mouseX, int cursorWidth, int monitorLeft,
                                      int monitorRight) {
        mouseX = Math.min(monitorRight, Math.max(monitorLeft, mouseX));
        boolean isNearLeftEdge = mouseX <= (monitorLeft + indicatorEdgeThreshold);
        boolean isNearRightEdge = mouseX >= (monitorRight - indicatorEdgeThreshold);
        if (isNearRightEdge)
            return mouseX - indicatorSize;
        return mouseX + cursorWidth / 2;
    }

    private static int bestIndicatorY(int mouseY, int cursorHeight, int monitorTop,
                                      int monitorBottom) {
        mouseY = Math.min(monitorBottom, Math.max(monitorTop, mouseY));
        boolean isNearBottomEdge = mouseY >= (monitorBottom - indicatorEdgeThreshold);
        boolean isNearTopEdge = mouseY <= (monitorTop + indicatorEdgeThreshold);
        if (isNearBottomEdge)
            return mouseY - indicatorSize;
        return mouseY + cursorHeight / 2;
    }

    private static void createIndicatorWindow() {
        CursorPositionAndSize cursorPositionAndSize =
                WindowsMouse.cursorPositionAndSize();
        WinUser.MONITORINFO monitorInfo =
                WindowsMonitor.activeMonitorInfo(cursorPositionAndSize.position());
        indicatorWindowHwnd = createWindow("Indicator",
                bestIndicatorX(cursorPositionAndSize.position().x,
                        cursorPositionAndSize.width(), monitorInfo.rcMonitor.left,
                        monitorInfo.rcMonitor.right),
                bestIndicatorY(cursorPositionAndSize.position().y,
                        cursorPositionAndSize.height(), monitorInfo.rcMonitor.top,
                        monitorInfo.rcMonitor.bottom), indicatorSize, indicatorSize,
                WindowsOverlay::indicatorWindowCallback);
    }

    private static void createGridWindow(int x, int y, int width, int height) {
        WinDef.HWND hwnd = createWindow("Grid", x, y, width, height,
                WindowsOverlay::gridWindowCallback);
        gridWindow = new GridWindow(hwnd);
    }

    private static WinDef.HWND createWindow(String windowName, int windowX, int windowY,
                                            int windowWidth, int windowHeight,
                                            WinUser.WindowProc windowCallback) {
        WinUser.WNDCLASSEX wClass = new WinUser.WNDCLASSEX();
        wClass.hbrBackground = null;
        wClass.lpszClassName = "JMouseable" + windowName + "ClassName";
        wClass.lpfnWndProc = windowCallback;
        WinDef.ATOM registerClassExResult = User32.INSTANCE.RegisterClassEx(wClass);
        WinDef.HWND hwnd = User32.INSTANCE.CreateWindowEx(
                User32.WS_EX_TOPMOST | ExtendedUser32.WS_EX_TOOLWINDOW | ExtendedUser32.WS_EX_NOACTIVATE
                | ExtendedUser32.WS_EX_LAYERED | ExtendedUser32.WS_EX_TRANSPARENT,
                wClass.lpszClassName, "JMouseable" + windowName + "WindowName",
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
                            hexColorStringToInt(currentIndicatorHexColor));
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
                ExtendedUser32.PAINTSTRUCT ps = new ExtendedUser32.PAINTSTRUCT();
                if (!showingGrid) {
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
                if (currentGrid.lineVisible())
                    drawGridLines(memDC, ps.rcPaint);
                if (currentGrid.hintEnabled())
                    drawGridHints(memDC, ps.rcPaint);
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

    private static void drawGridLines(WinDef.HDC hdc, WinDef.RECT windowRect) {
        int rowCount = currentGrid.rowCount();
        int columnCount = currentGrid.columnCount();
        int cellWidth = currentGrid.width() / rowCount;
        int cellHeight = currentGrid.height() / columnCount;
        int[] polyCounts = new int[rowCount + 1 + columnCount + 1];
        WinDef.POINT[] points =
                (WinDef.POINT[]) new WinDef.POINT().toArray(polyCounts.length * 2);
        int lineThickness = currentGrid.lineThickness();
        // Vertical lines
        for (int lineIndex = 0; lineIndex <= columnCount; lineIndex++) {
            int x = lineIndex == columnCount ? windowRect.right :
                    lineIndex * cellWidth;
            if (x == windowRect.left)
                x += lineThickness / 2;
            else if (x == windowRect.right)
                x -= lineThickness / 2 + lineThickness % 2;
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
                y += lineThickness / 2;
            else if (y == windowRect.bottom)
                y -= lineThickness / 2 + lineThickness % 2;
            points[pointsOffset + 2 * lineIndex].x = 0;
            points[pointsOffset + 2 * lineIndex].y = y;
            points[pointsOffset + 2 * lineIndex + 1].x = currentGrid.width();
            points[pointsOffset + 2 * lineIndex + 1].y = y;
            polyCounts[polyCountsOffset + lineIndex] = 2;
        }
        String lineColor = currentGrid.lineHexColor();
        WinUser.HPEN gridPen =
                ExtendedGDI32.INSTANCE.CreatePen(ExtendedGDI32.PS_SOLID, lineThickness,
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

    private static void drawGridHints(WinDef.HDC hdc, WinDef.RECT windowRect) {
        int rowCount = currentGrid.rowCount();
        int columnCount = currentGrid.columnCount();
        int cellWidth = currentGrid.width() / rowCount;
        int cellHeight = currentGrid.height() / columnCount;
        String fontName = currentGrid.hintFontName();
        int fontSize = currentGrid.hintFontSize();
        String fontHexColor = currentGrid.hintFontHexColor();
        String boxHexColor = currentGrid.hintBoxHexColor();
        Hint[][] hints = currentGrid.hints();
        List<Key> focusedHintKeySequence = currentGrid.focusedHintKeySequence();
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                Hint hint = hints[rowIndex][columnIndex];
                if (!hint.startsWith(focusedHintKeySequence))
                    continue;
                // Convert point size to logical units.
                // 1 point = 1/72 inch. So, multiply by dpi and divide by 72 to convert to pixels.
                int fontHeight =
                        -fontSize * GDI32.INSTANCE.GetDeviceCaps(hdc, LOGPIXELSY) / 72;
                // In Windows API, negative font size means "point size" (as opposed to pixels).
                WinDef.HFONT hintFont = ExtendedGDI32.INSTANCE.CreateFontA(
                        fontHeight, 0, 0, 0,
                        FW_BOLD,
                        new WinDef.DWORD(0),
                        new WinDef.DWORD(0),
                        new WinDef.DWORD(0),
                        new WinDef.DWORD(ANSI_CHARSET),
                        new WinDef.DWORD(OUT_DEFAULT_PRECIS),
                        new WinDef.DWORD(CLIP_DEFAULT_PRECIS),
                        new WinDef.DWORD(DEFAULT_QUALITY),
                        new WinDef.DWORD(DEFAULT_PITCH | FF_SWISS),
                        fontName);
                WinNT.HANDLE oldFont = GDI32.INSTANCE.SelectObject(hdc, hintFont);
                // Measure text size
                WinUser.SIZE textSize = new WinUser.SIZE();
                String text = hint.keySequence()
                                  .stream()
                                  .map(Key::name)
                                  .map(String::toUpperCase) // This could be problematic since it uses Locale.default().
                                  .collect(Collectors.joining());
                ExtendedGDI32.INSTANCE.GetTextExtentPoint32A(hdc, text, text.length(), textSize);
                // Calculate text and box positions.
                int textX = columnIndex * cellWidth + (cellWidth - textSize.cx) / 2;
                int textY = rowIndex * cellHeight + (cellHeight - textSize.cy) / 2;
                int xPadding = (int) (0.2d * textSize.cx);
                int yPadding = (int) (0.1d * textSize.cy);
                int boxLeft = textX - xPadding;
                int boxTop = textY - yPadding;
                int boxRight = textX + textSize.cx + xPadding;
                int boxBottom = textY + textSize.cy + yPadding;
                // Draw background box.
                WinDef.HBRUSH boxBrush = ExtendedGDI32.INSTANCE.CreateSolidBrush(
                        hexColorStringToInt(boxHexColor));
                WinDef.RECT boxRect = new WinDef.RECT();
                boxRect.left = boxLeft;
                boxRect.top = boxTop;
                boxRect.right = boxRight;
                boxRect.bottom = boxBottom;
                ExtendedUser32.INSTANCE.FillRect(hdc, boxRect, boxBrush);
                // Draw text.
                ExtendedGDI32.INSTANCE.SetTextColor(hdc, hexColorStringToInt(fontHexColor));
                ExtendedGDI32.INSTANCE.SetBkMode(hdc, TRANSPARENT);
                WinDef.RECT textRect = new WinDef.RECT();
                textRect.left = textX;
                textRect.top = textY;
                textRect.right = textX + textSize.cx; // Not strictly necessary for DT_SINGLELINE
                textRect.bottom = textY + textSize.cy; // Not strictly necessary for DT_SINGLELINE
                ExtendedUser32.INSTANCE.DrawText(hdc, text, -1, textRect,
                        new WinDef.UINT(DT_SINGLELINE | DT_CENTER | DT_VCENTER));
                // Clean up.
                GDI32.INSTANCE.SelectObject(hdc, oldFont);
                GDI32.INSTANCE.DeleteObject(hintFont);
                GDI32.INSTANCE.DeleteObject(boxBrush);
            }
        }
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

    public static void setIndicatorColor(String hexColor) {
        Objects.requireNonNull(hexColor);
        if (showingIndicator && currentIndicatorHexColor != null &&
            currentIndicatorHexColor.equals(hexColor))
            return;
        currentIndicatorHexColor = hexColor;
        if (indicatorWindowHwnd == null)
            createIndicatorWindow();
        showingIndicator = true;
        requestWindowRepaint(indicatorWindowHwnd);
    }

    public static void hideIndicator() {
        if (!showingIndicator)
            return;
        showingIndicator = false;
        requestWindowRepaint(indicatorWindowHwnd);
    }

    public static void setGrid(Grid grid) {
        Objects.requireNonNull(grid);
        if (showingGrid && currentGrid != null && currentGrid.equals(grid))
            return;
        Grid oldGrid = currentGrid;
        currentGrid = grid;
        if (gridWindow == null)
            createGridWindow(currentGrid.x(), currentGrid.y(), currentGrid.width(),
                    currentGrid.height());
        else {
            if (grid.x() != oldGrid.x() || grid.y() != oldGrid.y() ||
                grid.width() != oldGrid.width() || grid.height() != oldGrid.height()) {
                User32.INSTANCE.SetWindowPos(gridWindow.hwnd(),
                        ExtendedUser32.HWND_TOPMOST, grid.x(), grid.y(), grid.width(),
                        grid.height(), 0);
            }
        }
        showingGrid = true;
        requestWindowRepaint(gridWindow.hwnd);
    }

    public static void hideGrid() {
        if (!showingGrid)
            return;
        showingGrid = false;
        requestWindowRepaint(gridWindow.hwnd);
    }

    private static void requestWindowRepaint(WinDef.HWND hwnd) {
        User32.INSTANCE.InvalidateRect(hwnd, null, true);
        User32.INSTANCE.UpdateWindow(hwnd);
    }

    static void mouseMoved(WinDef.POINT mousePosition) {
        WinUser.MONITORINFO monitorInfo = WindowsMonitor.activeMonitorInfo(mousePosition);
        CursorPositionAndSize cursorPositionAndSize =
                WindowsMouse.cursorPositionAndSize();
        User32.INSTANCE.MoveWindow(indicatorWindowHwnd,
                bestIndicatorX(mousePosition.x, cursorPositionAndSize.width(),
                        monitorInfo.rcMonitor.left, monitorInfo.rcMonitor.right),
                bestIndicatorY(mousePosition.y, cursorPositionAndSize.height(),
                        monitorInfo.rcMonitor.top, monitorInfo.rcMonitor.bottom),
                indicatorSize, indicatorSize, false);
    }

    public static boolean doesFontExist(String fontName) {
        ExtendedGDI32.EnumFontFamExProc fontEnumProc = new ExtendedGDI32.EnumFontFamExProc() {
            public int callback(LOGFONT lpelfe, TEXTMETRIC lpntme, WinDef.DWORD FontType, WinDef.LPARAM lParam) {
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
        LOGFONT logfont = new LOGFONT();
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