package mousemaster;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZoomManager implements ModeListener, MousePositionListener {

    private final ScreenManager screenManager;
    private final HintManager hintManager;
    private Mode currentMode;
    private int mouseX, mouseY;

    private boolean animating;
    private boolean endIsNoZoom;
    private double animationDuration;
    private double beginPercent;
    private double endPercent;
    private double currentPercent = 1.0;
    private Point beginCenterPoint;
    private Point currentCenterPoint;
    private ZoomCenter endCenter;
    private Easing animationEasing;
    private double animationTotalDuration;
    // Hint mesh to interpolate during animation (null if no hints).
    private HintMesh endHintMesh;
    // The zoom center used to build endHintMesh.
    private Point endHintZoomCenter;

    public ZoomManager(ScreenManager screenManager, HintManager hintManager) {
        this.screenManager = screenManager;
        this.hintManager = hintManager;
    }

    @Override
    public void modeChanged(Mode newMode) {
        Mode previousMode = this.currentMode;
        this.currentMode = newMode;
        if (previousMode != null && previousMode.zoom().equals(newMode.zoom()))
            return;
        endIsNoZoom = newMode.zoom().percent() == 1
                && newMode.zoom().center() == ZoomCenter.SCREEN_CENTER;
        beginPercent = currentPercent;
        endPercent = endIsNoZoom ? 1.0 : newMode.zoom().percent();
        // When transitioning to no-zoom, use previous mode's animation configuration.
        ZoomConfiguration animationConfig =
                (endIsNoZoom && previousMode != null) ? previousMode.zoom() : newMode.zoom();
        if (!animationConfig.animationEnabled()) {
            // Apply immediately, no animation.
            currentPercent = endPercent;
            if (endIsNoZoom) {
                currentCenterPoint = null;
                WindowsOverlay.setZoom(null);
            }
            else {
                Point centerPoint = newMode.zoom().center().centerPoint(
                        screenManager.activeScreen().rectangle(), mouseX, mouseY,
                        hintManager.lastSelectedHintPoint());
                currentCenterPoint = centerPoint;
                Screen screen = screenManager.nearestScreenContaining(centerPoint.x(),
                        centerPoint.y());
                WindowsOverlay.setZoom(new Zoom(endPercent,
                        centerPoint, screen.rectangle()));
            }
        }
        else {
            // startScreenshotZoomAnimation handles the interruption if
            // screenshotAnimating is still true — no need to end here.
            animating = true;
            animationDuration = 0;
            animationEasing = animationConfig.animationEasing();
            // Scale duration proportionally to the actual zoom change.
            // E.g. if the configured duration covers 1x→3x but we only need
            // 1.2x→1x (interrupted at 10%), use 10% of the configured duration.
            double fullRange = Math.abs(animationConfig.percent() - 1.0);
            double actualRange = Math.abs(beginPercent - endPercent);
            double durationScale = fullRange > 0 ? Math.min(1.0, actualRange / fullRange) : 1.0;
            animationTotalDuration = animationConfig.animationDurationMillis() / 1000.0
                    * durationScale;
            beginCenterPoint = currentCenterPoint != null
                    ? currentCenterPoint
                    : screenManager.activeScreen().rectangle().center();
            endCenter = endIsNoZoom
                    ? ZoomCenter.SCREEN_CENTER
                    : newMode.zoom().center();
            // Capture hint mesh for interpolation during animation,
            // only if the new mode has hints enabled.
            HintMesh hintMesh = hintManager.hintMesh();
            if (newMode.hintMesh().enabled() && hintMesh != null && hintMesh.visible()) {
                endHintMesh = hintMesh;
                endHintZoomCenter = newMode.zoom().center().centerPoint(
                        screenManager.activeScreen().rectangle(), mouseX, mouseY,
                        hintManager.lastSelectedHintPoint());
                // Override the final hint mesh that HintManager just displayed
                // with the t=0 interpolated mesh.
                Screen screen = screenManager.nearestScreenContaining(
                        beginCenterPoint.x(), beginCenterPoint.y());
                HintMesh interpolatedMesh = interpolateHintMesh(endHintMesh,
                        endHintZoomCenter, beginCenterPoint,
                        screen.rectangle().center(), endPercent, beginPercent);
                WindowsOverlay.setHintMesh(interpolatedMesh,
                        new Zoom(beginPercent, beginCenterPoint, screen.rectangle()));
            }
            else {
                endHintMesh = null;
                endHintZoomCenter = null;
            }
            // Start screenshot-based zoom animation.
            Screen screen = screenManager.nearestScreenContaining(
                    beginCenterPoint.x(), beginCenterPoint.y());
            Zoom beginZoom = new Zoom(beginPercent, beginCenterPoint, screen.rectangle());
            WindowsOverlay.startScreenshotZoomAnimation(screen.rectangle(), beginZoom);
        }
    }

    public void update(double delta) {
        if (!animating)
            return;
        animationDuration += delta;
        double t = Math.min(1.0, animationDuration / animationTotalDuration);
        double easedT = animationEasing.apply(t);
        currentPercent = beginPercent + (endPercent - beginPercent) * easedT;
        Point endCenterPoint = endCenter.centerPoint(
                screenManager.activeScreen().rectangle(), mouseX, mouseY,
                hintManager.lastSelectedHintPoint());
        int centerX = (int) Math.round(
                beginCenterPoint.x() + (endCenterPoint.x() - beginCenterPoint.x()) * easedT);
        int centerY = (int) Math.round(
                beginCenterPoint.y() + (endCenterPoint.y() - beginCenterPoint.y()) * easedT);
        Point centerPoint = new Point(centerX, centerY);
        currentCenterPoint = centerPoint;
        Screen screen = screenManager.nearestScreenContaining(centerPoint.x(),
                centerPoint.y());
        Zoom currentZoom = new Zoom(currentPercent, centerPoint, screen.rectangle());
        WindowsOverlay.updateScreenshotZoom(currentZoom);
        if (endHintMesh != null) {
            HintMesh interpolatedMesh = interpolateHintMesh(endHintMesh,
                    endHintZoomCenter, centerPoint,
                    screen.rectangle().center(), endPercent, currentPercent);
            WindowsOverlay.setHintMesh(interpolatedMesh, currentZoom);
        }
        if (t >= 1.0) {
            animating = false;
            Zoom endZoom = endIsNoZoom ? null :
                    new Zoom(currentPercent, centerPoint, screen.rectangle());
            WindowsOverlay.endScreenshotZoomAnimation(endZoom);
            if (endHintMesh != null) {
                // Restore the final hint mesh.
                if (endZoom == null)
                    endZoom = new Zoom(currentPercent, centerPoint, screen.rectangle());
                WindowsOverlay.setHintMesh(endHintMesh, endZoom);
                endHintMesh = null;
                endHintZoomCenter = null;
            }
            if (endIsNoZoom)
                currentCenterPoint = null;
        }
    }

    private static HintMesh interpolateHintMesh(HintMesh mesh,
            Point endCenter, Point currentCenter,
            Point screenCenter, double endPercent, double currentPercent) {
        // unzoomedX = (h.centerX - screenCenter) / endPercent + endCenter
        // interpolatedX = screenCenter + (unzoomedX - currentCenter) * currentPercent
        double scale = currentPercent / endPercent;
        List<Hint> interpolatedHints = mesh.hints().stream()
                .map(h -> {
                    double unzoomedX = (h.centerX() - screenCenter.x()) / endPercent + endCenter.x();
                    double unzoomedY = (h.centerY() - screenCenter.y()) / endPercent + endCenter.y();
                    return new Hint(
                            screenCenter.x() + (unzoomedX - currentCenter.x()) * currentPercent,
                            screenCenter.y() + (unzoomedY - currentCenter.y()) * currentPercent,
                            h.cellWidth() * scale, h.cellHeight() * scale,
                            h.keySequence());
                })
                .toList();
        Rectangle backgroundArea = mesh.backgroundArea();
        Rectangle interpolatedBackgroundArea = null;
        if (backgroundArea != null) {
            double backgroundUnzoomedX = (backgroundArea.x() - screenCenter.x()) / endPercent + endCenter.x();
            double backgroundUnzoomedY = (backgroundArea.y() - screenCenter.y()) / endPercent + endCenter.y();
            interpolatedBackgroundArea = new Rectangle(
                    (int) (screenCenter.x() + (backgroundUnzoomedX - currentCenter.x()) * currentPercent),
                    (int) (screenCenter.y() + (backgroundUnzoomedY - currentCenter.y()) * currentPercent),
                    (int) (backgroundArea.width() * scale), (int) (backgroundArea.height() * scale));
        }
        ViewportFilterMap<HintMeshStyle> scaledStyleByFilter = scaleFontSize(
                mesh.styleByFilter(), scale);
        return new HintMesh(mesh.visible(), interpolatedHints, mesh.prefixLength(),
                mesh.selectedKeySequence(), scaledStyleByFilter, interpolatedBackgroundArea);
    }

    private static ViewportFilterMap<HintMeshStyle> scaleFontSize(
            ViewportFilterMap<HintMeshStyle> styleByFilter, double scale) {
        Map<ViewportFilter, HintMeshStyle> scaledMap = new HashMap<>();
        for (Map.Entry<ViewportFilter, HintMeshStyle> entry : styleByFilter.map().entrySet()) {
            scaledMap.put(entry.getKey(), scaleFontSize(entry.getValue(), scale));
        }
        return new ViewportFilterMap<>(scaledMap);
    }

    private static HintMeshStyle scaleFontSize(HintMeshStyle style, double scale) {
        return new HintMeshStyle(
                scaleFontSize(style.fontStyle(), scale),
                style.prefixInBackground(),
                scaleFontSize(style.prefixFontStyle(), scale),
                style.boxHexColor(), style.boxOpacity(),
                style.boxBorderThickness(), style.boxBorderLength(),
                style.boxBorderHexColor(), style.boxBorderOpacity(),
                style.boxBorderRadius(), style.boxShadow(),
                style.prefixBoxEnabled(),
                style.prefixBoxBorderThickness(), style.prefixBoxBorderLength(),
                style.prefixBoxBorderHexColor(), style.prefixBoxBorderOpacity(),
                style.boxWidthPercent(), style.boxHeightPercent(),
                style.cellHorizontalPadding(), style.cellVerticalPadding(),
                style.subgridRowCount(), style.subgridColumnCount(),
                style.subgridBorderThickness(), style.subgridBorderLength(),
                style.subgridBorderHexColor(), style.subgridBorderOpacity(),
                style.transitionAnimationEnabled(), style.transitionAnimationDuration(),
                style.fadeAnimationEnabled(), style.fadeAnimationDuration(),
                style.backgroundHexColor(), style.backgroundOpacity());
    }

    private static HintFontStyle scaleFontSize(HintFontStyle style, double scale) {
        return new HintFontStyle(
                scaleFontSize(style.defaultFontStyle(), scale),
                style.spacingPercent(),
                scaleFontSize(style.selectedFontStyle(), scale),
                scaleFontSize(style.focusedFontStyle(), scale));
    }

    private static FontStyle scaleFontSize(FontStyle style, double scale) {
        return new FontStyle(style.name(), style.weight(),
                style.size() * scale, style.hexColor(), style.opacity(),
                style.outlineThickness(), style.outlineHexColor(),
                style.outlineOpacity(), style.shadow());
    }

    @Override
    public void modeTimedOut() {
        // No op.
    }

    @Override
    public void mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
        if (!animating && currentMode.zoom().center().equals(ZoomCenter.MOUSE)) {
            Point centerPoint = currentMode.zoom().center().centerPoint(
                    screenManager.activeScreen().rectangle(), mouseX, mouseY,
                    hintManager.lastSelectedHintPoint());
            currentCenterPoint = centerPoint;
            Screen screen = screenManager.nearestScreenContaining(centerPoint.x(),
                    centerPoint.y());
            WindowsOverlay.setZoom(new Zoom(currentMode.zoom().percent(),
                    centerPoint, screen.rectangle()));
        }
    }
}
