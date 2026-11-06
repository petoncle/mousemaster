package mousemaster.platform.windows;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import io.qt.core.Qt;
import io.qt.gui.QImage;
import io.qt.gui.QPixmap;
import io.qt.widgets.QApplication;
import mousemaster.*;
import mousemaster.platform.Overlay;
import mousemaster.platform.DesktopCapture;
import mousemaster.qt.*;
import mousemaster.renderer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.stream.IntStream;
import java.nio.ByteBuffer;

public class WindowsOverlay implements Overlay {

    private static final Logger logger = LoggerFactory.getLogger(WindowsOverlay.class);

    private static final Duration ZOOM_IDLE_RELEASE = Duration.ofSeconds(30);

    private final WindowsMouseController mouse;
    private boolean waitForZoom;
    private IndicatorRenderer followingIndicatorRenderer;
    private final List<IndicatorRenderer> followingIndicatorRenderers = new ArrayList<>();
    private final List<WindowsIndicatorWindow> followingIndicatorWindows = new ArrayList<>();
    private final List<IndicatorRenderer> anchoredIndicatorRenderers = new ArrayList<>();
    private final List<WindowsIndicatorWindow> anchoredIndicatorWindows = new ArrayList<>();
    private final Map<IndicatorLayerCopyKey, IndicatorRenderer>
            anchoredIndicatorRendererByCopyKey = new LinkedHashMap<>();
    private Map<IndicatorLayerCopyKey, IndicatorConfiguration> indicatorByCopyKey = Map.of();
    private List<IndicatorRenderer.WindowLayerGroup> currentWindowLayerGroups = List.of();
    private List<WindowsIndicatorWindow> indicatorWindowsAboveOverlay = List.of();
    private List<WindowsIndicatorWindow> indicatorWindowsBelowOverlay = List.of();
    private Map<WindowsIndicatorWindow, Set<String>> layerNamesByIndicatorWindow = Map.of();
    private boolean indicatorIsCursor;
    private boolean indicatorExcludedFromCapture;
    private boolean hintMeshExcludedFromCapture;
    private IndicatorConfiguration currentCursorIndicator;
    private double currentCursorScale;
    private boolean currentIncludeOriginalCursor;
    private boolean currentCursorAnimating;
    private boolean mousePositionMissing;
    /** Owns no QWidget, so it can be created eagerly (no QtJambi native-load ordering). */
    private final HintMeshRenderer hintMeshRenderer;
    private WinDef.HWND zoomHwnd;
    private WinUser.WindowProc zoomWindowProc;
    private WindowsZoomRenderer zoomRenderer;
    /** Reused across vision captures; created on first use. */
    private final List<WindowsDesktopFrameCapture> desktopCaptures = new ArrayList<>();
    private WindowsDesktopDuplication zoomDuplication;
    private Zoom currentZoom;
    private boolean zoomWindowShowing;
    private double zoomIdleTimer;
    private double zoomFrameWait;
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
    public void runPendingHintMeshWork() {
        hintMeshRenderer.runPendingWork();
    }

