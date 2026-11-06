package mousemaster;

import mousemaster.platform.Overlay;

public class ZoomManager implements ModeListener {

    private final ScreenManager screenManager;
    private final HintManager hintManager;
    private final Overlay overlay;
    private Mode currentMode;

    private boolean animating;
    private boolean endIsNoZoom;
    private AreaCenter endCenter;
    private final TransitionedValue percent = new TransitionedValue(1);
    private final TransitionedValue centerX = new TransitionedValue(0);
    private final TransitionedValue centerY = new TransitionedValue(0);
    private boolean zoomShown;
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
                newMode.zoom().center() == AreaCenter.LAST_SELECTED_HINT;
        if (previousMode != null && !anchoredOnHintSelection &&
            previousMode.zoom().equals(newMode.zoom()))
            return;
        endCenter = newMode.zoom().center();
        Point endCenterPoint = hintManager.areaCenterPoint(endCenter);
        Rectangle screenRectangle = screenManager.nearestScreenContaining(
                endCenterPoint.x(), endCenterPoint.y()).rectangle();
        double endPercent = newMode.zoom().percent(
                hintManager.lastSelectedHintCell(), screenRectangle);
        endIsNoZoom = endPercent == 1 && endCenter == AreaCenter.ACTIVE_SCREEN_CENTER;
        if (!zoomShown) {
            Point screenCenter = screenManager.activeScreen().rectangle().center();
            percent.jumpTo(1);
            centerX.jumpTo(screenCenter.x());
            centerY.jumpTo(screenCenter.y());
        }
        if (percent.value() != endPercent || centerX.value() != endCenterPoint.x() ||
            centerY.value() != endCenterPoint.y()) {
            overlay.stopHintMatchAnimation();
            // When transitioning to no-zoom, use previous mode's transition.
            Transition transition = (endIsNoZoom && previousMode != null ? previousMode :
                    newMode).zoom().transition();
            percent.transitionTo(transition, endPercent);
            centerX.transitionTo(transition, endCenterPoint.x());
            centerY.transitionTo(transition, endCenterPoint.y());
        }
        else {
            // Zoom configurations differing only in their transition settings, or a
            // zoom released before a single frame of it was drawn.
            percent.jumpTo(endPercent);
            centerX.jumpTo(endCenterPoint.x());
            centerY.jumpTo(endCenterPoint.y());
        }
        if (!transitioning()) {
            settle();
            return;
        }
        animating = true;
        zoomShown = true;
        HintMesh hintMesh = hintManager.hintMesh();
        hintMeshHidden = newMode.hintMesh().enabled() && hintMesh != null &&
                         hintMesh.visible();
        if (hintMeshHidden)
            overlay.hideHintMesh();
        overlay.setZoom(zoom());
    }

    public void update(double delta) {
        if (!animating) {
            AreaCenter center = currentMode.zoom().center();
            if (!center.isMouseOrTarget())
                return;
            Point centerPoint = hintManager.areaCenterPoint(center);
            if (centerX.value() == centerPoint.x() && centerY.value() == centerPoint.y())
                return;
            Screen screen = screenManager.nearestScreenContaining(centerPoint.x(),
                    centerPoint.y());
            percent.jumpTo(currentMode.zoom().percent(hintManager.lastSelectedHintCell(),
                    screen.rectangle()));
            centerX.jumpTo(centerPoint.x());
            centerY.jumpTo(centerPoint.y());
            overlay.setZoom(zoom());
            return;
        }
        percent.update(delta);
        centerX.update(delta);
        centerY.update(delta);
        Point endCenterPoint = hintManager.areaCenterPoint(endCenter);
        centerX.follow(endCenterPoint.x());
        centerY.follow(endCenterPoint.y());
        overlay.setZoom(zoom());
        if (!transitioning())
            settle();
    }

    private boolean transitioning() {
        return percent.transitioning() || centerX.transitioning() || centerY.transitioning();
    }

    /** The image has arrived, so the mesh hidden for the move can come back over it. */
    private void settle() {
        animating = false;
        zoomShown = !endIsNoZoom;
        Zoom zoom = zoom();
        overlay.setZoom(endIsNoZoom ? null : zoom);
        if (hintMeshHidden) {
            hintMeshHidden = false;
            // Read now, not when it was hidden: the mesh hidden at the start was laid out
            // for the zoom that has since been left behind.
            HintMesh hintMesh = hintManager.hintMesh();
            if (hintMesh != null)
                overlay.setHintMesh(hintMesh, zoom, false);
        }
    }

    private Zoom zoom() {
        Point center = new Point(Math.round(centerX.value()),
                Math.round(centerY.value()));
        return new Zoom(percent.value(), center,
                screenManager.nearestScreenContaining(center.x(), center.y()).rectangle());
    }

    @Override
    public void modeTimedOut() {
        // No op.
    }
}
