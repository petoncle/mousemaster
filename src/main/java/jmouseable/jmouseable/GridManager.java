package jmouseable.jmouseable;

import jmouseable.jmouseable.Grid.GridBuilder;

/**
 * Displays the grid and handles grid commands.
 */
public class GridManager implements MousePositionListener, ModeListener {

    private final MonitorManager monitorManager;
    private final MouseController mouseController;
    private Grid grid;
    private boolean gridFollowsCursor;
    private int mouseX, mouseY;
    private Mode currentMode;

    public GridManager(MonitorManager monitorManager, MouseController mouseController) {
        this.monitorManager = monitorManager;
        this.mouseController = mouseController;
    }

    public void cutGridTop() {
        grid = grid.builder().height(Math.max(1, grid.height() / 2)).build();
        gridChanged();
    }

    public void cutGridBottom() {
        grid = grid.builder()
                   .y(grid.y() + grid.height() / 2)
                   .height(Math.max(1, grid.height() / 2))
                   .build();
        gridChanged();
    }

    public void cutGridLeft() {
        grid = grid.builder().width(Math.max(1, grid.width() / 2)).build();
        gridChanged();
    }

    public void cutGridRight() {
        grid = grid.builder()
                   .x(grid.x() + grid.width() / 2)
                   .width(Math.max(1, grid.width() / 2))
                   .build();
        gridChanged();
    }

    private GridBuilder gridCenteredAroundMouse(GridBuilder grid) {
        return grid.x(mouseX - grid.width() / 2).y(mouseY - grid.height() / 2);
    }

    private void shiftGrid(int deltaX, int deltaY) {
        int x = grid.x() + deltaX;
        int y = grid.y() + deltaY;
        int gridCenterX = x + grid.width() / 2;
        int gridCenterY = y + grid.height() / 2;
        if (monitorManager.monitorContaining(gridCenterX, gridCenterY) == null) {
            Monitor activeMonitor = monitorManager.activeMonitor();
            x = Math.max(activeMonitor.x(),
                    Math.min(activeMonitor.x() + activeMonitor.width() - grid.width(),
                            x));
            y = Math.max(activeMonitor.y(),
                    Math.min(activeMonitor.y() + activeMonitor.height() - grid.height(),
                            y));
        }
        Grid newGrid = grid.builder().x(x).y(y).build();
        if (newGrid.equals(grid))
            return;
        grid = newGrid;
        gridChanged();
    }

    public void shiftGridTop() {
        shiftGrid(0, -grid.height());
    }

    public void shiftGridBottom() {
        shiftGrid(0, grid.height());
    }

    public void shiftGridLeft() {
        shiftGrid(-grid.width(), 0);
    }

    public void shiftGridRight() {
        shiftGrid(grid.width(), 0);
    }

    public void snapUp() {
        snap(false, false);
    }

    public void snapDown() {
        snap(false, true);
    }

    public void snapLeft() {
        snap(true, false);
    }

    public void snapRight() {
        snap(true, true);
    }