    @Override
    public void update(double delta) {
        hintMeshRenderer.update(delta);
        hintMeshRenderer.runPendingWork();
        updateZoomWindow(delta);
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

    private void updateZoomWindow(double delta) {
        if (currentZoom == null)
            return;
        // The loop iterates far more often than the screen refreshes, and every frame copies
        // and presents a screen sized image over whatever else is drawing.
        zoomFrameWait -= delta;
        if (zoomWindowShowing && zoomFrameWait > 0)
            return;
        if (!zoomRenderer.prepare(zoomHwnd, currentZoom.screenRectangle()))
            return;
        long before = System.nanoTime();
        if (!zoomRenderer.render(currentZoom))
            return;
        double renderDuration = (System.nanoTime() - before) / 1e9;
        // Presenting over a busy compositor takes tens of milliseconds and holds up the
        // keyboard with it, so give the loop as long as the render took before asking again.
        zoomFrameWait = Math.max(Mousemaster.FRAME_INTERVAL, 2 * renderDuration);
        // Not per frame: enforceTopmost issues a SetWindowPos per overlay, which flickers
        // the layered hint windows.
        if (!zoomWindowShowing) {
            zoomWindowShowing = true;
            // Drawing only queues the frame: revealing the window before it reaches the
            // screen would show what it last held, the final frame of the previous zoom.
            Dwmapi.INSTANCE.DwmFlush();
            setZoomWindowVisible(true);
            setWindowTopmost(zoomHwnd);
            setTopmost();
        }
    }

    @Override
    public void flushCache() {
        hintMeshRenderer.flushCache();
    }

    @Override
    public Rectangle activeWindowRectangle() {
        WinDef.HWND foregroundWindow = User32.INSTANCE.GetForegroundWindow();
        // https://stackoverflow.com/a/65605845
        WinDef.RECT excludeShadow = windowRectExcludingShadow(foregroundWindow);
        return new Rectangle(excludeShadow.left, excludeShadow.top,
                excludeShadow.right - excludeShadow.left,
                excludeShadow.bottom - excludeShadow.top);
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
        List<WinDef.HWND> notTopmostHwnds = new ArrayList<>();
        // First in the hwnds list means drawn on top.
        for (WindowsIndicatorWindow indicatorWindow : indicatorWindowsAboveOverlay)
            hwnds.add(indicatorWindow.hwnd());
        for (TransparentWindow window : hintMeshRenderer.windows())
            (hintMeshRenderer.showing() ? hwnds : notTopmostHwnds).add(hwnd(window));
        for (WindowsIndicatorWindow indicatorWindow : indicatorWindowsBelowOverlay)
            hwnds.add(indicatorWindow.hwnd());
        if (zoomHwnd != null)
            (currentZoom != null ? hwnds : notTopmostHwnds).add(zoomHwnd);
        // The shell demotes the taskbar under a topmost window that covers a screen.
        notTopmostHwnds.forEach(this::setWindowNotTopmost);
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
            if (topmost(hwnds.get(windowIndex + 1)) && hwnds.get(windowIndex + 1)
                     .equals(windowBelow(hwnds.get(windowIndex), hwnds)))
                continue;
            allOtherWindowsAreBelowInOrder = false;
            break;
        }
        if (allOtherWindowsAreBelowInOrder)
            return;
        for (int windowIndex = hwnds.size() - 1; windowIndex >= 0; windowIndex--)
            setWindowTopmost(hwnds.get(windowIndex));
    }

    /** Our hidden IME window sits directly above the overlay that owns it. */
    private WinDef.HWND windowBelow(WinDef.HWND hwnd, List<WinDef.HWND> hwnds) {
        WinDef.HWND nextHwnd = hwnd;
        do {
            nextHwnd = User32.INSTANCE.GetWindow(nextHwnd,
                    new WinDef.DWORD(User32.GW_HWNDNEXT));
        } while (nextHwnd != null && !hwnds.contains(nextHwnd));
        return nextHwnd;
    }

    private void setWindowTopmost(WinDef.HWND hwnd) {
        User32.INSTANCE.SetWindowPos(hwnd, ExtendedUser32.HWND_TOPMOST, 0, 0, 0, 0,
                WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE | WinUser.SWP_NOACTIVATE);
    }

    /** enforceTopmost runs every 200ms and SetWindowPos flickers the layered windows. */
    private void setWindowNotTopmost(WinDef.HWND hwnd) {
        if (!topmost(hwnd))
            return;
        User32.INSTANCE.SetWindowPos(hwnd, ExtendedUser32.HWND_NOTOPMOST, 0, 0, 0, 0,
                WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE | WinUser.SWP_NOACTIVATE);
    }

    private static boolean topmost(WinDef.HWND hwnd) {
        return (User32.INSTANCE.GetWindowLongPtr(hwnd, WinUser.GWL_EXSTYLE).longValue() &
                User32.WS_EX_TOPMOST) != 0;
    }


