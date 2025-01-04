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
        else
            WindowsOverlay.setZoom(
                    new Zoom(newMode.zoom().percent(),
                            centerPoint(newMode.zoom().center())));
    }

    private Point centerPoint(ZoomCenter center) {
        return switch (center) {
            case SCREEN_CENTER -> screenManager.activeScreen().rectangle().center();
            case MOUSE -> new Point(mouseX, mouseY);
            case LAST_SELECTED_HINT -> hintManager.lastSelectedHintPoint();
        };
    }

    @Override
    public void modeTimedOut() {
        // No op.
    }

    @Override
    public void mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
    }
}