    private void snap(boolean horizontal, boolean forward) {
        boolean mouseIsInsideGrid =
                RectUtil.rectContains(grid.x(), grid.y(), grid.width(), grid.height(),
                        mouseX, mouseY);
        int x, y;
        int rowWidth = Math.max(1, grid.width() / grid.snapRowCount());
        int columnHeight = Math.max(1, grid.height() / grid.snapColumnCount());
        if (mouseIsInsideGrid) {
            double mouseRow = (double) (mouseX - grid.x()) / rowWidth;
            double mouseColumn = (double) (mouseY - grid.y()) / columnHeight;
            if (horizontal) {
                x = grid.x() + (int) ((forward ? Math.floor(mouseRow) + 1 :
                        Math.ceil(mouseRow) - 1) * rowWidth);
                x = Math.max(grid.x(), Math.min(grid.x() + grid.width(), x));
                y = mouseY;
            }
            else {
                x = mouseX;
                y = grid.y() + (int) ((forward ? Math.floor(mouseColumn) + 1 :
                        Math.ceil(mouseColumn) - 1) * columnHeight);
                y = Math.max(grid.y(), Math.min(grid.y() + grid.height(), y));
            }
            if (monitorManager.monitorContaining(x, y) == null) {
                Monitor activeMonitor = monitorManager.activeMonitor();
                x = Math.max(activeMonitor.x(),
                        Math.min(activeMonitor.x() + activeMonitor.width(), x));
                y = Math.max(activeMonitor.y(),
                        Math.min(activeMonitor.y() + activeMonitor.height(), y));
            }
            mouseController.moveTo(x, y);
        }
        // If mouse is not in grid, snap it to the grid edges...
        else if (mouseX >= grid.x() && mouseX <= grid.x() + grid.width())
            mouseController.moveTo(mouseX,
                    mouseY < grid.y() ? grid.y() : (grid.y() + grid.height()));
        else if (mouseY >= grid.y() && mouseY <= grid.y() + grid.height())
            mouseController.moveTo(
                    mouseX < grid.x() ? grid.x() : (grid.x() + grid.height()), mouseY);
        // ...or to the grid corners.
        else if (mouseX < grid.x() && mouseY < grid.y())
            mouseController.moveTo(grid.x(), grid.y());
        else if (mouseX > grid.x() + grid.width() && mouseY < grid.y())
            mouseController.moveTo(grid.x() + grid.width(), grid.y());
        else if (mouseX < grid.x() && mouseY > grid.y())
            mouseController.moveTo(grid.x(), grid.y() + grid.height());
        else
            mouseController.moveTo(grid.x() + grid.width(), grid.y() + grid.height());
    }

    @Override
    public void mouseMoved(int x, int y) {
        this.mouseX = x;
        this.mouseY = y;
        if (gridFollowsCursor) {
            grid = gridCenteredAroundMouse(grid.builder()).build();
            gridChanged();
        }
    }

    @Override
    public void modeChanged(Mode newMode) {
        GridConfiguration gridConfiguration = newMode.gridConfiguration();
        GridBuilder gridBuilder =
                new GridBuilder().snapRowCount(gridConfiguration.snapRowCount())
                                 .snapColumnCount(gridConfiguration.snapColumnCount())
                                 .lineHexColor(gridConfiguration.lineHexColor())
                                 .lineThickness(gridConfiguration.lineThickness());
        Grid newGrid = switch (gridConfiguration.type()) {
            case GridType.FullScreen fullScreen -> {
                Monitor monitor = monitorManager.activeMonitor();
                yield gridBuilder.x(monitor.x())
                                 .y(monitor.y())
                                 .width(monitor.width())
                                 .height(monitor.height())
                                 .build();
            }
            case GridType.ActiveWindow activeWindow ->
                    WindowsOverlay.gridFittingActiveWindow(gridBuilder).build();
            case GridType.AroundMouse aroundMouse -> {
                Monitor monitor = monitorManager.nearestMonitorContaining(mouseX, mouseY);
                yield gridCenteredAroundMouse(
                        gridBuilder.width(monitor.width() / aroundMouse.width())
                                   .height(monitor.height() /
                                           aroundMouse.height())).build();
            }
        };
        if (currentMode != null &&
            newMode.gridConfiguration().equals(currentMode.gridConfiguration()) &&
            newGrid.equals(grid))
            return;
        currentMode = newMode;
        grid = newGrid;
        gridFollowsCursor =
                currentMode.gridConfiguration().type() instanceof GridType.AroundMouse;
        gridChanged();
    }

    private void gridChanged() {
        if (currentMode.gridConfiguration().visible())
            WindowsOverlay.setGrid(grid);
        else
            WindowsOverlay.hideGrid();
        if (currentMode.gridConfiguration().autoMoveToGridCenter())
            mouseController.moveTo(grid.x() + grid.width() / 2,
                    grid.y() + grid.height() / 2);
    }

    @Override
    public void modeTimedOut() {
        // No op.
    }

}
