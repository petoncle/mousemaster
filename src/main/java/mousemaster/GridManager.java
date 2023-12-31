package mousemaster;

import mousemaster.Grid.GridBuilder;

import java.util.List;

/**
 * Displays the grid and handles grid commands.
 */
public class GridManager implements MousePositionListener, ModeListener {

    private final ScreenManager screenManager;
    private final MouseController mouseController;
    private List<GridListener> listeners;
    private Grid grid;
    private int mouseX, mouseY;
    private Mode currentMode;

    public GridManager(ScreenManager screenManager, MouseController mouseController) {
        this.screenManager = screenManager;
        this.mouseController = mouseController;
    }

    public void setListeners(List<GridListener> listeners) {
        this.listeners = listeners;
    }

    public void shrinkGridUp() {
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

    public void shrinkGridDown() {
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

    public void shrinkGridLeft() {
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

    public void shrinkGridRight() {
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
        if (screenManager.screenContaining(gridCenterX, gridCenterY) == null) {
            // We want the grid center in screen.
            Screen activeScreen = screenManager.activeScreen();
            gridCenterX = Math.max(activeScreen.rectangle().x(), Math.min(
                    activeScreen.rectangle().x() + activeScreen.rectangle().width(),
                    gridCenterX));
            gridCenterY = Math.max(activeScreen.rectangle().y(), Math.min(
                    activeScreen.rectangle().y() + activeScreen.rectangle().height(),
                    gridCenterY));
            x = gridCenterX - grid.width() / 2;
            y = gridCenterY - grid.height() / 2;
        }
        Grid newGrid = grid.builder().x(x).y(y).build();
        if (newGrid.equals(grid))
            return;
        grid = newGrid;
        gridChanged();
    }

    public void moveGridUp() {
        moveGrid(0, -grid.height());
    }

    public void moveGridDown() {
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
                Rectangle.rectangleContains(grid.x(), grid.y(), grid.width(),
                        grid.height(), mouseX, mouseY);
        int x, y;
        int cellWidth = Math.max(1, grid.width() / grid.columnCount());
        int cellHeight = Math.max(1, grid.height() / grid.rowCount());
        if (mouseIsInsideGrid) {
            int mouseColumn = (mouseX - grid.x()) / cellWidth;
            int mouseRow = (mouseY - grid.y()) / cellHeight;
            if (horizontal) {
                int newMouseColumn =
                        forward ? Math.min(grid.columnCount(), mouseColumn + 1) :
                                Math.max(0, mouseColumn -
                                            ((mouseX - grid.x()) % cellWidth <= 1 ? 1 :
                                                    0));
                if (newMouseColumn == 0)
                    x = grid.x();
                else if (newMouseColumn == grid.columnCount())
                    x = grid.x() + grid.width();
                else
                    x = grid.x() + newMouseColumn * cellWidth;
                y = mouseY;
            }
            else {
                x = mouseX;
                int newMouseRow =
                        forward ? Math.min(grid.rowCount(), mouseRow + 1) :
                                Math.max(0, mouseRow -
                                            ((mouseY - grid.y()) % cellHeight <= 1 ? 1 :
                                                    0));
                if (newMouseRow == 0)
                    y = grid.y();
                else if (newMouseRow == grid.rowCount())
                    y = grid.y() + grid.height();
                else
                    y = grid.y() + newMouseRow * cellHeight;
            }
            if (screenManager.screenContaining(x, y) == null) {
                Screen activeScreen = screenManager.activeScreen();
                x = Math.max(activeScreen.rectangle().x(), Math.min(
                        activeScreen.rectangle().x() + activeScreen.rectangle().width(),
                        x));
                y = Math.max(activeScreen.rectangle().y(), Math.min(
                        activeScreen.rectangle().y() + activeScreen.rectangle().height(),
                        y));
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
        listeners.forEach(GridListener::snappedToGrid);
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
            case GridArea.ActiveScreenGridArea activeScreenGridArea -> {
                Screen screen = screenManager.activeScreen();
                int gridWidth = (int) (screen.rectangle().width() *
                                       activeScreenGridArea.widthPercent());
                int gridHeight = (int) (screen.rectangle().height() *
                                        activeScreenGridArea.heightPercent());
                gridBuilder.x(screen.rectangle().x() +
                              (screen.rectangle().width() - gridWidth) / 2)
                           .y(screen.rectangle().y() +
                              (screen.rectangle().height() - gridHeight) / 2)
                           .width(gridWidth)
                           .height(gridHeight);
            }
            case GridArea.ActiveWindowGridArea activeWindowGridArea -> {
                Rectangle activeWindowRectangle = WindowsOverlay.activeWindowRectangle(
                        activeWindowGridArea.widthPercent(),
                        activeWindowGridArea.heightPercent());
                gridBuilder.x(activeWindowRectangle.x())
                           .y(activeWindowRectangle.y())
                           .width(activeWindowRectangle.width())
                           .height(activeWindowRectangle.height());
            }
        }
        ;
        if (gridConfiguration.synchronization() ==
            Synchronization.GRID_CENTER_FOLLOWS_MOUSE)
            gridCenteredAroundMouse(gridBuilder);
        Grid newGrid = gridBuilder.build();
        if (currentMode != null && newMode.grid().equals(currentMode.grid()) &&
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
        Synchronization synchronization = currentMode.grid().synchronization();
        if (synchronization == Synchronization.MOUSE_FOLLOWS_GRID_CENTER)
            mouseController.moveTo(grid.x() + grid.width() / 2,
                    grid.y() + grid.height() / 2);
    }

    @Override
    public void modeTimedOut() {
        // No op.
    }

}

