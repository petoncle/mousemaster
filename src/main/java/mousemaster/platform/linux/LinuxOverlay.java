package mousemaster.platform.linux;

import com.sun.jna.Pointer;
import io.qt.gui.QPixmap;
import io.qt.widgets.QApplication;
import mousemaster.*;
import mousemaster.platform.Overlay;
import mousemaster.qt.GridWindow;
import mousemaster.qt.HintMeshWindow;
import mousemaster.qt.ZoomWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;

/**
 * Linux overlay implementation. Delegates rendering to Qt-based GridWindow and HintMeshWindow.
 * Zoom is implemented via a captured screenshot rendered magnified in ZoomWindow, since
 * Linux has no equivalent to the Win32 Magnification API. Indicator is still a stub
 * pending future implementation.
 */
public class LinuxOverlay implements Overlay {

    private static final Logger logger = LoggerFactory.getLogger(LinuxOverlay.class);

    private final Pointer display;
    private Runnable messagePump;
    private GridWindow gridWindow;
    private HintMeshWindow hintMeshWindow;
    private ZoomWindow zoomWindow;

    private Rectangle pendingCaptureRect;
    private Zoom pendingCaptureZoom;
    private boolean pendingCaptureGridWasVisible;
    private boolean pendingCaptureHintWasVisible;

    public LinuxOverlay(Pointer display) {
        this.display = display;
    }

    @Override
    public void update(double delta) {
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
            gridWindow.show();
        if (pendingCaptureHintWasVisible)
            hintMeshWindow.show();
        if (zoomWindow == null)
            zoomWindow = new ZoomWindow();
        zoomWindow.setScreenshot(pixmap, pendingCaptureRect);
        zoomWindow.setZoom(pendingCaptureZoom);
        zoomWindow.show();
        raiseOverlayWindows();
        pendingCaptureRect = null;
        pendingCaptureZoom = null;
    }

    @Override
    public void flushCache() {
        // TODO: Implement cache flushing if needed
    }

    @Override
    public void setTopmost() {
        logger.debug("setTopmost() called - TODO: implement X11 always-on-top");
    }

    @Override
    public void setMessagePump(Runnable pump) {
        this.messagePump = pump;
    }

    @Override
    public void preWarmFontStyles(Set<HintMeshConfiguration> configs) {
        logger.debug("preWarmFontStyles() called with {} configs", configs.size());
    }

    @Override
    public void preWarmHintMeshWindows() {
        logger.debug("preWarmHintMeshWindows() called");
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
    }

    @Override
    public void hideIndicator(boolean allowFade) {
        // Called every frame when no indicator is active - this is normal
    }

    @Override
    public void setGrid(Grid grid) {
        if (gridWindow == null)
            gridWindow = new GridWindow();
        gridWindow.setGrid(grid);
        gridWindow.raise();
        logger.debug("Grid displayed: {}x{} at ({},{}) size {}x{}",
                grid.columnCount(), grid.rowCount(),
                grid.x(), grid.y(), grid.width(), grid.height());
    }

    @Override
    public void hideGrid() {
        if (gridWindow != null)
            gridWindow.clearGrid();
    }

    @Override
    public void setHintMesh(HintMesh hintMesh, Zoom zoom) {
        if (hintMeshWindow == null)
            hintMeshWindow = new HintMeshWindow();
        hintMeshWindow.setHintMesh(hintMesh);
        hintMeshWindow.raise();
        logger.debug("Hint mesh displayed with {} hints", hintMesh.hints().size());
    }

    @Override
    public void setHintMesh(HintMesh hintMesh, Zoom zoom, boolean hintMatch) {
        setHintMesh(hintMesh, zoom);
    }

    @Override
    public void hideHintMesh() {
        if (hintMeshWindow != null)
            hintMeshWindow.clearHints();
    }

    @Override
    public void animateHintMatch(Hint hint) {
        logger.debug("animateHintMatch() called");
    }

    @Override
    public void setZoom(Zoom zoom) {
        if (zoom == null) {
            cancelPendingCapture();
            if (zoomWindow != null)
                zoomWindow.clear();
            return;
        }
        requestScreenshotCapture(zoom.screenRectangle(), zoom);
        logger.debug("Zoom requested: {}x at {}", zoom.percent(), zoom.center());
    }

    @Override
    public void startScreenshotZoomAnimation(Rectangle screenRect, Zoom beginZoom) {
        requestScreenshotCapture(screenRect, beginZoom);
        logger.debug("Screenshot zoom animation requested at {}x", beginZoom.percent());
    }

    @Override
    public void updateScreenshotZoom(Zoom zoom) {
        if (zoomWindow == null)
            return;
        zoomWindow.setZoom(zoom);
        zoomWindow.update();
    }

    @Override
    public void endScreenshotZoomAnimation(Zoom finalZoom) {
        if (zoomWindow == null)
            return;
        if (finalZoom == null) {
            zoomWindow.clear();
            return;
        }
        zoomWindow.setZoom(finalZoom);
        zoomWindow.update();
        raiseOverlayWindows();
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
            pendingCaptureGridWasVisible = gridWindow != null && gridWindow.isVisible();
            pendingCaptureHintWasVisible =
                    hintMeshWindow != null && hintMeshWindow.isVisible();
            if (pendingCaptureGridWasVisible)
                gridWindow.hide();
            if (pendingCaptureHintWasVisible)
                hintMeshWindow.hide();
            if (zoomWindow != null)
                zoomWindow.hide();
        }
        pendingCaptureRect = rect;
        pendingCaptureZoom = zoom;
    }

    /** Aborts a capture requested but not yet fulfilled, restoring window visibility. */
    private void cancelPendingCapture() {
        if (pendingCaptureRect == null)
            return;
        if (pendingCaptureGridWasVisible)
            gridWindow.show();
        if (pendingCaptureHintWasVisible)
            hintMeshWindow.show();
        pendingCaptureRect = null;
        pendingCaptureZoom = null;
    }

    /** Keeps hints/grid stacked above the zoom backdrop. */
    private void raiseOverlayWindows() {
        if (gridWindow != null)
            gridWindow.raise();
        if (hintMeshWindow != null)
            hintMeshWindow.raise();
    }

    @Override
    public boolean waitForZoomBeforeRepainting() {
        return false;
    }

    @Override
    public void setWaitForZoomBeforeRepainting(boolean value) {
    }

}
