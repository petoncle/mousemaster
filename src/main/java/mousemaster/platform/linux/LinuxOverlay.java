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
 * Linux implementation of the Overlay interface.
 * Currently implements basic grid display for Milestone 1.
 * TODO: Full implementation of zoom, hint mesh, and indicator features.
 */
public class LinuxOverlay implements Overlay {

    private static final Logger logger = LoggerFactory.getLogger(LinuxOverlay.class);

    private final Pointer display;
    private Grid currentGrid;
    private boolean showingGrid = false;
    private Runnable messagePump;
    private GridWindow gridWindow;
    private HintMeshWindow hintMeshWindow;
    private HintMesh currentHintMesh;
    private LinuxPlatform platform;

    // TEMPORARY: Test mode to auto-display hints after 1 second
    private double elapsedTime = 0.0;
    private boolean testGridShown = false;
    private boolean testGridHidden = false;

    public LinuxOverlay(Pointer display) {
        this.display = display;
        logger.info("LinuxOverlay initialized");
    }

    public void setPlatform(LinuxPlatform platform) {
        this.platform = platform;
    }

    @Override
    public void update(double delta) {
        elapsedTime += delta;

        // TEMPORARY: Auto-display test grid after 1 second
        if (!testGridShown && elapsedTime >= 1.0) {
            logger.info("TEST MODE: Auto-displaying grid after 1 second");
            displayTestGrid();
            testGridShown = true;
        }

        // TEMPORARY: Auto-hide test hints after 6 seconds (5 seconds after showing)
        if (testGridShown && !testGridHidden && elapsedTime >= 6.0) {
            logger.info("TEST MODE: Auto-hiding hints after 5 seconds of display");
            hideHintMesh();
            testGridHidden = true;
        }
    }

    // TEMPORARY: Test method to display hint mesh without keyboard input
    private void displayTestGrid() {
        java.util.List<Hint> hints = java.util.List.of(
            new Hint(400, 300, 100, 100, java.util.List.of(new Key(null, null, "A"))),
            new Hint(800, 300, 100, 100, java.util.List.of(new Key(null, null, "B"))),
            new Hint(1200, 300, 100, 100, java.util.List.of(new Key(null, null, "C"))),
            new Hint(400, 700, 100, 100, java.util.List.of(new Key(null, null, "D"))),
            new Hint(800, 700, 100, 100, java.util.List.of(new Key(null, null, "E"))),
            new Hint(1200, 700, 100, 100, java.util.List.of(new Key(null, null, "F")))
        );

        HintMesh testHintMesh = new HintMesh(
            true,
            hints,
            0,
            java.util.List.of(),
            null,
            null
        );

        setHintMesh(testHintMesh, null);
        logger.info("TEST MODE: Hint mesh with letters A-F should now be visible on screen");

        if (platform != null) {
            platform.grabKeyboard();
        }
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
        logger.debug("setIndicator() called");
    }

    @Override
    public void hideIndicator(boolean allowFade) {
        // Called every frame when no indicator is active - this is normal
    }

    @Override
    public void setGrid(Grid grid) {
        logger.info("setGrid() called: columns={}, rows={}", grid.columnCount(), grid.rowCount());
        this.currentGrid = grid;
        this.showingGrid = true;

        if (gridWindow == null) {
            gridWindow = new GridWindow();
            logger.debug("Created new GridWindow");
        }

        gridWindow.setGrid(grid);
        logger.info("Grid displayed: {}x{} at ({},{}), size {}x{}",
                grid.columnCount(), grid.rowCount(),
                grid.x(), grid.y(), grid.width(), grid.height());
    }

    @Override
    public void hideGrid() {
        logger.debug("hideGrid() called");
        this.showingGrid = false;
        this.currentGrid = null;

        if (gridWindow != null) {
            gridWindow.clearGrid();
            logger.debug("Grid window hidden");
        }
    }

    @Override
    public void setHintMesh(HintMesh hintMesh, Zoom zoom) {
        logger.debug("setHintMesh() called with {} hints", hintMesh.hints().size());

        this.currentHintMesh = hintMesh;

        if (hintMeshWindow == null) {
            hintMeshWindow = new HintMeshWindow();
            logger.debug("Created new HintMeshWindow");
        }

        hintMeshWindow.setHintMesh(hintMesh);
        logger.info("Hint mesh displayed with {} hints", hintMesh.hints().size());

        if (platform != null) {
            platform.grabKeyboard();
        }
    }

    @Override
    public void setHintMesh(HintMesh hintMesh, Zoom zoom, boolean hintMatch) {
        logger.debug("setHintMesh() called with hintMatch={}", hintMatch);
        setHintMesh(hintMesh, zoom);
    }

    @Override
    public void hideHintMesh() {
        logger.debug("hideHintMesh() called");

        this.currentHintMesh = null;

        if (hintMeshWindow != null) {
            hintMeshWindow.clearHints();
            logger.debug("Hint mesh window hidden");
        }

        if (platform != null) {
            platform.ungrabKeyboard();
        }
    }

    /**
     * TEMPORARY: Test method to handle keypresses while hints are showing.
     * In full implementation, this would go through KeyboardManager.
     */
    public void handleKeyPress(String keyString) {
        if (currentHintMesh == null || !currentHintMesh.visible()) {
            return;
        }

        String key = keyString.toUpperCase();

        for (Hint hint : currentHintMesh.hints()) {
            String hintLabel = getHintLabel(hint);
            if (hintLabel.equals(key)) {
                logger.info("TEST: Hint '{}' selected at position ({}, {})",
                           key, (int) hint.centerX(), (int) hint.centerY());
                if (platform != null) {
                    platform.mouse().synchronousMoveTo((int) hint.centerX(), (int) hint.centerY());
                }
                hideHintMesh();
                return;
            }
        }

        logger.debug("Key '{}' pressed but doesn't match any hint", key);
    }

    private String getHintLabel(Hint hint) {
        if (hint.keySequence().isEmpty()) {
            return "";
        }
        Key key = hint.keySequence().get(0);
        if (key.character() != null) {
            return key.character().toUpperCase();
        }
        return "";
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
