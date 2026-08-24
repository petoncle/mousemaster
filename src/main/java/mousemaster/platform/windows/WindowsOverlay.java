package mousemaster.platform.windows;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import mousemaster.*;
import mousemaster.platform.Overlay;
import mousemaster.qt.*;
import mousemaster.renderer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

public class WindowsOverlay implements Overlay {

    private static final Logger logger = LoggerFactory.getLogger(WindowsOverlay.class);

    private static final Duration ZOOM_IDLE_RELEASE = Duration.ofSeconds(30);
    private static final Duration ZOOM_FRAME_INTERVAL = Duration.ofMillis(16);

    private final WindowsMouseController mouse;
    private boolean waitForZoom;
    private IndicatorRenderer indicatorRenderer;
    private WinDef.HWND indicatorHwnd;
    private boolean indicatorIsCursor;
    private IndicatorConfiguration currentCursorIndicator;
    private double currentCursorScale;
    private boolean currentCursorIncludeGlyph;
    private boolean mousePositionMissing;
    private GridRenderer gridRenderer;
    private WinDef.HWND gridHwnd;
    /** Owns no QWidget, so it can be created eagerly (no QtJambi native-load ordering). */
    private final HintMeshRenderer hintMeshRenderer;
    private WinDef.HWND zoomHwnd;
    private WinUser.WindowProc zoomWindowProc;
    private WindowsZoomRenderer zoomRenderer;
    private Zoom currentZoom;
    private boolean zoomWindowShowing;
    private double zoomIdleTimer;
    private long nextZoomFrameNanos;
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

    private void hintMeshEndAnimationEndedCallback() {
        hideHintMesh();
    }

    @Override
    public void update(double delta) {
        hintMeshRenderer.runPendingWork();
        if (gridRenderer != null)
            gridRenderer.advanceAnimationsToFirstFrame();
        if (indicatorRenderer != null)
            indicatorRenderer.advanceAnimationsToFirstFrame();
        updateZoomWindow();
        releaseZoomWhenIdle(delta);
    }

    /** A Direct3D device keeps the graphics driver and a screen worth of surfaces resident,
     *  which is far more than an occasional zoom is worth holding on to. */
    private void releaseZoomWhenIdle(double delta) {
        if (currentZoom != null) {
            zoomIdleTimer = ZOOM_IDLE_RELEASE.toSeconds();
            return;
        }
        if (zoomIdleTimer <= 0)
            return;
        zoomIdleTimer -= delta;
        if (zoomIdleTimer <= 0)
            zoomRenderer.releaseDevice();
    }

    private void updateZoomWindow() {
        if (currentZoom == null)
            return;
        // The loop iterates far more often than the screen refreshes, and every frame copies
        // and presents a screen sized image over whatever else is drawing.
        if (zoomWindowShowing && System.nanoTime() < nextZoomFrameNanos)
            return;
        if (!zoomRenderer.prepare(zoomHwnd, currentZoom.screenRectangle()))
            return;
        long before = System.nanoTime();
        if (!zoomRenderer.render(currentZoom))
            return;
        long after = System.nanoTime();
        // Presenting over a busy compositor takes tens of milliseconds and holds up the
        // keyboard with it, so give the loop as long as the frame cost before asking again.
        nextZoomFrameNanos =
                after + Math.max(ZOOM_FRAME_INTERVAL.toNanos(), after - before);
        // Not per frame: enforceTopmost issues a SetWindowPos per overlay, which flickers
        // the layered hint windows.
        if (!zoomWindowShowing) {
            zoomWindowShowing = true;
            // Drawing only queues the frame: revealing the window before it reaches the
            // screen would show what it last held, the final frame of the previous zoom.
            Dwmapi.INSTANCE.DwmFlush();
            setZoomWindowVisible(true);
            setTopmost();
        }
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
        long before = System.nanoTime();
        enforceTopmost();
        long durationMillis = (long) ((System.nanoTime() - before) / 1e6);
        if (durationMillis >= 3)
            logger.trace("Enforced topmost in " + durationMillis + "ms");
    }

