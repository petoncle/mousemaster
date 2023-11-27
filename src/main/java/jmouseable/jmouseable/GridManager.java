package jmouseable.jmouseable;

/**
 * Displays the grid and handles grid commands.
 */
public class GridManager implements MousePositionListener, ModeListener {

    private final MonitorManager monitorManager;
    private final MouseController mouseController;
    private Grid grid;
    private int mouseX, mouseY;
    private Mode currentMode;

    public GridManager(MonitorManager monitorManager, MouseController mouseController) {
        this.monitorManager = monitorManager;
        this.mouseController = mouseController;
    }

    public void keepGridTop() {
        grid = grid.builder().height(Math.max(1, grid.height() / 2)).build();
        gridChanged();
    }

    public void keepGridBottom() {
        grid = grid.builder()
                   .y(grid.y() + grid.height() / 2)
                   .height(Math.max(1, grid.height() / 2))
                   .build();
        gridChanged();
    }

    public void keepGridLeft() {
        grid = grid.builder().width(Math.max(1, grid.width() / 2)).build();
        gridChanged();
    }

    public void keepGridRight() {
        grid = grid.builder()
                   .x(grid.x() + grid.width() / 2)
                   .width(Math.max(1, grid.width() / 2))
                   .build();
        gridChanged();
    }

    @Override
    public void mouseMoved(int x, int y) {
        this.mouseX = x;
        this.mouseY = y;
    }

    @Override
    public void modeChanged(Mode newMode) {
        currentMode = newMode;
        GridConfiguration gridConfiguration = currentMode.gridConfiguration();
        if (gridConfiguration.type() != GridConfiguration.GridType.FULL_SCREEN)
            throw new UnsupportedOperationException(); // TODO
        Monitor monitor = monitorManager.activeMonitor();
        grid = new Grid(monitor.x(), monitor.y(), monitor.width(), monitor.height(),
                gridConfiguration.snapRowCount(), gridConfiguration.snapColumnCount(),
                gridConfiguration.lineHexColor(), gridConfiguration.lineThickness());
        gridChanged();
    }

    private void gridChanged() {
        if (currentMode.gridConfiguration().enabled() &&
            currentMode.gridConfiguration().visible())
            WindowsOverlay.setGrid(grid);
        else
            WindowsOverlay.hideGrid();
        if (currentMode.gridConfiguration().enabled() &&
            currentMode.gridConfiguration().autoMoveToGridCenter())
            mouseController.moveTo(grid.x() + grid.width() / 2,
                    grid.y() + grid.height() / 2);
    }

    @Override
    public void modeTimedOut() {
        // No op.
    }
}