    private void moveAndResizeIndicatorWindow() {
        moveAndResizeIndicatorWindow(mouse.findMousePosition());
    }

    private void moveAndResizeIndicatorWindow(WinDef.POINT mousePosition) {
        showIndicator(currentWindowLayerGroups, indicatorByCopyKey, Set.of(), Map.of(),
                Map.of(), Map.of(), mousePosition);
    }

    /**
     * The anchored layers and the copies of a window layer group are drawn in one window: when
     * IndicatorRenderer.anchoredLayers splits the anchored layers into more window images, a
     * layer leaves one window image and enters another in the same update.
     */
    private void showIndicator(
            List<IndicatorRenderer.WindowLayerGroup> windowLayerGroups,
            Map<IndicatorLayerCopyKey, IndicatorConfiguration> indicatorByCopyKey,
            Set<String> layerNamesToAnchor,
            Map<IndicatorPosition, List<Point>> pointsByPosition,
            Map<Area, Rectangle> rectangleByArea,
            Map<Point, HintRectangles> hintRectanglesByPoint, WinDef.POINT mousePosition) {
        List<WindowsIndicatorWindow> windows = new ArrayList<>();
        List<List<IndicatorRenderer.WindowImage>> windowImages = new ArrayList<>();
        Map<WindowsIndicatorWindow, Set<String>> layerNamesByWindow = new HashMap<>();
        List<WindowsIndicatorWindow> windowsAboveOverlay = new ArrayList<>();
        List<WindowsIndicatorWindow> windowsBelowOverlay = new ArrayList<>();
        int followingIndex = 0;
        int anchoredIndex = 0;
        int runIndex = 0;
        for (IndicatorRenderer.WindowLayerGroup windowLayerGroup : windowLayerGroups) {
            WindowsIndicatorWindow window = windowLayerGroup.followMouse() ?
                    indicatorWindow(followingIndicatorWindows, followingIndex, false) :
                    indicatorWindow(anchoredIndicatorWindows, anchoredIndex++, true);
            List<IndicatorRenderer.WindowImage> images = new ArrayList<>();
            Set<String> layerNames = new HashSet<>();
            for (IndicatorLayerCopyKey copyKey : windowLayerGroup.copyKeys()) {
                images.add(windowImage(anchoredIndicatorRendererByCopyKey.get(copyKey),
                        indicatorByCopyKey.get(copyKey), Set.of(), Map.of(), Map.of(),
                        Map.of(), mousePosition));
                layerNames.add(copyKey.layerName());
            }
            for (IndicatorConfiguration indicator : windowLayerGroup.indicators()) {
                images.add(windowLayerGroup.followMouse() ?
                        windowImage(followingIndicatorRenderer(followingIndex++), indicator,
                                Set.of(), Map.of(), Map.of(), Map.of(), mousePosition) :
                        windowImage(anchoredIndicatorRenderer(runIndex++), indicator,
                                layerNamesToAnchor, pointsByPosition, rectangleByArea,
                                hintRectanglesByPoint, mousePosition));
                layerNames.addAll(indicator.layerByName().keySet());
            }
            windows.add(window);
            windowImages.add(images);
            layerNamesByWindow.put(window, layerNames);
            (windowLayerGroup.belowOverlay() ? windowsBelowOverlay : windowsAboveOverlay)
                    .addFirst(window);
        }
        // A layer that goes to another window is drawn there before it leaves this one.
        for (boolean receivesLayer : List.of(true, false))
            for (int index = 0; index < windows.size(); index++)
                if (receivesLayer != layerNamesByIndicatorWindow
                        .getOrDefault(windows.get(index), Set.of())
                        .containsAll(layerNamesByWindow.get(windows.get(index))))
                    windows.get(index).show(windowImages.get(index));
        for (WindowsIndicatorWindow indicatorWindow : indicatorWindows())
            if (!windows.contains(indicatorWindow))
                indicatorWindow.show(List.of());
        boolean orderChanged = !windowsAboveOverlay.equals(indicatorWindowsAboveOverlay) ||
                               !windowsBelowOverlay.equals(indicatorWindowsBelowOverlay);
        indicatorWindowsAboveOverlay = windowsAboveOverlay;
        indicatorWindowsBelowOverlay = windowsBelowOverlay;
        layerNamesByIndicatorWindow = layerNamesByWindow;
        currentWindowLayerGroups = windowLayerGroups;
        this.indicatorByCopyKey = indicatorByCopyKey;
        if (orderChanged)
            setTopmost();
    }

