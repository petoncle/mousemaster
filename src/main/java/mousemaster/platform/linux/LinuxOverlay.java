package mousemaster.platform.linux;

import com.sun.jna.Pointer;
import mousemaster.*;
import mousemaster.platform.Overlay;
import mousemaster.qt.GridWindow;
import mousemaster.qt.HintMeshWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;

/**
 * Linux overlay implementation. Delegates rendering to Qt-based GridWindow and HintMeshWindow.
 * Zoom and indicator features are stubs pending future implementation.
 */
public class LinuxOverlay implements Overlay {

    private static final Logger logger = LoggerFactory.getLogger(LinuxOverlay.class);

    private final Pointer display;
    private Runnable messagePump;
    private GridWindow gridWindow;
    private HintMeshWindow hintMeshWindow;

    public LinuxOverlay(Pointer display) {
        this.display = display;
    }

    @Override
    public void update(double delta) {
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
        logger.debug("setZoom() called");
    }

    @Override
    public void startScreenshotZoomAnimation(Rectangle screenRect, Zoom beginZoom) {
        logger.debug("startScreenshotZoomAnimation() called");
    }

    @Override
    public void updateScreenshotZoom(Zoom zoom) {
        logger.debug("updateScreenshotZoom() called");
    }

    @Override
    public void endScreenshotZoomAnimation(Zoom finalZoom) {
        logger.debug("endScreenshotZoomAnimation() called");
    }

    @Override
    public boolean waitForZoomBeforeRepainting() {
        return false;
    }

    @Override
    public void setWaitForZoomBeforeRepainting(boolean value) {
    }

}
