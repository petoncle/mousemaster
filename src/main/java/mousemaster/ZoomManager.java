package mousemaster;

import mousemaster.platform.Overlay;

public class ZoomManager implements ModeListener, MousePositionListener {

    private final ScreenManager screenManager;
    private final HintManager hintManager;
    private final Overlay overlay;
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
    /** Hidden while the image moves, shown again when it settles. */
    private boolean hintMeshHidden;

    public ZoomManager(ScreenManager screenManager, HintManager hintManager,
                       Overlay overlay) {
        this.screenManager = screenManager;
        this.hintManager = hintManager;
        this.overlay = overlay;
    }

    public boolean animating() {
        return animating;
    }

    @Override
    public void modeChanged(Mode newMode) {
        Mode previousMode = this.currentMode;
        this.currentMode = newMode;
        // A zoom anchored on the hint selection resolves to a deeper zoom on every drill,
        // even though the configuration it comes from is the same one.
        boolean anchoredOnHintSelection =
                newMode.zoom().areaSize().source() ==
                ZoomAreaSizeSource.LAST_SELECTED_HINT_CELL ||
                newMode.zoom().center() == ZoomCenter.LAST_SELECTED_HINT;
        if (previousMode != null && !anchoredOnHintSelection &&
            previousMode.zoom().equals(newMode.zoom()))
            return;
        Point targetCenter = newMode.zoom().center().centerPoint(
                screenManager.activeScreen().rectangle(), mouseX, mouseY,
                hintManager.lastSelectedHintPoint());
        Rectangle targetScreen = screenManager.nearestScreenContaining(
                targetCenter.x(), targetCenter.y()).rectangle();
        double targetPercent = newMode.zoom().percent(
                hintManager.lastSelectedHintCell(), targetScreen);
        endIsNoZoom = targetPercent == 1
                && newMode.zoom().center() == ZoomCenter.SCREEN_CENTER;
        beginPercent = currentPercent;
        endPercent = endIsNoZoom ? 1.0 : targetPercent;
        // When transitioning to no-zoom, use previous mode's animation configuration.
        ZoomConfiguration animationConfig =
                (endIsNoZoom && previousMode != null) ? previousMode.zoom() : newMode.zoom();
        if (!animationConfig.animationEnabled()) {
            // Apply immediately, no animation.
            currentPercent = endPercent;
            if (endIsNoZoom) {
                currentCenterPoint = null;
                overlay.setZoom(null);
            }
            else {
                Point centerPoint = newMode.zoom().center().centerPoint(
                        screenManager.activeScreen().rectangle(), mouseX, mouseY,
                        hintManager.lastSelectedHintPoint());
                currentCenterPoint = centerPoint;
                Screen screen = screenManager.nearestScreenContaining(centerPoint.x(),
                        centerPoint.y());
                overlay.setZoom(new Zoom(endPercent,
                        centerPoint, screen.rectangle()));
            }
        }
        else {
            beginCenterPoint = currentCenterPoint != null
                    ? currentCenterPoint
                    : screenManager.activeScreen().rectangle().center();
            endCenter = endIsNoZoom
                    ? ZoomCenter.SCREEN_CENTER
                    : newMode.zoom().center();
            Point endCenterPoint = endCenter.centerPoint(
                    screenManager.activeScreen().rectangle(), mouseX, mouseY,
                    hintManager.lastSelectedHintPoint());
            if (beginPercent == endPercent && beginCenterPoint.equals(endCenterPoint)) {
                // Zoom configurations differing only in their animation settings, or a
                // zoom released before a single frame of it was drawn.
                currentPercent = endPercent;
                settle(beginCenterPoint);
                return;
            }
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
            HintMesh hintMesh = hintManager.hintMesh();
            hintMeshHidden = newMode.hintMesh().enabled() && hintMesh != null &&
                             hintMesh.visible();
            if (hintMeshHidden)
                overlay.hideHintMesh();
            Screen screen = screenManager.nearestScreenContaining(
                    beginCenterPoint.x(), beginCenterPoint.y());
            overlay.setZoom(new Zoom(beginPercent, beginCenterPoint, screen.rectangle()));
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
        overlay.setZoom(currentZoom);
        if (t >= 1.0)
            settle(centerPoint);
    }

    /** The image has arrived, so the mesh hidden for the move can come back over it. */
    private void settle(Point centerPoint) {
        animating = false;
        Screen screen = screenManager.nearestScreenContaining(centerPoint.x(),
                centerPoint.y());
        Zoom zoom = new Zoom(currentPercent, centerPoint, screen.rectangle());
        overlay.setZoom(endIsNoZoom ? null : zoom);
        if (hintMeshHidden) {
            hintMeshHidden = false;
            // Read now, not when it was hidden: the mesh hidden at the start was laid out
            // for the zoom that has since been left behind.
            HintMesh hintMesh = hintManager.hintMesh();
            if (hintMesh != null)
                overlay.restoreHintMesh(hintMesh, zoom);
        }
        if (endIsNoZoom)
            currentCenterPoint = null;
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
            overlay.setZoom(new Zoom(currentMode.zoom().percent(
                    hintManager.lastSelectedHintCell(), screen.rectangle()),
                    centerPoint, screen.rectangle()));
        }
    }
}
