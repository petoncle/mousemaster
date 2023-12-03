package jmouseable.jmouseable;

import jmouseable.jmouseable.Grid.GridBuilder;

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

    public void cutGridTop() {
        grid = grid.builder()
                   .x(currentMode.grid().synchronization() !=
                      Synchronization.GRID_CENTER_FOLLOWS_MOUSE ? grid.x() :
                           mouseX - grid.width() / 2)
                   .y(currentMode.grid().synchronization() !=
                      Synchronization.GRID_CENTER_FOLLOWS_MOUSE ? grid.y() :
                           mouseY - grid.height() / 4)
                   .height(Math.max(1, grid.height() / 2))
                   .build();
        gridChanged();
    }

    public void cutGridBottom() {
        grid = grid.builder()
                   .x(currentMode.grid().synchronization() !=
                      Synchronization.GRID_CENTER_FOLLOWS_MOUSE ? grid.x() :
                           mouseX - grid.width() / 2)
                   .y(currentMode.grid().synchronization() !=
                      Synchronization.GRID_CENTER_FOLLOWS_MOUSE ?
                           grid.y() + grid.height() / 2 : mouseY - grid.height() / 4)
                   .height(Math.max(1, grid.height() / 2))
                   .build();
        gridChanged();
    }

    public void cutGridLeft() {
        grid = grid.builder()
                   .x(currentMode.grid().synchronization() !=
                      Synchronization.GRID_CENTER_FOLLOWS_MOUSE ? grid.x() :
                           mouseX - grid.width() / 4)
                   .y(currentMode.grid().synchronization() !=
                      Synchronization.GRID_CENTER_FOLLOWS_MOUSE ? grid.y() :
                           mouseY - grid.height() / 2)
                   .width(Math.max(1, grid.width() / 2))
                   .build();
        gridChanged();
    }

    public void cutGridRight() {
        grid = grid.builder()
                   .x(currentMode.grid().synchronization() !=
                      Synchronization.GRID_CENTER_FOLLOWS_MOUSE ?
                           grid.x() + grid.width() / 2 : mouseX - grid.width() / 4)
                   .y(currentMode.grid().synchronization() !=
                      Synchronization.GRID_CENTER_FOLLOWS_MOUSE ? grid.y() :
                           mouseY - grid.height() / 2)
                   .width(Math.max(1, grid.width() / 2))
                   .build();
        gridChanged();
    }

    private GridBuilder gridCenteredAroundMouse(GridBuilder grid) {
        return grid.x(mouseX - grid.width() / 2).y(mouseY - grid.height() / 2);
    }

    private void moveGrid(int deltaX, int deltaY) {
        int x = grid.x() + deltaX;
        int y = grid.y() + deltaY;
        int gridCenterX = x + grid.width() / 2;
        int gridCenterY = y + grid.height() / 2;
        if (monitorManager.monitorContaining(gridCenterX, gridCenterY) == null) {
            // We want the grid center in screen.
            Monitor activeMonitor = monitorManager.activeMonitor();
            gridCenterX = Math.max(activeMonitor.x(),
                    Math.min(activeMonitor.x() + activeMonitor.width(), gridCenterX));
            gridCenterY = Math.max(activeMonitor.y(),
                    Math.min(activeMonitor.y() + activeMonitor.height(), gridCenterY));
            x = gridCenterX - grid.width() / 2;
            y = gridCenterY - grid.height() / 2;
        }
        Grid newGrid = grid.builder().x(x).y(y).build();
        if (newGrid.equals(grid))
            return;
        grid = newGrid;
        gridChanged();
    }

    public void moveGridTop() {
        moveGrid(0, -grid.height());
    }

    public void moveGridBottom() {
        moveGrid(0, grid.height());
    }

    public void moveGridLeft() {
        moveGrid(-grid.width(), 0);
    }

    public void moveGridRight() {
        moveGrid(grid.width(), 0);
    }

    public void moveToGridCenter() {
        mouseController.moveTo(grid.x() + grid.width() / 2, grid.y() + grid.height() / 2);
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
                Rectangle.rectangleContains(grid.x(), grid.y(), grid.width(), grid.height(),
                        mouseX, mouseY);
        int x, y;
        int cellWidth = Math.max(1, grid.width() / grid.columnCount());
        int cellHeight = Math.max(1, grid.height() / grid.rowCount());
        if (mouseIsInsideGrid) {
            int mouseColumn = (mouseX - grid.x()) / cellWidth;
            int mouseRow = (mouseY - grid.y()) / cellHeight;
            if (horizontal) {
                x = grid.x() + (forward ?
                        (mouseColumn + (mouseX % cellWidth == 0 ? 0 : 1)) * cellWidth :
                        (mouseColumn == 0 ? 0 : (mouseColumn - 1) * cellWidth));
                x = Math.max(grid.x(), Math.min(grid.x() + grid.width(), x));
                y = mouseY;
            }
            else {
                x = mouseX;
                y = grid.y() + (forward ?
                        (mouseRow + (mouseY % cellHeight == 0 ? 0 : 1)) * cellHeight :
                        (mouseRow == 0 ? 0 : (mouseRow - 1) * cellHeight));
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
        else if (mouseX >= grid.x() && mouseX <= grid.x() + grid.width()) {
            if (!horizontal && forward && mouseY < grid.y())
                mouseController.moveTo(mouseX, grid.y());
            else if (!horizontal && !forward && mouseY > grid.y())
                mouseController.moveTo(mouseX, grid.y() + grid.height());
        }
        else if (mouseY >= grid.y() && mouseY <= grid.y() + grid.height()) {
            if (horizontal && forward && mouseX < grid.x())
                mouseController.moveTo(grid.x(), mouseY);
            else if (horizontal && !forward && mouseX > grid.x())
                mouseController.moveTo(grid.x() + grid.height(), mouseY);
        }
        // ...or to the grid corners.
        else if (mouseX < grid.x() && mouseY < grid.y()) {
            if (horizontal && forward || !horizontal && forward)
                mouseController.moveTo(grid.x(), grid.y());
        }
        else if (mouseX > grid.x() + grid.width() && mouseY < grid.y()) {
            if (horizontal && !forward || !horizontal && forward)
                mouseController.moveTo(grid.x() + grid.width(), grid.y());
        }
        else if (mouseX < grid.x() && mouseY > grid.y() + grid.height()) {
            if (horizontal && forward || !horizontal && !forward)
                mouseController.moveTo(grid.x(), grid.y() + grid.height());
        }
        else {
            if (horizontal && !forward || !horizontal && !forward)
                mouseController.moveTo(grid.x() + grid.width(), grid.y() + grid.height());
        }
    }

    @Override
    public void mouseMoved(int x, int y) {
        this.mouseX = x;
        this.mouseY = y;
        if (currentMode.grid().synchronization() ==
            Synchronization.GRID_CENTER_FOLLOWS_MOUSE) {
            grid = gridCenteredAroundMouse(grid.builder()).build();
            gridChanged();
        }
    }

    @Override
    public void modeChanged(Mode newMode) {
        GridConfiguration gridConfiguration = newMode.grid();
        GridBuilder gridBuilder = //
                new GridBuilder().rowCount(gridConfiguration.rowCount())
                                 .columnCount(gridConfiguration.columnCount())
                                 .lineVisible(gridConfiguration.lineVisible())
                                 .lineHexColor(gridConfiguration.lineHexColor())
                                 .lineThickness(gridConfiguration.lineThickness());
        switch (gridConfiguration.area()) {
            case GridArea.ActiveScreen activeScreen -> {
                Monitor monitor = monitorManager.activeMonitor();
                int gridWidth = (int) (monitor.width() * activeScreen.screenWidthPercent());
                int gridHeight =
                        (int) (monitor.height() * activeScreen.screenHeightPercent());
                gridBuilder.x(monitor.x() + (monitor.width() - gridWidth) / 2)
                                 .y(monitor.y() + (monitor.height() - gridHeight) / 2)
                                 .width(gridWidth)
                                 .height(gridHeight);
            }
            case GridArea.ActiveWindow activeWindow -> {
                Rectangle activeWindowRectangle = WindowsOverlay.activeWindowRectangle(
                        activeWindow.windowWidthPercent(),
                        activeWindow.windowHeightPercent());
                gridBuilder.x(activeWindowRectangle.x())
                           .y(activeWindowRectangle.y())
                           .width(activeWindowRectangle.width())
                           .height(activeWindowRectangle.height());
            }
        };
        if (gridConfiguration.synchronization() ==
            Synchronization.GRID_CENTER_FOLLOWS_MOUSE)
            gridCenteredAroundMouse(gridBuilder);
        Grid newGrid = gridBuilder.build();
        if (currentMode != null &&
            newMode.grid().equals(currentMode.grid()) &&
            newGrid.equals(grid))
            return;
        currentMode = newMode;
        grid = newGrid;
        gridChanged();
    }

    private void gridChanged() {
        if (grid.lineVisible())
            WindowsOverlay.setGrid(grid);
        else
            WindowsOverlay.hideGrid();
        Synchronization synchronization =
                currentMode.grid().synchronization();
        if (synchronization == Synchronization.MOUSE_FOLLOWS_GRID_CENTER)
            mouseController.moveTo(grid.x() + grid.width() / 2,
                    grid.y() + grid.height() / 2);
    }

    @Override
    public void modeTimedOut() {
        // No op.
    }

}