    private void enforceTopmost() {
        List<WinDef.HWND> hwnds = new ArrayList<>();
        // First in the hwnds list means drawn on top.
        if (gridHwnd != null && gridRenderer.showing())
            hwnds.add(gridHwnd);
        if (hintMeshRenderer.showing())
            for (TransparentWindow window : hintMeshRenderer.windows())
                hwnds.add(hwnd(window));
        if (indicatorHwnd != null && indicatorRenderer.showing())
            hwnds.add(indicatorHwnd);
        if (zoomHwnd != null)
            hwnds.add(zoomHwnd);
        if (hwnds.isEmpty())
            return;
        if (currentZoom != null) {
            // During zoom, use relative positioning to maintain z-order.
            // Avoid SetWindowPos(hwnd, HWND_TOPMOST) which causes a DWM
            // recomposition glitch visible as a brief indicator flicker.
            for (int i = 1; i < hwnds.size(); i++)
                User32.INSTANCE.SetWindowPos(hwnds.get(i), hwnds.get(i - 1),
                        0, 0, 0, 0,
                        WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE | WinUser.SWP_NOACTIVATE);
            return;
        }
        setWindowTopmost(hwnds.getFirst());
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
            setWindowTopmost(hwnds.get(windowIndex));
    }

    private WinDef.HWND windowBelow(WinDef.HWND hwnd) {
        WinDef.HWND nextHwnd =
                User32.INSTANCE.GetWindow(hwnd, new WinDef.DWORD(User32.GW_HWNDNEXT));
        return nextHwnd;
    }

    private void setWindowTopmost(WinDef.HWND hwnd) {
        User32.INSTANCE.SetWindowPos(hwnd, ExtendedUser32.HWND_TOPMOST, 0, 0, 0, 0,
                WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE | WinUser.SWP_NOACTIVATE);
    }


    private void moveAndResizeIndicatorWindow() {
        moveAndResizeIndicatorWindow(mouse.findMousePosition());
    }

    private void moveAndResizeIndicatorWindow(WinDef.POINT mousePosition) {
        // The window is created before the first indicator is set, so that the mode the user
        // switches into does not pay for it: there is nothing to place until then.
        if (indicatorRenderer.currentIndicator() == null)
            return;
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
        if (indicatorRenderer == null)
            indicatorRenderer = new IndicatorRenderer();
        indicatorHwnd = new WinDef.HWND(new Pointer(indicatorRenderer.window().winId()));
        applyOverlayExStyles(indicatorHwnd);
        updateCaptureExclusions();
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
        // enforceTopmost skips windows that are not showing.
        setWindowTopmost(hwnd);
    }

    private Rectangle virtualDesktopBounds() {
        return Rectangle.union(WindowsScreen.findScreens()
                                            .stream()
                                            .map(Screen::rectangle)
                                            .toList());
    }

    /** The window factory the renderer uses: a styled, transparent, click-through window. */
    private TransparentWindow createStyledHintMeshWindow() {
        TransparentWindow window = new TransparentWindow();
        applyOverlayExStyles(hwnd(window));
        return window;
    }

    @Override
    public void preWarmFontsAndWindows(Set<HintMeshConfiguration> hintMeshConfigurations) {
        QtHintFont.preWarm(hintMeshConfigurations);
        hintMeshRenderer.preWarmHintMeshWindows(WindowsScreen.findScreens());
        updateCaptureExclusions();
        preWarmZoomWindow();
        if (indicatorHwnd != null)
            return;
        long before = System.nanoTime();
        createIndicatorWindow();
        indicatorRenderer.preWarm();
        logger.debug("Pre-warmed the indicator window in " +
                    (long) ((System.nanoTime() - before) / 1e6) + "ms");
    }

    /** A full-screen window appearing for the first time makes Windows rearrange how it
     *  assembles the desktop, which costs a frame. Direct3D is left to the first zoom: a
     *  device holds the graphics driver and its surfaces for as long as it lives. */
    private void preWarmZoomWindow() {
        if (zoomHwnd != null)
            return;
        long before = System.nanoTime();
        createZoomWindow();
        placeZoomWindow(
                WindowsScreen.findActiveScreen(mouse.findMousePosition()).rectangle());
        logger.debug("Pre-warmed the zoom window in " +
                     (System.nanoTime() - before) / 1_000_000 + "ms");
    }

    /** Through the alpha, never by hiding: a window at zero alpha is still drawn, so it can
     *  hold an image before it is shown. */
    private void setZoomWindowVisible(boolean visible) {
        User32.INSTANCE.SetLayeredWindowAttributes(zoomHwnd, 0,
                (byte) (visible ? 255 : 0), WinUser.LWA_ALPHA);
    }

    private void placeZoomWindow(Rectangle screenRectangle) {
        User32.INSTANCE.SetWindowPos(zoomHwnd, null, screenRectangle.x(),
                screenRectangle.y(), screenRectangle.width(), screenRectangle.height(),
                User32.SWP_NOZORDER | WinUser.SWP_SHOWWINDOW);
    }

