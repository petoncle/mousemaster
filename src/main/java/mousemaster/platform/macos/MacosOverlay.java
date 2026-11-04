package mousemaster.platform.macos;

import io.qt.core.QPoint;
import mousemaster.Hint;
import mousemaster.HintGridArea;
import mousemaster.HintMesh;
import mousemaster.HintMeshConfiguration;
import mousemaster.HintRectangles;
import mousemaster.IndicatorConfiguration;
import mousemaster.IndicatorLayerCopyKey;
import mousemaster.IndicatorPosition;
import mousemaster.Point;
import mousemaster.Rectangle;
import mousemaster.Screen;
import mousemaster.Zoom;
import mousemaster.platform.Overlay;
import mousemaster.platform.DesktopCapture;
import mousemaster.qt.QtHintFont;
import mousemaster.qt.TransparentWindow;
import mousemaster.renderer.HintMeshRenderer;
import mousemaster.renderer.IndicatorRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MacosOverlay implements Overlay {

    private static final Logger logger = LoggerFactory.getLogger(MacosOverlay.class);

    private static final Duration zoomFrameInterval = Duration.ofMillis(16);

    private final MacosMouseController mouse;
    private final HintMeshRenderer hintMeshRenderer;
    private final List<MacosDesktopCapture> desktopCaptures = new ArrayList<>();

    private IndicatorRenderer followingIndicatorRenderer;
    private final List<IndicatorRenderer> followingIndicatorRenderers = new ArrayList<>();
    private final List<IndicatorRenderer> anchoredIndicatorRenderers = new ArrayList<>();
    private final Map<IndicatorLayerCopyKey, IndicatorRenderer>
            anchoredIndicatorRendererByCopyKey = new LinkedHashMap<>();
    private List<IndicatorLayerCopyKey> copyKeys = List.of();
    private List<IndicatorRenderer.WindowLayerGroup> currentWindowLayerGroups = List.of();
    private List<IndicatorRenderer> orderedIndicatorRenderers = List.of();
    private final Map<IndicatorRenderer, Long> levelByIndicatorRenderer = new HashMap<>();
    private boolean indicatorExcludedFromCapture;
    private boolean hintMeshExcludedFromCapture;
    private MacosZoomRenderer zoomRenderer;
    private Zoom currentZoom;
    private long nextZoomFrameNanos;

    public MacosOverlay(MacosMouseController mouse) {
        this.mouse = mouse;
        hintMeshRenderer = new HintMeshRenderer(this::createStyledHintMeshWindow,
                this::hideHintMesh);
    }

    @Override
    public void runPendingHintMeshWork() {
        hintMeshRenderer.runPendingWork();
    }

    @Override
    public void update(double delta) {
        hintMeshRenderer.runPendingWork();
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
    public DesktopCapture captureDesktop(Rectangle bounds, int scaledWidth,
                                         int scaledHeight) {
        return new DesktopCapture(bounds,
                captureCovering(bounds).capture(bounds, scaledWidth, scaledHeight),
                scaledWidth, scaledHeight);
    }

    private MacosDesktopCapture captureCovering(Rectangle bounds) {
        for (MacosDesktopCapture capture : desktopCaptures)
            if (capture.covers(bounds))
                return capture;
        MacosDesktopCapture capture = new MacosDesktopCapture(bounds);
        desktopCaptures.add(capture);
        return capture;
    }

    @Override
    public void preWarmFontsAndWindows(Set<HintMeshConfiguration> hintMeshConfigurations) {
        QtHintFont.preWarm(hintMeshConfigurations);
        logger.debug("Screens " + MacosScreens.screens());
        hintMeshRenderer.preWarmHintMeshWindows(MacosScreens.screens());
        if (followingIndicatorRenderer != null)
            return;
        long before = System.nanoTime();
        createIndicatorWindows();
        for (IndicatorRenderer indicatorRenderer : indicatorRenderers())
            indicatorRenderer.preWarm();
        logger.debug("Pre-warmed the indicator window in " +
                     (long) ((System.nanoTime() - before) / 1e6) + "ms");
    }

    private void createIndicatorWindows() {
        followingIndicatorRenderer = createIndicatorRenderer(new IndicatorRenderer());
        followingIndicatorRenderers.add(followingIndicatorRenderer);
    }

    private List<IndicatorRenderer> indicatorRenderers() {
        List<IndicatorRenderer> indicatorRenderers =
                new ArrayList<>(followingIndicatorRenderers);
        indicatorRenderers.addAll(anchoredIndicatorRenderers);
        indicatorRenderers.addAll(anchoredIndicatorRendererByCopyKey.values());
        return indicatorRenderers;
    }

    private IndicatorRenderer anchoredIndicatorRenderer(IndicatorLayerCopyKey copyKey) {
        return anchoredIndicatorRendererByCopyKey.computeIfAbsent(copyKey,
                copyKey_ -> createIndicatorRenderer(new IndicatorRenderer()));
    }

    private IndicatorRenderer followingIndicatorRenderer(int index) {
        while (followingIndicatorRenderers.size() <= index)
            followingIndicatorRenderers.add(
                    createIndicatorRenderer(new IndicatorRenderer(followingIndicatorRenderer)));
        return followingIndicatorRenderers.get(index);
    }

    private IndicatorRenderer anchoredIndicatorRenderer(int index) {
        while (anchoredIndicatorRenderers.size() <= index)
            anchoredIndicatorRenderers.add(
                    createIndicatorRenderer(new IndicatorRenderer(followingIndicatorRenderer)));
        return anchoredIndicatorRenderers.get(index);
    }

    private IndicatorRenderer createIndicatorRenderer(IndicatorRenderer indicatorRenderer) {
        MacosWindow.applyOverlayProperties(indicatorRenderer.window(),
                MacosWindow.aboveOverlayLevel);
        levelByIndicatorRenderer.put(indicatorRenderer, MacosWindow.aboveOverlayLevel);
        MacosWindow.setExcludedFromCapture(indicatorRenderer.window(),
                indicatorExcludedFromCapture);
        return indicatorRenderer;
    }

    private IndicatorRenderer atLevel(IndicatorRenderer indicatorRenderer, long level) {
        if (levelByIndicatorRenderer.get(indicatorRenderer) != level) {
            MacosWindow.applyOverlayProperties(indicatorRenderer.window(), level);
            levelByIndicatorRenderer.put(indicatorRenderer, level);
        }
        return indicatorRenderer;
    }

    private TransparentWindow createStyledHintMeshWindow() {
        TransparentWindow window = new TransparentWindow();
        MacosWindow.applyOverlayProperties(window);
        MacosWindow.setExcludedFromCapture(window, hintMeshExcludedFromCapture);
        return window;
    }

    @Override
    public void preWarmHintMesh(HintMesh hintMesh, Zoom zoom) {
        hintMeshRenderer.preWarmHintMesh(hintMesh, zoom, MacosScreens.screens());
    }

    @Override
    public Rectangle activeWindowRectangle() {
        Rectangle window = MacosAccessibility.focusedWindowFrame();
        if (window == null)
            return MacosScreens.findActiveScreen(mouse.findMousePosition()).rectangle();
        return window;
    }

    @Override
    public void setIndicator(IndicatorConfiguration indicator,
                             Map<IndicatorLayerCopyKey, IndicatorConfiguration> indicatorByCopyKey,
                             Set<String> layerNamesToAnchor,
                             Map<IndicatorPosition, List<Point>> pointsByPosition,
                             Map<HintGridArea, Rectangle> rectangleByArea,
                             Map<Point, HintRectangles> hintRectanglesByPoint,
                             int overlayZ, boolean animating,
                             boolean includeOriginalCursor) {
        if (followingIndicatorRenderer == null)
            createIndicatorWindows();
        if (indicator.excludeFromCapture() != indicatorExcludedFromCapture) {
            indicatorExcludedFromCapture = indicator.excludeFromCapture();
            for (IndicatorRenderer indicatorRenderer : indicatorRenderers())
                MacosWindow.setExcludedFromCapture(indicatorRenderer.window(),
                        indicatorExcludedFromCapture);
        }
        QPoint mousePosition = mouse.findMousePosition();
        for (IndicatorLayerCopyKey copyKey : indicatorByCopyKey.keySet())
            if (!copyKeys.contains(copyKey))
                anchoredIndicatorRenderer(copyKey).anchor(copyKey.layerName(),
                        followingIndicatorRenderer);
        List<IndicatorRenderer.WindowLayerGroup> windowLayerGroups =
                IndicatorRenderer.windowLayerGroups(indicator, indicatorByCopyKey,
                        currentWindowLayerGroups, layerNamesToAnchor, overlayZ);
        List<IndicatorRenderer> showingIndicatorRenderers = indicatorRenderers().stream()
                .filter(IndicatorRenderer::showing).toList();
        List<IndicatorRenderer> orderedIndicatorRenderers = new ArrayList<>();
        int followingIndex = 0;
        int runIndex = 0;
        for (IndicatorRenderer.WindowLayerGroup windowLayerGroup : windowLayerGroups) {
            long level = windowLayerGroup.belowOverlay() ? MacosWindow.belowOverlayLevel :
                    MacosWindow.aboveOverlayLevel;
            for (IndicatorLayerCopyKey copyKey : windowLayerGroup.copyKeys()) {
                IndicatorRenderer indicatorRenderer =
                        anchoredIndicatorRendererByCopyKey.get(copyKey);
                setIndicator(indicatorRenderer, indicatorByCopyKey.get(copyKey), Set.of(),
                        Map.of(), Map.of(), Map.of(), mousePosition);
                orderedIndicatorRenderers.add(atLevel(indicatorRenderer, level));
            }
            for (IndicatorConfiguration windowIndicator : windowLayerGroup.indicators()) {
                IndicatorRenderer indicatorRenderer = windowLayerGroup.followMouse() ?
                        followingIndicatorRenderer(followingIndex++) :
                        anchoredIndicatorRenderer(runIndex++);
                if (windowLayerGroup.followMouse())
                    setIndicator(indicatorRenderer, windowIndicator, Set.of(), Map.of(),
                            Map.of(), Map.of(), mousePosition);
                else
                    setIndicator(indicatorRenderer, windowIndicator, layerNamesToAnchor,
                            pointsByPosition, rectangleByArea, hintRectanglesByPoint,
                            mousePosition);
                orderedIndicatorRenderers.add(atLevel(indicatorRenderer, level));
            }
        }
        for (IndicatorRenderer indicatorRenderer : indicatorRenderers())
            if (!orderedIndicatorRenderers.contains(indicatorRenderer))
                indicatorRenderer.hide();
        // Within a level, the window ordered front last is drawn on top. Ordering a hidden
        // window front shows it.
        if (!orderedIndicatorRenderers.equals(this.orderedIndicatorRenderers) ||
            !showingIndicatorRenderers.containsAll(orderedIndicatorRenderers))
            for (IndicatorRenderer indicatorRenderer : orderedIndicatorRenderers)
                if (indicatorRenderer.showing())
                    MacosWindow.orderFront(indicatorRenderer.window());
        copyKeys = List.copyOf(indicatorByCopyKey.keySet());
        currentWindowLayerGroups = windowLayerGroups;
        this.orderedIndicatorRenderers = orderedIndicatorRenderers;
    }

    @Override
    public Rectangle hintBoxRectangle(Hint hint) {
        return hintMeshRenderer.hintBoxRectangle(hint);
    }

    private void setIndicator(IndicatorRenderer indicatorRenderer,
                              IndicatorConfiguration indicator, Set<String> layerNamesToAnchor,
                              Map<IndicatorPosition, List<Point>> pointsByPosition,
                              Map<HintGridArea, Rectangle> rectangleByArea,
                              Map<Point, HintRectangles> hintRectanglesByPoint,
                              QPoint mousePosition) {
        if (!indicator.enabled()) {
            indicatorRenderer.hide();
            return;
        }
        Screen activeScreen = MacosScreens.findActiveScreen(mousePosition);
        indicatorRenderer.setIndicator(indicator, layerNamesToAnchor, pointsByPosition,
                rectangleByArea, hintRectanglesByPoint, cursorRectangle(mousePosition, activeScreen),
                cursorVisualCenter(activeScreen), activeScreen, null,
                hintMeshRenderer.lastSelectedHintBoxHexColor());
    }

    @Override
    public void hideIndicator() {
        for (IndicatorRenderer indicatorRenderer : indicatorRenderers())
            indicatorRenderer.hide();
    }

    public void repositionIndicator(QPoint mousePosition) {
        Screen activeScreen = MacosScreens.findActiveScreen(mousePosition);
        for (IndicatorRenderer indicatorRenderer : indicatorRenderers())
            if (indicatorRenderer.showing())
                indicatorRenderer.reposition(cursorRectangle(mousePosition, activeScreen),
                        cursorVisualCenter(activeScreen), activeScreen, null,
                        hintMeshRenderer.lastSelectedHintBoxHexColor());
    }

    /** The cursor is measured in points, and the indicator it positions is in pixels. */
    private static Rectangle cursorRectangle(QPoint mousePosition, Screen activeScreen) {
        Point size = MacosCursor.size();
        return new Rectangle(mousePosition.x(), mousePosition.y(),
                (int) Math.round(size.x() * activeScreen.scale()),
                (int) Math.round(size.y() * activeScreen.scale()));
    }

    private static Point cursorVisualCenter(Screen activeScreen) {
        Point visualCenter = MacosCursor.visualCenter();
        return new Point(visualCenter.x() * activeScreen.scale(),
                visualCenter.y() * activeScreen.scale());
    }

    @Override
    public void setHintMesh(HintMesh hintMesh, Zoom zoom) {
        setHintMesh(hintMesh, zoom, false);
    }

    @Override
    public void setHintMesh(HintMesh hintMesh, Zoom zoom, boolean hintMatch) {
        if (hintMesh.excludeFromCapture() != hintMeshExcludedFromCapture) {
            hintMeshExcludedFromCapture = hintMesh.excludeFromCapture();
            for (TransparentWindow window : hintMeshRenderer.windows())
                MacosWindow.setExcludedFromCapture(window, hintMeshExcludedFromCapture);
        }
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
    public boolean hintMeshBuildPending() {
        return hintMeshRenderer.buildPending();
    }

    @Override
    public void animateHintMatch(Hint hint) {
        hintMeshRenderer.animateHintMatch(hint, MacosScreens.screens());
    }

    @Override
    public void stopHintMatchAnimation() {
        hintMeshRenderer.stopHintMatchAnimation();
    }

    @Override
    public boolean canZoom(Rectangle screenRectangle) {
        return true;
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
