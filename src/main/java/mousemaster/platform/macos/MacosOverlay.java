package mousemaster.platform.macos;

import io.qt.core.QPoint;
import mousemaster.Hint;
import mousemaster.Area;
import mousemaster.HintMesh;
import mousemaster.HintMeshConfiguration;
import mousemaster.HintRectangles;
import mousemaster.IndicatorConfiguration;
import mousemaster.IndicatorLayerCopyKey;
import mousemaster.IndicatorPosition;
import mousemaster.Mousemaster;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MacosOverlay implements Overlay {

    private static final Logger logger = LoggerFactory.getLogger(MacosOverlay.class);

    private final MacosMouseController mouse;
    private final HintMeshRenderer hintMeshRenderer;
    private final List<MacosDesktopCapture> desktopCaptures = new ArrayList<>();

    private IndicatorRenderer followingIndicatorRenderer;
    private final List<IndicatorRenderer> followingIndicatorRenderers = new ArrayList<>();
    private final List<IndicatorRenderer> anchoredIndicatorRenderers = new ArrayList<>();
    private final Map<IndicatorLayerCopyKey, IndicatorRenderer>
            anchoredIndicatorRendererByCopyKey = new LinkedHashMap<>();
    private final List<MacosIndicatorWindow> followingIndicatorWindows = new ArrayList<>();
    private final List<MacosIndicatorWindow> anchoredIndicatorWindows = new ArrayList<>();
    private Map<IndicatorLayerCopyKey, IndicatorConfiguration> indicatorByCopyKey = Map.of();
    private List<IndicatorRenderer.WindowLayerGroup> currentWindowLayerGroups = List.of();
    private List<MacosIndicatorWindow> orderedIndicatorWindows = List.of();
    private final Map<MacosIndicatorWindow, Long> levelByIndicatorWindow = new HashMap<>();
    private boolean indicatorExcludedFromCapture;
    private boolean hintMeshExcludedFromCapture;
    private MacosZoomRenderer zoomRenderer;
    private Zoom currentZoom;
    private double zoomFrameWait;

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
        hintMeshRenderer.update(delta);
        hintMeshRenderer.runPendingWork();
        updateZoomWindow(delta);
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
        followingIndicatorRenderer.preWarm();
        logger.debug("Pre-warmed the indicator window in " +
                     (long) ((System.nanoTime() - before) / 1e6) + "ms");
    }

    private void createIndicatorWindows() {
        followingIndicatorRenderer = new IndicatorRenderer();
        followingIndicatorRenderers.add(followingIndicatorRenderer);
        followingIndicatorWindows.add(createIndicatorWindow());
        anchoredIndicatorWindows.add(createIndicatorWindow());
    }

    private List<MacosIndicatorWindow> indicatorWindows() {
        List<MacosIndicatorWindow> indicatorWindows = new ArrayList<>(followingIndicatorWindows);
        indicatorWindows.addAll(anchoredIndicatorWindows);
        return indicatorWindows;
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

    private MacosIndicatorWindow createIndicatorWindow() {
        MacosIndicatorWindow indicatorWindow = new MacosIndicatorWindow();
        MacosWindow.applyOverlayProperties(indicatorWindow, MacosWindow.aboveOverlayLevel);
        levelByIndicatorWindow.put(indicatorWindow, MacosWindow.aboveOverlayLevel);
        MacosWindow.setExcludedFromCapture(indicatorWindow, indicatorExcludedFromCapture);
        indicatorWindow.show(List.of(),
                MacosScreens.findActiveScreen(mouse.findMousePosition()));
        return indicatorWindow;
    }

    private MacosIndicatorWindow atLevel(MacosIndicatorWindow indicatorWindow, long level) {
        if (levelByIndicatorWindow.get(indicatorWindow) != level) {
            MacosWindow.applyOverlayProperties(indicatorWindow, level);
            levelByIndicatorWindow.put(indicatorWindow, level);
        }
        return indicatorWindow;
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
                             Map<Area, Rectangle> rectangleByArea,
                             Map<Point, HintRectangles> hintRectanglesByPoint,
                             int overlayZ, boolean animating,
                             boolean includeOriginalCursor) {
        if (followingIndicatorRenderer == null)
            createIndicatorWindows();
        if (indicator.excludeFromCapture() != indicatorExcludedFromCapture) {
            indicatorExcludedFromCapture = indicator.excludeFromCapture();
            for (MacosIndicatorWindow indicatorWindow : indicatorWindows())
                MacosWindow.setExcludedFromCapture(indicatorWindow, indicatorExcludedFromCapture);
        }
        List<IndicatorRenderer.WindowLayerGroup> windowLayerGroups =
                IndicatorRenderer.windowLayerGroups(indicator, indicatorByCopyKey,
                        currentWindowLayerGroups, layerNamesToAnchor, overlayZ);
        if (windowLayerGroups.equals(currentWindowLayerGroups) && indicatorByCopyKey.isEmpty() &&
            this.indicatorByCopyKey.isEmpty() && layerNamesToAnchor.isEmpty())
            return;
        for (IndicatorLayerCopyKey copyKey : indicatorByCopyKey.keySet())
            if (!this.indicatorByCopyKey.containsKey(copyKey))
                anchoredIndicatorRendererByCopyKey
                        .computeIfAbsent(copyKey, copyKey_ -> new IndicatorRenderer())
                        .anchor(copyKey.layerName(), followingIndicatorRenderer);
        showIndicator(windowLayerGroups, new LinkedHashMap<>(indicatorByCopyKey),
                layerNamesToAnchor, pointsByPosition, rectangleByArea, hintRectanglesByPoint,
                mouse.findMousePosition());
    }

    @Override
    public Rectangle hintBoxRectangle(Hint hint) {
        return hintMeshRenderer.hintBoxRectangle(hint);
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
            Map<Point, HintRectangles> hintRectanglesByPoint, QPoint mousePosition) {
        Screen activeScreen = MacosScreens.findActiveScreen(mousePosition);
        List<MacosIndicatorWindow> windows = new ArrayList<>();
        int followingIndex = 0;
        int anchoredIndex = 0;
        int runIndex = 0;
        for (IndicatorRenderer.WindowLayerGroup windowLayerGroup : windowLayerGroups) {
            MacosIndicatorWindow window = atLevel(windowLayerGroup.followMouse() ?
                    indicatorWindow(followingIndicatorWindows, followingIndex) :
                    indicatorWindow(anchoredIndicatorWindows, anchoredIndex++),
                    windowLayerGroup.belowOverlay() ? MacosWindow.belowOverlayLevel :
                            MacosWindow.aboveOverlayLevel);
            List<IndicatorRenderer.WindowImage> images = new ArrayList<>();
            for (IndicatorLayerCopyKey copyKey : windowLayerGroup.copyKeys())
                images.add(windowImage(anchoredIndicatorRendererByCopyKey.get(copyKey),
                        indicatorByCopyKey.get(copyKey), Set.of(), Map.of(), Map.of(),
                        Map.of(), mousePosition));
            for (IndicatorConfiguration indicator : windowLayerGroup.indicators())
                images.add(windowLayerGroup.followMouse() ?
                        windowImage(followingIndicatorRenderer(followingIndex++), indicator,
                                Set.of(), Map.of(), Map.of(), Map.of(), mousePosition) :
                        windowImage(anchoredIndicatorRenderer(runIndex++), indicator,
                                layerNamesToAnchor, pointsByPosition, rectangleByArea,
                                hintRectanglesByPoint, mousePosition));
            window.show(images, activeScreen);
            windows.add(window);
        }
        for (MacosIndicatorWindow indicatorWindow : indicatorWindows())
            if (!windows.contains(indicatorWindow))
                indicatorWindow.show(List.of(), activeScreen);
        // Within a level, the window ordered front last is drawn on top.
        if (!windows.equals(orderedIndicatorWindows))
            for (MacosIndicatorWindow window : windows)
                MacosWindow.orderFront(window);
        orderedIndicatorWindows = windows;
        currentWindowLayerGroups = windowLayerGroups;
        this.indicatorByCopyKey = indicatorByCopyKey;
    }

    private MacosIndicatorWindow indicatorWindow(List<MacosIndicatorWindow> windows, int index) {
        while (windows.size() <= index)
            windows.add(createIndicatorWindow());
        return windows.get(index);
    }

    private IndicatorRenderer.WindowImage windowImage(
            IndicatorRenderer renderer, IndicatorConfiguration indicator,
            Set<String> layerNamesToAnchor, Map<IndicatorPosition, List<Point>> pointsByPosition,
            Map<Area, Rectangle> rectangleByArea,
            Map<Point, HintRectangles> hintRectanglesByPoint, QPoint mousePosition) {
        Screen activeScreen = MacosScreens.findActiveScreen(mousePosition);
        return renderer.renderWindowImage(indicator, layerNamesToAnchor, pointsByPosition,
                rectangleByArea, hintRectanglesByPoint,
                cursorRectangle(mousePosition, activeScreen), cursorVisualCenter(activeScreen),
                activeScreen, null, hintMeshRenderer.lastSelectedHintBoxHexColor());
    }

    @Override
    public void hideIndicator() {
        Screen activeScreen = MacosScreens.findActiveScreen(mouse.findMousePosition());
        for (MacosIndicatorWindow indicatorWindow : indicatorWindows())
            indicatorWindow.show(List.of(), activeScreen);
        indicatorByCopyKey = Map.of();
        currentWindowLayerGroups = List.of();
    }

    public void repositionIndicator(QPoint mousePosition) {
        if (followingIndicatorRenderer != null)
            showIndicator(currentWindowLayerGroups, indicatorByCopyKey, Set.of(), Map.of(),
                    Map.of(), Map.of(), mousePosition);
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
    public void setHintMesh(HintMesh hintMesh, Zoom zoom, boolean animate) {
        if (hintMesh.excludeFromCapture() != hintMeshExcludedFromCapture) {
            hintMeshExcludedFromCapture = hintMesh.excludeFromCapture();
            for (TransparentWindow window : hintMeshRenderer.windows())
                MacosWindow.setExcludedFromCapture(window, hintMeshExcludedFromCapture);
        }
        hintMeshRenderer.setHintMesh(hintMesh, zoom, animate, MacosScreens.screens());
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
        if (currentZoom == null)
            zoomFrameWait = 0;
        currentZoom = zoom;
        if (currentZoom == null && zoomRenderer != null)
            zoomRenderer.hide();
    }

    /**
     * The loop iterates far more often than the screen refreshes, and every frame captures
     * and hands over a screen sized image over whatever else is drawing.
     */
    private void updateZoomWindow(double delta) {
        if (currentZoom == null)
            return;
        zoomFrameWait -= delta;
        if (zoomFrameWait > 0)
            return;
        if (zoomRenderer == null)
            zoomRenderer = new MacosZoomRenderer();
        long before = System.nanoTime();
        zoomRenderer.render(currentZoom);
        double renderDuration = (System.nanoTime() - before) / 1e9;
        // Capturing over a busy compositor takes tens of milliseconds and holds up the
        // keyboard with it, so give the loop as long as the render took before asking again.
        zoomFrameWait = Math.max(Mousemaster.FRAME_INTERVAL, 2 * renderDuration);
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