    private WindowsIndicatorWindow indicatorWindow(List<WindowsIndicatorWindow> windows,
                                                   int index, boolean grows) {
        while (windows.size() <= index)
            windows.add(createIndicatorWindow(grows));
        return windows.get(index);
    }

    private IndicatorRenderer followingIndicatorRenderer(int index) {
        while (followingIndicatorRenderers.size() <= index)
            followingIndicatorRenderers.add(new IndicatorRenderer(followingIndicatorRenderer));
        return followingIndicatorRenderers.get(index);
    }

    private IndicatorRenderer anchoredIndicatorRenderer(int index) {
        while (anchoredIndicatorRenderers.size() <= index)
            anchoredIndicatorRenderers.add(new IndicatorRenderer(followingIndicatorRenderer));
        return anchoredIndicatorRenderers.get(index);
    }

    private IndicatorRenderer.WindowImage windowImage(
            IndicatorRenderer renderer, IndicatorConfiguration indicator,
            Set<String> layerNamesToAnchor, Map<IndicatorPosition, List<Point>> pointsByPosition,
            Map<Area, Rectangle> rectangleByArea,
            Map<Point, HintRectangles> hintRectanglesByPoint, WinDef.POINT mousePosition) {
        return renderer.renderWindowImage(indicator, layerNamesToAnchor,
                pointsByPosition, rectangleByArea, hintRectanglesByPoint,
                mouseRectangle(mousePosition),
                mouse.cursorVisualCenter(),
                WindowsScreen.findActiveScreen(mousePosition), currentZoom,
                hintMeshRenderer.lastSelectedHintBoxHexColor());
    }

    private List<WindowsIndicatorWindow> indicatorWindows() {
        List<WindowsIndicatorWindow> indicatorWindows =
                new ArrayList<>(followingIndicatorWindows);
        indicatorWindows.addAll(anchoredIndicatorWindows);
        return indicatorWindows;
    }

    private WindowsIndicatorWindow createIndicatorWindow(boolean grows) {
        WindowsIndicatorWindow indicatorWindow = new WindowsIndicatorWindow(grows);
        applyOverlayExStyles(indicatorWindow.hwnd());
        applyCaptureExclusion(indicatorWindow.hwnd(),
                currentZoom != null || indicatorExcludedFromCapture);
        indicatorWindow.show(List.of());
        return indicatorWindow;
    }

    /** The cursor's bounding rectangle (position + size) at the given mouse position. */
    private Rectangle mouseRectangle(WinDef.POINT mousePosition) {
        WindowsMouseController.MouseSize mouseSize = mouse.mouseSize();
        return new Rectangle(mousePosition.x, mousePosition.y,
                mouseSize.width(), mouseSize.height());
    }

    private void createIndicatorWindows() {
        followingIndicatorRenderer = new IndicatorRenderer();
        followingIndicatorRenderers.add(followingIndicatorRenderer);
        followingIndicatorWindows.add(createIndicatorWindow(false));
        anchoredIndicatorWindows.add(createIndicatorWindow(true));
        setTopmost();
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
        applyCaptureExclusion(hwnd, currentZoom != null);
        // enforceTopmost skips windows that are not showing.
        setWindowTopmost(hwnd);
    }

