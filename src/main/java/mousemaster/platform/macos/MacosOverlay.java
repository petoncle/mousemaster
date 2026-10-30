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
    private final Map<String, IndicatorRenderer> anchoredIndicatorRendererByLayerName =
            new LinkedHashMap<>();
    private List<String> anchoredLayerNames = List.of();
    private final Map<IndicatorLayerCopyKey, IndicatorRenderer>
            anchoredIndicatorRendererByCopyKey = new LinkedHashMap<>();
    private List<IndicatorLayerCopyKey> copyKeys = List.of();
    private boolean indicatorExcludedFromCapture;
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
        followingIndicatorRenderer = new IndicatorRenderer();
        MacosWindow.applyOverlayProperties(followingIndicatorRenderer.window());
    }

    private List<IndicatorRenderer> indicatorRenderers() {
        List<IndicatorRenderer> indicatorRenderers = new ArrayList<>();
        if (followingIndicatorRenderer != null)
            indicatorRenderers.add(followingIndicatorRenderer);
        indicatorRenderers.addAll(anchoredIndicatorRendererByLayerName.values());
        indicatorRenderers.addAll(anchoredIndicatorRendererByCopyKey.values());
        return indicatorRenderers;
    }

    private IndicatorRenderer anchoredIndicatorRenderer(String layerName) {
        return anchoredIndicatorRendererByLayerName.computeIfAbsent(layerName,
                layerName_ -> createAnchoredIndicatorRenderer());
    }

    private IndicatorRenderer anchoredIndicatorRenderer(IndicatorLayerCopyKey copyKey) {
        return anchoredIndicatorRendererByCopyKey.computeIfAbsent(copyKey,
                copyKey_ -> createAnchoredIndicatorRenderer());
    }

    private IndicatorRenderer createAnchoredIndicatorRenderer() {
        IndicatorRenderer indicatorRenderer = new IndicatorRenderer();
        MacosWindow.applyOverlayProperties(indicatorRenderer.window(),
                MacosWindow.aboveOverlaysLevel);
        MacosWindow.setExcludedFromCapture(indicatorRenderer.window(),
                indicatorExcludedFromCapture);
        return indicatorRenderer;
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
                             boolean animating,
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
        setIndicator(followingIndicatorRenderer,
                IndicatorRenderer.followingMouse(indicator), Set.of(), Map.of(),
                Map.of(), Map.of(), mousePosition);
        for (IndicatorLayerCopyKey copyKey : indicatorByCopyKey.keySet())
            if (!copyKeys.contains(copyKey)) {
                IndicatorRenderer layerRenderer = anchoredIndicatorRenderer(copyKey.layerName());
                anchoredIndicatorRendererByLayerName.put(copyKey.layerName(),
                        anchoredIndicatorRenderer(copyKey));
                anchoredIndicatorRendererByCopyKey.put(copyKey, layerRenderer);
            }
        for (Map.Entry<IndicatorLayerCopyKey, IndicatorRenderer> entry :
                anchoredIndicatorRendererByCopyKey.entrySet())
            if (!indicatorByCopyKey.containsKey(entry.getKey()))
                entry.getValue().hide();
        for (Map.Entry<IndicatorLayerCopyKey, IndicatorConfiguration> entry :
                indicatorByCopyKey.entrySet())
            setIndicator(anchoredIndicatorRendererByCopyKey.get(entry.getKey()),
                    entry.getValue(), Set.of(), Map.of(), Map.of(), Map.of(), mousePosition);
        Map<String, IndicatorConfiguration> anchored = IndicatorRenderer.anchoredLayers(indicator);
        for (Map.Entry<String, IndicatorRenderer> entry :
                anchoredIndicatorRendererByLayerName.entrySet())
            if (!anchored.containsKey(entry.getKey()))
                entry.getValue().hide();
        for (Map.Entry<String, IndicatorConfiguration> entry : anchored.entrySet())
            setIndicator(anchoredIndicatorRenderer(entry.getKey()), entry.getValue(),
                    layerNamesToAnchor, pointsByPosition, rectangleByArea, hintRectanglesByPoint,
                    mousePosition);
        // The windows share a level: the one ordered front last is drawn on top. Ordering a
        // hidden window front shows it.
        if (!copyKeys.equals(List.copyOf(indicatorByCopyKey.keySet())) ||
            !anchoredLayerNames.equals(List.copyOf(anchored.keySet()))) {
            for (IndicatorLayerCopyKey copyKey : indicatorByCopyKey.keySet())
                if (anchoredIndicatorRendererByCopyKey.get(copyKey).showing())
                    MacosWindow.orderFront(
                            anchoredIndicatorRendererByCopyKey.get(copyKey).window());
            for (String layerName : anchored.keySet())
                MacosWindow.orderFront(
                        anchoredIndicatorRendererByLayerName.get(layerName).window());
        }
        copyKeys = List.copyOf(indicatorByCopyKey.keySet());
        anchoredLayerNames = List.copyOf(anchored.keySet());
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
