package mousemaster;

public class ZoomManager implements ModeListener, MousePositionListener {

    private final ScreenManager screenManager;
    private final HintManager hintManager;
    private Mode currentMode;
    private int mouseX, mouseY;

    private boolean animating;
    private double animationDuration;
    private double beginPercent;
    private double endPercent;
    private double currentPercent = 1.0;
    private Point beginCenterPoint;
    private Point currentCenterPoint;
    private ZoomCenter endCenter;
    private Easing animationEasing;
    private double animationTotalDuration;

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
        boolean endIsNoZoom = newMode.zoom().percent() == 1
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
            animating = true;
            animationDuration = 0;
            animationEasing = animationConfig.animationEasing();
            animationTotalDuration = animationConfig.animationDurationMillis() / 1000.0;
            beginCenterPoint = currentCenterPoint != null
                    ? currentCenterPoint
                    : screenManager.activeScreen().rectangle().center();
            endCenter = endIsNoZoom
                    ? ZoomCenter.SCREEN_CENTER
                    : newMode.zoom().center();
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
        WindowsOverlay.setZoom(new Zoom(currentPercent, centerPoint, screen.rectangle()));
        if (t >= 1.0) {
            animating = false;
            if (currentPercent == 1.0) {
                currentCenterPoint = null;
                WindowsOverlay.setZoom(null);
            }
        }
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
