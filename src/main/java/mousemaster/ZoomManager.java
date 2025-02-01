package mousemaster;

public class ZoomManager implements ModeListener, MousePositionListener {

    private final ScreenManager screenManager;
    private final HintManager hintManager;
    private Mode currentMode;
    private int mouseX, mouseY;

    public ZoomManager(ScreenManager screenManager, HintManager hintManager) {
        this.screenManager = screenManager;
        this.hintManager = hintManager;
    }

    @Override
    public void modeChanged(Mode newMode) {
        Mode currentMode = this.currentMode;
        this.currentMode = newMode;
        if (currentMode != null && currentMode.zoom().equals(newMode.zoom()))
            return;
        if (newMode.zoom().percent() == 1 && newMode.zoom().center() == ZoomCenter.SCREEN_CENTER)
            WindowsOverlay.setZoom(null);
        else {
            Point centerPoint = newMode.zoom().center().centerPoint(
                    screenManager.activeScreen().rectangle(), mouseX, mouseY,
                    hintManager.lastSelectedHintPoint());
            WindowsOverlay.setZoom(new Zoom(newMode.zoom().percent(),
                    centerPoint, screenManager.screenContaining(centerPoint.x(),
                    centerPoint.y()).rectangle()));
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
        if (currentMode.zoom().center().equals(ZoomCenter.MOUSE)) {
            Point centerPoint = currentMode.zoom().center().centerPoint(
                    screenManager.activeScreen().rectangle(), mouseX, mouseY,
                    hintManager.lastSelectedHintPoint());
            WindowsOverlay.setZoom(new Zoom(currentMode.zoom().percent(),
                    centerPoint, screenManager.screenContaining(centerPoint.x(),
                    centerPoint.y()).rectangle()));
        }
    }
}