    /** The window factory the renderer uses: a styled, transparent, click-through window. */
    private TransparentWindow createStyledHintMeshWindow() {
        TransparentWindow window = new TransparentWindow();
        applyOverlayExStyles(hwnd(window));
        applyCaptureExclusion(hwnd(window), currentZoom != null || hintMeshExcludedFromCapture);
        return window;
    }

    @Override
    public void preWarmFontsAndWindows(Set<HintMeshConfiguration> hintMeshConfigurations) {
        QtHintFont.preWarm(hintMeshConfigurations);
        hintMeshRenderer.preWarmHintMeshWindows(WindowsScreen.findScreens());
        preWarmZoomWindow();
        if (followingIndicatorRenderer != null)
            return;
        long before = System.nanoTime();
        createIndicatorWindows();
        followingIndicatorRenderer.preWarm();
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
                ExtendedUser32.WS_EX_TOOLWINDOW |
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
        zoomDuplication = new WindowsDesktopDuplication();
        zoomRenderer = new WindowsZoomRenderer(zoomDuplication);
    }

    @Override
    public void setIndicator(IndicatorConfiguration indicator,
                             Map<IndicatorLayerCopyKey, IndicatorConfiguration> indicatorByCopyKey,
                             Set<String> layerNamesToAnchor,
                             Map<IndicatorPosition, List<Point>> pointsByPosition,
                             Map<Area, Rectangle> rectangleByArea,
                             Map<Point, HintRectangles> hintRectanglesByPoint,
                             int overlayZ, boolean animating,
                             boolean includeOriginalCursor) {
        Objects.requireNonNull(indicator);
        if (followingIndicatorRenderer == null)
            createIndicatorWindows();
        if (indicator.excludeFromCapture() != indicatorExcludedFromCapture) {
            indicatorExcludedFromCapture = indicator.excludeFromCapture();
            for (WindowsIndicatorWindow indicatorWindow : indicatorWindows())
                applyCaptureExclusion(indicatorWindow.hwnd(),
                        currentZoom != null || indicatorExcludedFromCapture);
        }
        IndicatorConfiguration cursorIndicator = IndicatorRenderer.cursorLayers(indicator);
        boolean renderAsCursor = cursorIndicator.renderAsCursor() && cursorIndicator.enabled();
        Map<String, IndicatorLayerConfiguration> layerByName =
                new LinkedHashMap<>(indicator.layerByName());
        if (renderAsCursor)
            layerByName.keySet().removeAll(cursorIndicator.layerByName().keySet());
        List<IndicatorRenderer.WindowLayerGroup> windowLayerGroups =
                IndicatorRenderer.windowLayerGroups(new IndicatorConfiguration(
                                indicator.renderAsCursor(), indicator.excludeFromCapture(),
                                layerByName), indicatorByCopyKey, currentWindowLayerGroups,
                        layerNamesToAnchor, overlayZ);
        if (!renderAsCursor && !indicatorIsCursor &&
            windowLayerGroups.equals(currentWindowLayerGroups) && indicatorByCopyKey.isEmpty() &&
            this.indicatorByCopyKey.isEmpty() && layerNamesToAnchor.isEmpty())
            return;
        if (mouse.tryFindMousePosition() == null) {
            if (!mousePositionMissing)
                logger.warn("Unable to find mouse position for indicator");
            mousePositionMissing = true;
            return;
        }
        mousePositionMissing = false;
        WinDef.POINT mousePosition = mouse.findMousePosition();
        for (IndicatorLayerCopyKey copyKey : indicatorByCopyKey.keySet())
            if (!this.indicatorByCopyKey.containsKey(copyKey))
                anchoredIndicatorRendererByCopyKey
                        .computeIfAbsent(copyKey, copyKey_ -> new IndicatorRenderer())
                        .anchor(copyKey.layerName(), followingIndicatorRenderer);
        showIndicator(windowLayerGroups, new LinkedHashMap<>(indicatorByCopyKey),
                layerNamesToAnchor, pointsByPosition, rectangleByArea, hintRectanglesByPoint,
                mousePosition);
        if (renderAsCursor)
            setIndicatorCursor(cursorIndicator, animating, includeOriginalCursor,
                    mousePosition);
        if (!renderAsCursor && indicatorIsCursor) {
            // The cursor changes on the next refresh, the window once it is composited.
            Dwmapi.INSTANCE.DwmFlush();
            mouse.showCursor();
            indicatorIsCursor = false;
            currentCursorIndicator = null;
        }
    }