    private void createZoomWindow() {
        WinUser.WNDCLASSEX wClass = new WinUser.WNDCLASSEX();
        zoomWindowProc = this::zoomWindowCallback;
        wClass.lpszClassName = "MousemasterZoomWindow";
        wClass.lpfnWndProc = zoomWindowProc;
        User32.INSTANCE.RegisterClassEx(wClass);
        zoomHwnd = User32.INSTANCE.CreateWindowEx(
                User32.WS_EX_TOPMOST | ExtendedUser32.WS_EX_TOOLWINDOW |
                ExtendedUser32.WS_EX_NOACTIVATE | ExtendedUser32.WS_EX_LAYERED |
                ExtendedUser32.WS_EX_TRANSPARENT,
                wClass.lpszClassName, "MousemasterZoom", WinUser.WS_POPUP,
                0, 0, 10, 10, null, null,
                Kernel32.INSTANCE.GetModuleHandle(null), null);
        // Layered and transparent is what makes it click-through for other processes.
        setZoomWindowVisible(false);
        // Without this the duplicated frame contains the zoom window: infinite mirror.
        ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(zoomHwnd,
                ExtendedUser32.WDA_EXCLUDEFROMCAPTURE);
        zoomRenderer = new WindowsZoomRenderer(new WindowsDesktopDuplication());
    }

    @Override
    public void setIndicator(IndicatorConfiguration indicator,
                             IndicatorConfiguration transitionTo, boolean allowFade,
                             boolean includeCursorGlyph) {
        Objects.requireNonNull(indicator);
        boolean renderAsCursor = indicator.renderAsCursor();
        if (!renderAsCursor && !indicatorIsCursor && indicatorRenderer != null &&
            indicatorRenderer.showing() &&
            indicator.equals(indicatorRenderer.currentIndicator()))
            return;
        if (mouse.tryFindMousePosition() == null) {
            if (!mousePositionMissing)
                logger.warn("Unable to find mouse position for indicator");
            mousePositionMissing = true;
            return;
        }
        mousePositionMissing = false;
        WinDef.POINT mousePosition = mouse.findMousePosition();
        if (renderAsCursor) {
            double scale = WindowsScreen.findActiveScreen(mousePosition).scale();
            if (indicatorIsCursor && indicator.equals(currentCursorIndicator) &&
                scale == currentCursorScale &&
                includeCursorGlyph == currentCursorIncludeGlyph)
                return;
            if (indicatorRenderer != null && indicatorRenderer.showing())
                indicatorRenderer.hide(false);
            if (indicatorRenderer == null)
                indicatorRenderer = new IndicatorRenderer();
            IndicatorRenderer.CursorImage image =
                    indicatorRenderer.renderCursorImage(indicator, scale);
            if (image == null)
                return;
            mouse.setIndicatorCursor(image.argb(), image.width(), image.height(),
                    includeCursorGlyph, indicator.equals(transitionTo));
            indicatorIsCursor = true;
            currentCursorIndicator = indicator;
            currentCursorScale = scale;
            currentCursorIncludeGlyph = includeCursorGlyph;
            return;
        }
        if (indicatorIsCursor) {
            mouse.showCursor();
            indicatorIsCursor = false;
            currentCursorIndicator = null;
        }
        if (indicatorHwnd == null)
            createIndicatorWindow();
        boolean wasShowing = indicatorRenderer.showing();
        indicatorRenderer.setIndicator(indicator, transitionTo, allowFade,
                mouseRectangle(mousePosition), mouse.cursorVisualCenter(),
                WindowsScreen.findActiveScreen(mousePosition), currentZoom);
        if (!wasShowing)
            setTopmost();
    }

    @Override
    public void setZoom(Zoom zoom) {
        if (currentZoom != null && currentZoom.equals(zoom))
            return;
        if (zoomHwnd == null) {
            if (zoom == null)
                return;
            createZoomWindow();
        }
        // Magnifying where the hints and the indicator go, over a screen that Direct3D
        // cannot magnify, would send clicks to the wrong place.
        if (zoom != null && !zoomRenderer.prepare(zoomHwnd, zoom.screenRectangle()))
            zoom = null;
        Zoom previousZoom = currentZoom;
        currentZoom = zoom;
        if (currentZoom == null) {
            zoomWindowShowing = false;
            setZoomWindowVisible(false);
        }
        else if (previousZoom == null ||
                 !previousZoom.screenRectangle().equals(currentZoom.screenRectangle()))
            // Still transparent: updateZoomWindow reveals it once it holds a frame.
            placeZoomWindow(currentZoom.screenRectangle());
        if (previousZoom == null && currentZoom != null) {
            updateCaptureExclusions();
            // Duplication hands over the desktop as Windows assembled it, so the
            // exclusions only reach the next frame it assembles.
            Dwmapi.INSTANCE.DwmFlush();
            zoomRenderer.discardFrame();
        }
        else if (previousZoom != null && currentZoom == null)
            updateCaptureExclusions();
        if (indicatorHwnd != null)
            moveAndResizeIndicatorWindow();
        if (hintMeshRenderer.showing()) {
            for (TransparentWindow window : hintMeshRenderer.windows())
                User32.INSTANCE.InvalidateRect(hwnd(window), null, true);
        }
        updateZoomWindow();
    }

