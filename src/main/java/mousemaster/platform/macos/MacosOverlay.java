package mousemaster.platform.macos;

import io.qt.core.QPoint;
import mousemaster.Grid;
import mousemaster.Hint;
import mousemaster.HintMesh;
import mousemaster.HintMeshConfiguration;
import mousemaster.Indicator;
import mousemaster.Point;
import mousemaster.Rectangle;
import mousemaster.Zoom;
import mousemaster.platform.Overlay;
import mousemaster.qt.QtHintFont;
import mousemaster.qt.TransparentWindow;
import mousemaster.renderer.GridRenderer;
import mousemaster.renderer.HintMeshRenderer;
import mousemaster.renderer.IndicatorRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;

public class MacosOverlay implements Overlay {

    private static final Logger logger = LoggerFactory.getLogger(MacosOverlay.class);

    private static final Duration zoomFrameInterval = Duration.ofMillis(16);

    private final MacosMouseController mouse;
    private final HintMeshRenderer hintMeshRenderer;

    private IndicatorRenderer indicatorRenderer;
    private GridRenderer gridRenderer;
    private MacosZoomRenderer zoomRenderer;
    private Zoom currentZoom;
    private long nextZoomFrameNanos;

    public MacosOverlay(MacosMouseController mouse) {
        this.mouse = mouse;
        hintMeshRenderer = new HintMeshRenderer(this::createStyledHintMeshWindow,
                this::hideHintMesh);
    }

    @Override
    public void update(double delta) {
        hintMeshRenderer.runPendingWork();
        if (gridRenderer != null)
            gridRenderer.advanceAnimationsToFirstFrame();
        if (indicatorRenderer != null)
            indicatorRenderer.advanceAnimationsToFirstFrame();
        updateZoomWindow();
    }

    @Override
    public void flushCache() {
        hintMeshRenderer.flushCache();
    }

    /** A window keeps the level it was given, so there is no z-order to put back. */
    @Override
    public void setTopmost() {
    }

    @Override
    public void setMessagePump(Runnable pump) {
        hintMeshRenderer.setMessagePump(pump);
    }

    @Override
    public void preWarmFontsAndWindows(Set<HintMeshConfiguration> hintMeshConfigurations) {
        QtHintFont.preWarm(hintMeshConfigurations);
        logger.debug("Screens " + MacosScreens.screens());
        hintMeshRenderer.preWarmHintMeshWindows(MacosScreens.screens());
        if (indicatorRenderer != null)
            return;
        long before = System.nanoTime();
        createIndicatorWindow();
        indicatorRenderer.preWarm();
        logger.debug("Pre-warmed the indicator window in " +
                     (long) ((System.nanoTime() - before) / 1e6) + "ms");
    }

    private void createIndicatorWindow() {
        indicatorRenderer = new IndicatorRenderer();
        MacosWindow.applyOverlayProperties(indicatorRenderer.window());
    }

    private TransparentWindow createStyledHintMeshWindow() {
        TransparentWindow window = new TransparentWindow();
        MacosWindow.applyOverlayProperties(window);
        return window;
    }

    @Override
    public void preWarmHintMesh(HintMesh hintMesh, Zoom zoom) {
        hintMeshRenderer.preWarmHintMesh(hintMesh, zoom, MacosScreens.screens());
    }

    @Override
    public Rectangle activeWindowRectangle(double windowWidthPercent,
                                           double windowHeightPercent,
                                           int scaledTopInset, int scaledBottomInset,
                                           int scaledLeftInset, int scaledRightInset) {
        Rectangle window = MacosAccessibility.focusedWindowFrame();
        if (window == null)
            return MacosScreens.findActiveScreen(mouse.findMousePosition()).rectangle();
        int noInsetGridWidth =
                Math.max(1, (int) (window.width() * windowWidthPercent));
        int gridWidth =
                Math.max(1, noInsetGridWidth - scaledLeftInset - scaledRightInset);
        int noInsetGridHeight =
                Math.max(1, (int) (window.height() * windowHeightPercent));
        int gridHeight =
                Math.max(1, noInsetGridHeight - scaledTopInset - scaledBottomInset);
        return new Rectangle(
                Math.min(window.x() + window.width(),
                        window.x() + scaledLeftInset +
                        (window.width() - noInsetGridWidth) / 2),
                Math.min(window.y() + window.height(),
                        window.y() + scaledTopInset +
                        (window.height() - noInsetGridHeight) / 2),
                gridWidth, gridHeight);
    }