    @Override
    public Rectangle hintBoxRectangle(Hint hint) {
        return hintMeshRenderer.hintBoxRectangle(hint);
    }

    private void setIndicatorCursor(IndicatorConfiguration indicator, boolean animating,
                                    boolean includeOriginalCursor,
                                    WinDef.POINT mousePosition) {
        double scale = WindowsScreen.findActiveScreen(mousePosition).scale();
        if (indicatorIsCursor && indicator.equals(currentCursorIndicator) &&
            scale == currentCursorScale &&
            includeOriginalCursor == currentIncludeOriginalCursor &&
            animating == currentCursorAnimating)
            return;
        IndicatorRenderer.IndicatorImage image =
                followingIndicatorRenderer.renderCursorImage(indicator, scale,
                        hintMeshRenderer.lastSelectedHintBoxHexColor(),
                        mouseRectangle(mousePosition), mouse.cursorVisualCenter(),
                        WindowsScreen.findActiveScreen(mousePosition));
        mouse.setIndicatorCursor(image.argb(), image.width(), image.height(),
                includeOriginalCursor, !animating);
        indicatorIsCursor = true;
        currentCursorIndicator = indicator;
        currentCursorScale = scale;
        currentIncludeOriginalCursor = includeOriginalCursor;
        currentCursorAnimating = animating;
    }

    @Override
    public boolean canZoom(Rectangle screenRectangle) {
        if (zoomHwnd == null)
            createZoomWindow();
        // An output duplicates once per application, so the zoom takes it over from the
        // vision capture, which falls back to the Qt grab until the zoom gives it back.
        List<WindowsDesktopFrameCapture> covering = desktopCaptures.stream()
                .filter(capture -> capture.covers(screenRectangle))
                .toList();
        covering.forEach(WindowsDesktopFrameCapture::close);
        desktopCaptures.removeAll(covering);
        return zoomRenderer.prepare(zoomHwnd, screenRectangle);
    }

    @Override
    public void setZoom(Zoom zoom) {
        if (currentZoom != null && currentZoom.equals(zoom))
            return;
        if (zoomHwnd == null && zoom == null)
            return;
        // Magnifying where the hints and the indicator go, over a screen that Direct3D
        // cannot magnify, would send clicks to the wrong place.
        if (zoom != null && !canZoom(zoom.screenRectangle()))
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
        else if (previousZoom != null && currentZoom == null) {
            updateCaptureExclusions();
            zoomDuplication.releaseDuplication();
        }
        if (followingIndicatorRenderer != null)
            moveAndResizeIndicatorWindow();
        if (hintMeshRenderer.showing()) {
            for (TransparentWindow window : hintMeshRenderer.windows())
                User32.INSTANCE.InvalidateRect(hwnd(window), null, true);
        }
        updateZoomWindow(0);
    }

    /**
     * Keeps the overlays out of the duplicated frame, which would otherwise magnify them.
     * This hides them from every capture, not just ours, so it is cleared with the zoom.
     * Each call makes the windows it touches flicker: never call it per frame.
     */
    private static void applyCaptureExclusion(WinDef.HWND hwnd, boolean excluded) {
        ExtendedUser32.INSTANCE.SetWindowDisplayAffinity(hwnd,
                excluded ? ExtendedUser32.WDA_EXCLUDEFROMCAPTURE : ExtendedUser32.WDA_NONE);
    }