    /**
     * Keeps the overlays out of the duplicated frame, which would otherwise magnify them.
     * This hides them from every capture, not just ours, so it is cleared with the zoom.
     * Each call makes the windows it touches flicker: never call it per frame.
     */
    private void updateCaptureExclusions() {
        int affinity = currentZoom != null ? ExtendedUser32.WDA_EXCLUDEFROMCAPTURE
                                           : ExtendedUser32.WDA_NONE;
        if (gridHwnd != null)
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(gridHwnd, affinity);
        for (TransparentWindow window : hintMeshRenderer.windows())
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(hwnd(window), affinity);
        if (indicatorHwnd != null)
            ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(indicatorHwnd, affinity);
    }

    private WinDef.LRESULT zoomWindowCallback(WinDef.HWND hwnd, int uMsg,
                                                     WinDef.WPARAM wParam,
                                                     WinDef.LPARAM lParam) {
        return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
    }

    @Override
    public void hideIndicator(boolean allowFade) {
        if (indicatorIsCursor) {
            mouse.showCursor();
            indicatorIsCursor = false;
            currentCursorIndicator = null;
            return;
        }
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
        boolean wasShowing = gridRenderer.showing();
        // Screen pixels: the configured thickness does not change with the zoom. A line the
        // screen's scale leaves below a pixel is still drawn as one, and the repainted region
        // grows by the thickness, so a thickness of 0 would leave the line behind.
        gridRenderer.setGrid(grid, virtualDesktopBounds(),
                Math.max(1, (int) Math.round(grid.lineThickness())));
        if (!wasShowing)
            setTopmost();
        if (firstCreation)
            updateCaptureExclusions();
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
    public void preWarmHintMesh(HintMesh hintMesh, Zoom zoom) {
        hintMeshRenderer.preWarmHintMesh(hintMesh, zoom, WindowsScreen.findScreens());
    }

    @Override
    public void setHintMesh(HintMesh hintMesh, Zoom zoom, boolean hintMatch) {
        showHintMesh(hintMesh, zoom, hintMatch, true);
    }

    /** The mesh was concealed while the zoom moved, so putting it back is not it appearing. */
    @Override
    public void restoreHintMesh(HintMesh hintMesh, Zoom zoom) {
        showHintMesh(hintMesh, zoom, false, false);
    }

    private void showHintMesh(HintMesh hintMesh, Zoom zoom, boolean hintMatch,
                              boolean allowFade) {
        int windowsBefore = hintMeshRenderer.windows().size();
        boolean wasShowing = hintMeshRenderer.showing();
        hintMeshRenderer.setHintMesh(hintMesh, zoom, hintMatch, allowFade,
                WindowsScreen.findScreens());
        if (!wasShowing)
            setTopmost();
        if (hintMeshRenderer.windows().size() > windowsBefore)
            updateCaptureExclusions();
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

    @Override
    public boolean hintTransitionAnimating() {
        return hintMeshRenderer.transitionAnimating();
    }

    @Override
    public boolean hintMeshBuildPending() {
        return hintMeshRenderer.buildPending();
    }

    void mouseMoved(WinDef.POINT mousePosition) {
        if (indicatorIsCursor) {
            // The OS moves the cursor; only re-install when the screen scale changes
            // (cursors don't auto-scale per-monitor DPI).
            double scale = WindowsScreen.findActiveScreen(mousePosition).scale();
            if (scale != currentCursorScale && currentCursorIndicator != null) {
                IndicatorRenderer.CursorImage image =
                        indicatorRenderer.renderCursorImage(currentCursorIndicator, scale);
                if (image != null) {
                    mouse.setIndicatorCursor(image.argb(), image.width(), image.height(),
                            currentCursorIncludeGlyph, true);
                    currentCursorScale = scale;
                }
            }
            return;
        }
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
