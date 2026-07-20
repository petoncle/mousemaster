package mousemaster.platform.linux;

import com.sun.jna.Pointer;
import io.qt.core.Qt;
import io.qt.gui.QPixmap;
import io.qt.widgets.QApplication;
import io.qt.widgets.QWidget;
import mousemaster.*;
import mousemaster.platform.Overlay;
import mousemaster.platform.Screens;
import mousemaster.qt.QtHintFont;
import mousemaster.qt.ScreenshotWidget;
import mousemaster.qt.TransparentWindow;
import mousemaster.renderer.GridRenderer;
import mousemaster.renderer.HintMeshRenderer;
import mousemaster.renderer.IndicatorRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Linux overlay implementation. Delegates rendering to the same shared, platform-agnostic
 * renderer classes Windows uses (GridRenderer, HintMeshRenderer, IndicatorRenderer,
 * ScreenshotWidget); this class only handles X11-specific window management (stacking,
 * and the one-tick-deferred screenshot capture zoom uses, since Linux has no
 * capture-exclusion API like Windows' WDA_EXCLUDEFROMCAPTURE) plus the small glue the
 * renderers need (mouse position, active screen, screen enumeration).
 */
public class LinuxOverlay implements Overlay {

    private static final Logger logger = LoggerFactory.getLogger(LinuxOverlay.class);

    /** No XFixesGetCursorImage-based real cursor size lookup yet; assume a fixed size. */
    private static final int DEFAULT_CURSOR_SIZE = 24;

    private final Pointer display;
    private final Screens screens;
    private final ScreenManager screenManager;
    private final LinuxPlatform platform;
    private Runnable messagePump;

    private GridRenderer gridRenderer;
    /** Owns no QWidget, so it can be created eagerly (no QtJambi native-load ordering). */
    private final HintMeshRenderer hintMeshRenderer;
    private IndicatorRenderer indicatorRenderer;
    private ScreenshotWidget screenshotWidget;
    private Zoom currentZoom;
    private boolean waitForZoom;
    private boolean zoomAfterHintMeshEndAnimation;
    private Zoom afterHintMeshEndAnimationZoom;

    private Rectangle pendingCaptureRect;
    private Zoom pendingCaptureZoom;
    private boolean pendingCaptureGridWasVisible;
    private boolean pendingCaptureHintWasVisible;

    public LinuxOverlay(Pointer display, Screens screens, LinuxPlatform platform) {
        this.display = display;
        this.screens = screens;
        this.screenManager = new ScreenManager(screens);
        this.platform = platform;
        hintMeshRenderer = new HintMeshRenderer(this::createStyledHintMeshWindow,
                this::hintMeshEndAnimationEndedCallback);
    }

    /** The window factory the renderer uses: a styled, transparent, click-through window. */
    private TransparentWindow createStyledHintMeshWindow() {
        TransparentWindow window = new TransparentWindow();
        applyX11OverlayFlags(window);
        return window;
    }

    /** Runs when the hint container end-animation finishes: hides the hint mesh, then
     *  applies any zoom that was deferred until the animation finished. */
    private void hintMeshEndAnimationEndedCallback() {
        hideHintMesh();
        if (zoomAfterHintMeshEndAnimation) {
            zoomAfterHintMeshEndAnimation = false;
            Zoom zoom = afterHintMeshEndAnimationZoom;
            afterHintMeshEndAnimationZoom = null;
            setZoom(zoom);
        }
    }

    @Override
    public void update(double delta) {
        hintMeshRenderer.runPendingWork();
        if (pendingCaptureRect == null)
            return;
        // The hide() calls in requestScreenshotCapture() only ran on a previous tick;
        // QtManager.processEvents() at the top of *this* tick (see Mousemaster's main
        // loop - platform.update() runs before zoomManager.update(), so a capture
        // requested during zoom's update is only fulfilled here, one tick later) has
        // now flushed them, so the windows are actually gone from the screen and it's
        // safe to grab. Capturing in the same tick the hide() was issued would race
        // X11 and often capture the not-yet-unmapped windows.
        QPixmap pixmap = QApplication.primaryScreen()
                                     .grabWindow(0, pendingCaptureRect.x(),
                                             pendingCaptureRect.y(),
                                             pendingCaptureRect.width(),
                                             pendingCaptureRect.height());
        if (pendingCaptureGridWasVisible)
            gridRenderer.widget().show();
        if (pendingCaptureHintWasVisible)
            for (TransparentWindow window : hintMeshRenderer.windows())
                window.show();
        if (screenshotWidget == null)
            createScreenshotWindow();
        screenshotWidget.move(pendingCaptureRect.x(), pendingCaptureRect.y());
        screenshotWidget.resize(pendingCaptureRect.width(), pendingCaptureRect.height());
        screenshotWidget.setScreenshot(pixmap, pendingCaptureRect);
        screenshotWidget.setZoom(pendingCaptureZoom);
        currentZoom = pendingCaptureZoom;
        screenshotWidget.show();
        screenshotWidget.repaint();
        setTopmost();
        pendingCaptureRect = null;
        pendingCaptureZoom = null;
    }

    @Override
    public void flushCache() {
        hintMeshRenderer.flushCache();
    }

    @Override
    public void setTopmost() {
        // Mirrors WindowsOverlay's z-order (topmost first: grid, then hint windows,
        // then indicator, then the zoom/screenshot backdrop at the back). Qt's raise()
        // brings a top-level window to the front of the stack, so raise in the reverse
        // order here - whichever should end up topmost is raised last.
        if (screenshotWidget != null)
            screenshotWidget.raise();
        if (indicatorRenderer != null && indicatorRenderer.showing())
            indicatorRenderer.window().raise();
        for (TransparentWindow window : hintMeshRenderer.windows())
            window.raise();
        if (gridRenderer != null)
            gridRenderer.widget().raise();
    }

    @Override
    public void setMessagePump(Runnable pump) {
        this.messagePump = pump;
        hintMeshRenderer.setMessagePump(pump);
    }

    /**
     * Pre-warms the font engine with all hint fonts from the configuration, shifting
     * the cost of first-use font metrics computation away from the first hint render.
     */
    @Override
    public void preWarmFontStyles(Set<HintMeshConfiguration> configs) {
        QtHintFont.preWarm(configs);
    }

    @Override
    public void preWarmHintMeshWindows() {
        hintMeshRenderer.preWarmHintMeshWindows(screens.findScreens());
    }

    @Override
    public Rectangle activeWindowRectangle(double widthPct, double heightPct,
                                           int topInset, int bottomInset,
                                           int leftInset, int rightInset) {
        // TODO: Get active window rectangle using X11 XGetInputFocus + XGetWindowAttributes
        logger.debug("activeWindowRectangle() called - returning dummy rectangle");
        return new Rectangle(0, 0, 1920, 1080);
    }

    @Override
    public void setIndicator(Indicator indicator, boolean fadeAnimationEnabled,
                            Duration fadeAnimationDuration, boolean allowFade) {
        Objects.requireNonNull(indicator);
        if (indicatorRenderer == null) {
            indicatorRenderer = new IndicatorRenderer();
            applyX11OverlayFlags(indicatorRenderer.window());
        }
        indicatorRenderer.setIndicator(indicator, fadeAnimationEnabled,
                fadeAnimationDuration, allowFade, mouseRectangle(), cursorVisualCenter(),
                activeScreen(), currentZoom);
        setTopmost();
    }

    @Override
    public void hideIndicator(boolean allowFade) {
        if (indicatorRenderer != null)
            indicatorRenderer.hide(allowFade);
    }

    /** Repositions the visible indicator when the mouse moves. Windows does this via its
     *  low-level hook calling WindowsOverlay.mouseMoved(); Linux has no such hook, so
     *  LinuxPlatform's XQueryPointer-based polling calls this directly instead. */
    void mouseMoved(int x, int y) {
        if (indicatorRenderer == null || !indicatorRenderer.showing())
            return;
        // During zoom, currentZoom may still reflect the previous frame's center; the
        // active zoom manager corrects it via updateScreenshotZoom before the next
        // repaint, so skip here to avoid a stale-center mispositioning (mirrors
        // WindowsOverlay.mouseMoved's same early-return during an active zoom).
        if (currentZoom != null)
            return;
        indicatorRenderer.reposition(new Rectangle(x, y, DEFAULT_CURSOR_SIZE,
                DEFAULT_CURSOR_SIZE), new Point(0, 0),
                screenManager.nearestScreenContaining(x, y), null);
    }

    private int mouseX() {
        Integer x = platform.lastMouseX();
        return x != null ? x : 0;
    }

    private int mouseY() {
        Integer y = platform.lastMouseY();
        return y != null ? y : 0;
    }

    private Rectangle mouseRectangle() {
        return new Rectangle(mouseX(), mouseY(), DEFAULT_CURSOR_SIZE, DEFAULT_CURSOR_SIZE);
    }

    private Point cursorVisualCenter() {
        // Linux has no per-cursor-bitmap hotspot lookup (unlike WindowsMouseController's
        // GetIconInfo-based computeCursorVisualCenter); mirror Windows' own
        // fallback-when-lookup-fails value of (0, 0) rather than the cursor's actual
        // visual center relative to its hotspot.
        return new Point(0, 0);
    }

    private Screen activeScreen() {
        return screenManager.nearestScreenContaining(mouseX(), mouseY());
    }

    @Override
    public void setGrid(Grid grid) {
        Objects.requireNonNull(grid);
        boolean firstCreation = gridRenderer == null;
        if (firstCreation) {
            gridRenderer = new GridRenderer();
            applyX11OverlayFlags(gridRenderer.widget());
        }
        gridRenderer.setGrid(grid, virtualDesktopBounds(),
                (int) Math.round(grid.lineThickness()));
        setTopmost();
    }

    @Override
    public void hideGrid() {
        if (gridRenderer != null)
            gridRenderer.hide();
    }

    @Override
    public void setHintMesh(HintMesh hintMesh, Zoom zoom) {
        setHintMesh(hintMesh, zoom, false);
    }

    @Override
    public void setHintMesh(HintMesh hintMesh, Zoom zoom, boolean hintMatch) {
        boolean nonMatchShown = hintMeshRenderer.setHintMesh(hintMesh, zoom, hintMatch,
                screens.findScreens());
        if (nonMatchShown && zoomAfterHintMeshEndAnimation) {
            zoomAfterHintMeshEndAnimation = false;
            Zoom deferredZoom = afterHintMeshEndAnimationZoom;
            afterHintMeshEndAnimationZoom = null;
            setZoom(deferredZoom);
        }
        setTopmost();
    }

    @Override
    public void hideHintMesh() {
        hintMeshRenderer.hideHintMesh();
    }

    @Override
    public void animateHintMatch(Hint hint) {
        hintMeshRenderer.animateHintMatch(hint, screens.findScreens());
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
        if (zoom == null) {
            cancelPendingCapture();
            currentZoom = null;
            if (screenshotWidget != null) {
                screenshotWidget.setZoom(null);
                screenshotWidget.hide();
            }
            return;
        }
        requestScreenshotCapture(zoom.screenRectangle(), zoom);
    }

    @Override
    public void startScreenshotZoomAnimation(Rectangle screenRect, Zoom beginZoom) {
        requestScreenshotCapture(screenRect, beginZoom);
    }

    @Override
    public void updateScreenshotZoom(Zoom zoom) {
        currentZoom = zoom;
        if (screenshotWidget == null)
            return;
        screenshotWidget.setZoom(zoom);
        screenshotWidget.repaint();
        mouseMoved(mouseX(), mouseY());
        setTopmost();
    }

    @Override
    public void endScreenshotZoomAnimation(Zoom finalZoom) {
        if (screenshotWidget == null)
            return;
        if (finalZoom == null) {
            currentZoom = null;
            screenshotWidget.setZoom(null);
            screenshotWidget.hide();
            return;
        }
        currentZoom = finalZoom;
        screenshotWidget.setZoom(finalZoom);
        screenshotWidget.repaint();
        setTopmost();
    }

    /**
     * Hides our own overlay windows (which must not appear in the captured backdrop -
     * Linux has no capture-exclusion API like Windows' WDA_EXCLUDEFROMCAPTURE/
     * MagSetWindowFilterList) and records the capture to perform. The actual
     * grabWindow() call happens on the next tick's update(), once the hide has
     * actually been flushed - see the comment there.
     */
    private void requestScreenshotCapture(Rectangle rect, Zoom zoom) {
        if (pendingCaptureRect == null) {
            pendingCaptureGridWasVisible = gridRenderer != null && gridRenderer.showing();
            pendingCaptureHintWasVisible = hintMeshRenderer.showing();
            if (pendingCaptureGridWasVisible)
                gridRenderer.widget().hide();
            if (pendingCaptureHintWasVisible)
                for (TransparentWindow window : hintMeshRenderer.windows())
                    window.hide();
            if (screenshotWidget != null)
                screenshotWidget.hide();
        }
        pendingCaptureRect = rect;
        pendingCaptureZoom = zoom;
    }

    /** Aborts a capture requested but not yet fulfilled, restoring window visibility. */
    private void cancelPendingCapture() {
        if (pendingCaptureRect == null)
            return;
        if (pendingCaptureGridWasVisible)
            gridRenderer.widget().show();
        if (pendingCaptureHintWasVisible)
            for (TransparentWindow window : hintMeshRenderer.windows())
                window.show();
        pendingCaptureRect = null;
        pendingCaptureZoom = null;
    }

    private void createScreenshotWindow() {
        screenshotWidget = new ScreenshotWidget();
        applyX11OverlayFlags(screenshotWidget);
    }

    /**
     * Applies the X11-specific window flags/attributes every overlay window needs:
     * topmost, bypass-the-window-manager, and click-through. Called on all four window
     * types (indicator, hint-mesh, grid, screenshot) - GridRenderer's internal widget
     * and ScreenshotWidget are shared with Windows and only set FramelessWindowHint
     * themselves (Windows applies its own native WS_EX_* equivalents after construction
     * instead), and even TransparentWindow-based windows need the real X11 shape call
     * below, not just the Qt-level attribute set in its constructor.
     */
    private void applyX11OverlayFlags(QWidget widget) {
        widget.setWindowFlags(Qt.WindowType.FramelessWindowHint,
                Qt.WindowType.X11BypassWindowManagerHint,
                Qt.WindowType.WindowStaysOnTopHint);
        widget.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents);
        makeClickThrough(widget);
    }

    /**
     * Clears the window's X11 input shape so it accepts no mouse/keyboard input
     * anywhere and every click passes through to whatever is behind it. Qt's
     * WA_TransparentForMouseEvents attribute does not reliably achieve this for
     * frameless, override-redirect (X11BypassWindowManagerHint) windows on every
     * window manager - confirmed via hardware testing that it alone was not enough -
     * so the input shape is cleared directly via the XShape extension instead, the
     * same mechanism compositors and other click-through overlay tools rely on.
     * winId() forces the underlying native X11 window to be created if it isn't yet.
     */
    private void makeClickThrough(QWidget widget) {
        LibXShape.INSTANCE.XShapeCombineRectangles(display, widget.winId(),
                LibXShape.ShapeInput, 0, 0, Pointer.NULL, 0, LibXShape.ShapeSet, 0);
    }

    private Rectangle virtualDesktopBounds() {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Screen screen : screens.findScreens()) {
            Rectangle r = screen.rectangle();
            minX = Math.min(minX, r.x());
            minY = Math.min(minY, r.y());
            maxX = Math.max(maxX, r.x() + r.width());
            maxY = Math.max(maxY, r.y() + r.height());
        }
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    @Override
    public boolean waitForZoomBeforeRepainting() {
        return waitForZoom;
    }

    @Override
    public void setWaitForZoomBeforeRepainting(boolean value) {
        this.waitForZoom = value;
    }

}