    private void updateCaptureExclusions() {
        for (WindowsIndicatorWindow indicatorWindow : indicatorWindows())
            applyCaptureExclusion(indicatorWindow.hwnd(),
                    currentZoom != null || indicatorExcludedFromCapture);
        for (TransparentWindow window : hintMeshRenderer.windows())
            applyCaptureExclusion(hwnd(window), currentZoom != null || hintMeshExcludedFromCapture);
    }

    private WinDef.LRESULT zoomWindowCallback(WinDef.HWND hwnd, int uMsg,
                                                     WinDef.WPARAM wParam,
                                                     WinDef.LPARAM lParam) {
        return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
    }

    @Override
    public void hideIndicator() {
        if (indicatorIsCursor) {
            mouse.showCursor();
            indicatorIsCursor = false;
            currentCursorIndicator = null;
        }
        for (WindowsIndicatorWindow indicatorWindow : indicatorWindows())
            indicatorWindow.show(List.of());
        indicatorByCopyKey = Map.of();
        currentWindowLayerGroups = List.of();
        layerNamesByIndicatorWindow = Map.of();
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
    public void stopHintMatchAnimation() {
        hintMeshRenderer.stopHintMatchAnimation();
    }

    @Override
    public void preWarmHintMesh(HintMesh hintMesh, Zoom zoom) {
        hintMeshRenderer.preWarmHintMesh(hintMesh, zoom, WindowsScreen.findScreens());
    }

    @Override
    public void setHintMesh(HintMesh hintMesh, Zoom zoom, boolean animate) {
        if (hintMesh.excludeFromCapture() != hintMeshExcludedFromCapture) {
            hintMeshExcludedFromCapture = hintMesh.excludeFromCapture();
            for (TransparentWindow window : hintMeshRenderer.windows())
                applyCaptureExclusion(hwnd(window),
                        currentZoom != null || hintMeshExcludedFromCapture);
        }
        boolean wasShowing = hintMeshRenderer.showing();
        hintMeshRenderer.setHintMesh(hintMesh, zoom, animate, WindowsScreen.findScreens());
        if (!wasShowing)
            setTopmost();
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
            // (cursors don't auto-scale per-monitor DPI) or a color depends on where it is.
            double scale = WindowsScreen.findActiveScreen(mousePosition).scale();
            boolean scaleChanged = scale != currentCursorScale;
            if (currentCursorIndicator != null && (scaleChanged ||
                    !IndicatorRenderer.looksTheSameAnywhere(currentCursorIndicator))) {
                IndicatorRenderer.IndicatorImage image =
                        followingIndicatorRenderer.renderCursorImage(
                                currentCursorIndicator, scale,
                                hintMeshRenderer.lastSelectedHintBoxHexColor(),
                                mouseRectangle(mousePosition), mouse.cursorVisualCenter(),
                                WindowsScreen.findActiveScreen(mousePosition));
                mouse.setIndicatorCursor(image.argb(), image.width(), image.height(),
                        currentIncludeOriginalCursor, scaleChanged);
                currentCursorScale = scale;
            }
            if (currentWindowLayerGroups.stream()
                                        .noneMatch(IndicatorRenderer.WindowLayerGroup::followMouse))
                return;
        }
        if (followingIndicatorRenderer == null)
             return;
        // During zoom, currentZoom still has the previous frame's zoom center
        // (it will be updated right after by ZoomManager in WindowsPlatform.sleep).
        // Positioning here would use that stale center, causing a brief
        // mispositioning until setZoom() corrects it.
        if (currentZoom != null)
            return;
        moveAndResizeIndicatorWindow(mousePosition);
    }