    @Override
    public void setIndicator(Indicator indicator, boolean fadeAnimationEnabled,
                             Duration fadeAnimationDuration, boolean allowFade,
                             boolean renderAsCursor, boolean includeCursorGlyph) {
        if (indicatorRenderer == null)
            createIndicatorWindow();
        QPoint mousePosition = mouse.findMousePosition();
        indicatorRenderer.setIndicator(indicator, fadeAnimationEnabled,
                fadeAnimationDuration, allowFade, cursorRectangle(mousePosition),
                MacosCursor.visualCenter(), MacosScreens.findActiveScreen(mousePosition), null);
    }

    @Override
    public void hideIndicator(boolean allowFade) {
        if (indicatorRenderer != null)
            indicatorRenderer.hide(allowFade);
    }

    public void mouseMoved(QPoint mousePosition) {
        reposition(mousePosition);
    }

    private void reposition(QPoint mousePosition) {
        if (indicatorRenderer == null || indicatorRenderer.currentIndicator() == null)
            return;
        indicatorRenderer.reposition(cursorRectangle(mousePosition), MacosCursor.visualCenter(),
                MacosScreens.findActiveScreen(mousePosition), null);
    }

    private static Rectangle cursorRectangle(QPoint mousePosition) {
        Point size = MacosCursor.size();
        return new Rectangle(mousePosition.x(), mousePosition.y(), (int) size.x(),
                (int) size.y());
    }

    @Override
    public void setGrid(Grid grid) {
        if (gridRenderer == null) {
            gridRenderer = new GridRenderer();
            MacosWindow.applyOverlayProperties(gridRenderer.widget());
        }
        gridRenderer.setGrid(MacosScreens.logicalGrid(grid),
                MacosScreens.logicalVirtualDesktopBounds(),
                (int) Math.floor(grid.lineThickness()));
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
        hintMeshRenderer.setHintMesh(hintMesh, zoom, hintMatch, true,
                MacosScreens.screens());
    }

    /** The mesh was concealed while the zoom moved, so putting it back is not it appearing. */
    @Override
    public void restoreHintMesh(HintMesh hintMesh, Zoom zoom) {
        hintMeshRenderer.setHintMesh(hintMesh, zoom, false, false,
                MacosScreens.screens());
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
    public void animateHintMatch(Hint hint) {
        hintMeshRenderer.animateHintMatch(hint, MacosScreens.screens());
    }

    @Override
    public void setZoom(Zoom zoom) {
        if (currentZoom != null && currentZoom.equals(zoom))
            return;
        currentZoom = zoom;
        if (currentZoom != null)
            nextZoomFrameNanos = System.nanoTime();
        else if (zoomRenderer != null)
            zoomRenderer.hide();
    }

    /**
     * The loop iterates far more often than the screen refreshes, and every frame captures
     * and hands over a screen sized image over whatever else is drawing.
     */
    private void updateZoomWindow() {
        if (currentZoom == null || System.nanoTime() < nextZoomFrameNanos)
            return;
        if (zoomRenderer == null)
            zoomRenderer = new MacosZoomRenderer();
        long before = System.nanoTime();
        zoomRenderer.render(currentZoom);
        long after = System.nanoTime();
        // Capturing over a busy compositor takes tens of milliseconds and holds up the
        // keyboard with it, so give the loop as long as the frame cost before asking again.
        nextZoomFrameNanos =
                after + Math.max(zoomFrameInterval.toNanos(), after - before);
    }

    /** The window server composites a whole frame at once, so nothing tears. */
    @Override
    public boolean waitForZoomBeforeRepainting() {
        return false;
    }

    @Override
    public void setWaitForZoomBeforeRepainting(boolean value) {
    }

}