    /**
     * Captures the desktop inside bounds and downscales it for detection, leaving the overlays
     * that are on screen out of the frame. Runs on the Qt main thread.
     */
    @Override
    public DesktopCapture captureDesktop(Rectangle bounds, int scaledWidth,
                                         int scaledHeight) {
        boolean conceal = currentZoom == null;
        if (conceal)
            setOverlaysExcludedFromCapture(true);
        try {
            WindowsDesktopFrameCapture capture = captureCovering(bounds);
            WindowsDesktopFrameCapture.Frame frame = duplicatedFrame(capture);
            byte[] scaledBytes = frame != null
                    ? DesktopCapture.boxDownscaledRgb(frame.bgra(), frame.rowPitch(),
                            withinOutput(bounds, capture.outputBounds()),
                            scaledWidth, scaledHeight)
                    : qtGrabbedRgb(bounds, scaledWidth, scaledHeight);
            logger.debug("Captured desktop " + bounds.width() + "x" + bounds.height() +
                         " via " + (frame != null ? "DXGI" : "Qt"));
            return new DesktopCapture(bounds, scaledBytes, scaledWidth, scaledHeight);
        }
        finally {
            if (conceal)
                setOverlaysExcludedFromCapture(false);
        }
    }

    private void setOverlaysExcludedFromCapture(boolean excluded) {
        if (hintMeshRenderer.showing())
            for (TransparentWindow window : hintMeshRenderer.windows())
                applyCaptureExclusion(hwnd(window), excluded || hintMeshExcludedFromCapture);
        for (WindowsIndicatorWindow indicatorWindow : indicatorWindows())
            applyCaptureExclusion(indicatorWindow.hwnd(),
                    excluded || indicatorExcludedFromCapture);
        if (excluded)
            Dwmapi.INSTANCE.DwmFlush();
    }

    private WindowsDesktopFrameCapture captureCovering(Rectangle bounds) {
        for (WindowsDesktopFrameCapture capture : desktopCaptures)
            if (capture.covers(bounds))
                return capture;
        WindowsDesktopFrameCapture capture = new WindowsDesktopFrameCapture(bounds);
        desktopCaptures.add(capture);
        return capture;
    }

    /** Bounds in the coordinates of the output's own frame. */
    private static Rectangle withinOutput(Rectangle bounds, Rectangle outputBounds) {
        return new Rectangle(bounds.x() - outputBounds.x(), bounds.y() - outputBounds.y(),
                bounds.width(), bounds.height());
    }

    /** DXGI Desktop Duplication frame, or null to fall back to the Qt grab. */
    private WindowsDesktopFrameCapture.Frame duplicatedFrame(
            WindowsDesktopFrameCapture capture) {
        try {
            return capture.capture();
        }
        catch (Throwable e) {
            // Never let a capture failure break a hint: fall back to the Qt grab.
            logger.debug("DXGI capture path failed, using Qt: " + e.getMessage());
            return null;
        }
    }


    /** Qt grab of the desktop under bounds, with our windows WDA-excluded. */
    private byte[] qtGrabbedRgb(Rectangle bounds, int scaledWidth, int scaledHeight) {
        QPixmap capture = QApplication.primaryScreen().grabWindow(
                0, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        QImage image = capture.toImage();
        capture.dispose();
        QImage smallImage = image.scaled(scaledWidth, scaledHeight,
                Qt.AspectRatioMode.IgnoreAspectRatio,
                Qt.TransformationMode.SmoothTransformation);
        image.dispose();
        QImage scaled = smallImage.convertToFormat(QImage.Format.Format_RGB888);
        smallImage.dispose();
        byte[] scaledBytes = readRows(scaled, scaledWidth, scaledHeight, 3);
        scaled.dispose();
        return scaledBytes;
    }

    /** Copies row by row: Qt pads each scanline to a 4-byte boundary. */
    private static byte[] readRows(QImage image, int width, int height,
                                   int bytesPerPixel) {
        ByteBuffer bits = image.bits();
        int stride = (int) image.bytesPerLine();
        int rowLength = width * bytesPerPixel;
        byte[] bytes = new byte[rowLength * height];
        for (int y = 0; y < height; y++) {
            bits.position(y * stride);
            bits.get(bytes, y * rowLength, rowLength);
        }
        return bytes;
    }
}
