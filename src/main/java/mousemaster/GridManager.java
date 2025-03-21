package mousemaster;

import mousemaster.Grid.GridBuilder;

/**
 * Displays the grid and handles grid commands.
 */
public class GridManager implements MousePositionListener, ModeListener {

    private final ScreenManager screenManager;
    private final MouseController mouseController;
    private Grid grid;
    private int mouseX, mouseY;
    private Mode currentMode;

    public GridManager(ScreenManager screenManager, MouseController mouseController) {
        this.screenManager = screenManager;
        this.mouseController = mouseController;
    }

    public void shrinkGridUp() {
        if (mouseController.jumping(false, false) &&
            currentMode.grid().synchronization() ==
            Synchronization.MOUSE_FOLLOWS_GRID_CENTER)
            return;
        grid = grid.builder()
                   .x(currentMode.grid().synchronization() !=
                      Synchronization.GRID_CENTER_FOLLOWS_MOUSE ? grid.x() :
                           mouseX - grid.width() / 2)
                   .y(currentMode.grid().synchronization() !=
                      Synchronization.GRID_CENTER_FOLLOWS_MOUSE ? grid.y() :
                           mouseY - grid.height() / 4)
                   .height(Math.max(1, grid.height() / 2 + grid.height() % 2))
                   .build();
        gridChanged();
    }

    public void shrinkGridDown() {
        if (mouseController.jumping(false, true) &&
            currentMode.grid().synchronization() ==
            Synchronization.MOUSE_FOLLOWS_GRID_CENTER)
            return;
        grid = grid.builder()
                   .x(currentMode.grid().synchronization() !=
                      Synchronization.GRID_CENTER_FOLLOWS_MOUSE ? grid.x() :
                           mouseX - grid.width() / 2)
                   .y(currentMode.grid().synchronization() !=
                      Synchronization.GRID_CENTER_FOLLOWS_MOUSE ?
                           grid.y() + grid.height() / 2 : mouseY - grid.height() / 4)
                   .height(Math.max(1, grid.height() / 2 + grid.height() % 2))
                   .build();
        gridChanged();
    }

    public void shrinkGridLeft() {
        if (mouseController.jumping(true, false) &&
            currentMode.grid().synchronization() ==
            Synchronization.MOUSE_FOLLOWS_GRID_CENTER)
            return;
        grid = grid.builder()
                   .x(currentMode.grid().synchronization() !=
                      Synchronization.GRID_CENTER_FOLLOWS_MOUSE ? grid.x() :
                           mouseX - grid.width() / 4)
                   .y(currentMode.grid().synchronization() !=
                      Synchronization.GRID_CENTER_FOLLOWS_MOUSE ? grid.y() :
                           mouseY - grid.height() / 2)
                   .width(Math.max(1, grid.width() / 2 + grid.width() % 2))
                   .build();
        gridChanged();
    }

    public void shrinkGridRight() {
        if (mouseController.jumping(true, true) &&
            currentMode.grid().synchronization() ==
            Synchronization.MOUSE_FOLLOWS_GRID_CENTER)
            return;
        grid = grid.builder()
                   .x(currentMode.grid().synchronization() !=
                      Synchronization.GRID_CENTER_FOLLOWS_MOUSE ?
                           grid.x() + grid.width() / 2 : mouseX - grid.width() / 4)
                   .y(currentMode.grid().synchronization() !=
                      Synchronization.GRID_CENTER_FOLLOWS_MOUSE ? grid.y() :
                           mouseY - grid.height() / 2)
                   .width(Math.max(1, grid.width() / 2 + grid.width() % 2))
                   .build();
        gridChanged();
    }

    private GridBuilder gridCenteredAroundMouse(GridBuilder grid) {
        return grid.x(mouseX - grid.width() / 2).y(mouseY - grid.height() / 2);
    }

    private void moveGrid(int deltaX, int deltaY) {
        if (mouseController.jumping(deltaX != 0, deltaX > 0 || deltaY > 0) &&
            currentMode.grid().synchronization() ==
            Synchronization.MOUSE_FOLLOWS_GRID_CENTER)
            return;
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
        moveMouseTo(grid.x() + grid.width() / 2, grid.y() + grid.height() / 2);
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
        if (mouseController.jumping(horizontal, forward))
            return;
        boolean mouseIsInsideGrid =
                Rectangle.rectangleContains(grid.x(), grid.y(), grid.width(),
                        grid.height(), mouseX, mouseY);
        int cellWidth = Math.max(1, grid.width() / grid.columnCount());
        int cellHeight = Math.max(1, grid.height() / grid.rowCount());
        int mouseColumn = Math.min(grid.columnCount(), Math.max(0, mouseX - grid.x()) / cellWidth);
        int mouseRow = Math.min(grid.rowCount(), Math.max(0, mouseY - grid.y()) / cellHeight);
        int x = mouseX, y = mouseY;
        if (mouseIsInsideGrid) {
            int nextMouseColumn = forward ? Math.min(grid.columnCount(), mouseColumn + 1) :
                    Math.max(0, mouseColumn - ((mouseX - grid.x()) % cellWidth <= 1 ? 1 : 0));
            int nextMouseRow = forward ? Math.min(grid.rowCount(), mouseRow + 1) :
                    Math.max(0, mouseRow - ((mouseY - grid.y()) % cellHeight <= 1 ? 1 : 0));
            if (horizontal)
                x = mouseColumnX(nextMouseColumn, cellWidth);
            else
                y = mouseRowY(nextMouseRow, cellHeight);
        }
        else {
            if (horizontal)
                x = mouseColumnX(mouseColumn + (forward ? 1 : -1), cellWidth);
            else
                y = mouseRowY(mouseRow + (forward ? 1 : -1), cellHeight);
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
        moveMouseTo(x, y);
    }

    private int mouseColumnX(int mouseColumn, int cellWidth) {
        int x;
        if (mouseColumn <= 0)
            x = grid.x();
        else if (mouseColumn >= grid.columnCount())
            x = grid.x() + grid.width();
        else
            x = grid.x() + mouseColumn * cellWidth;
        return x;
    }

    private int mouseRowY(int mouseRow, int cellHeight) {
        int y;
        if (mouseRow <= 0)
            y = grid.y();
        else if (mouseRow >= grid.rowCount())
            y = grid.y() + grid.height();
        else
            y = grid.y() + mouseRow * cellHeight;
        return y;
    }

    private void moveMouseTo(int x, int y) {
        mouseController.moveTo(x, y);
        mouseMoved(x, y);
    }

    @Override
    public void mouseMoved(int x, int y) {
        if (mouseController.jumping()) {
            mouseX = mouseController.jumpEndX();
            mouseY = mouseController.jumpEndY();
            return;
        }
        mouseX = x;
        mouseY = y;
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
        if (currentMode != null) {
            GridConfiguration oldGridConfiguration = currentMode.grid();
            if (oldGridConfiguration.area().equals(gridConfiguration.area()) &&
                oldGridConfiguration.synchronization()
                                    .equals(gridConfiguration.synchronization()) &&
                oldGridConfiguration.rowCount() == gridConfiguration.rowCount() &&
                oldGridConfiguration.columnCount() == gridConfiguration.columnCount()) {
                // Keep the position and size of the old grid.
                gridBuilder.x(this.grid.x())
                           .y(this.grid.y())
                           .width(this.grid.width())
                           .height(this.grid.height());
                currentMode = newMode;
                grid = gridBuilder.build();
                setOverlay();
                // Do not call gridChanged() to avoid repositioning the mouse.
                return;
            }
        }
        Screen screen = screenManager.activeScreen();
        int scaledTopInset = (int) (gridConfiguration.area().topInset() * screen.scale());
        int scaledBottomInset =
                (int) (gridConfiguration.area().bottomInset() * screen.scale());
        int scaledLeftInset =
                (int) (gridConfiguration.area().leftInset() * screen.scale());
        int scaledRightInset =
                (int) (gridConfiguration.area().rightInset() * screen.scale());
        switch (gridConfiguration.area()) {
            case GridArea.ActiveScreenGridArea activeScreenGridArea -> {
                int noInsetGridWidth = Math.max(1, (int) (screen.rectangle().width() *
                                                          activeScreenGridArea.widthPercent()));
                int gridWidth = Math.max(1,
                        noInsetGridWidth - scaledLeftInset - scaledRightInset);
                int noInsetGridHeight = Math.max(1, (int) (screen.rectangle().height() *
                                                           activeScreenGridArea.heightPercent()));
                int gridHeight = Math.max(1,
                        noInsetGridHeight - scaledTopInset - scaledBottomInset);
                gridBuilder.x(
                                   Math.min(screen.rectangle().x() + screen.rectangle().width(),
                                           screen.rectangle().x() + scaledLeftInset +
                                           (screen.rectangle().width() - noInsetGridWidth) / 2))
                           .y(Math.min(
                                   screen.rectangle().y() + screen.rectangle().height(),
                                   screen.rectangle().y() + scaledTopInset +
                                   (screen.rectangle().height() - noInsetGridHeight) / 2))
                           .width(gridWidth)
                           .height(gridHeight);
            }
            case GridArea.ActiveWindowGridArea activeWindowGridArea -> {
                Rectangle activeWindowRectangle = WindowsOverlay.activeWindowRectangle(
                        activeWindowGridArea.widthPercent(),
                        activeWindowGridArea.heightPercent(), scaledTopInset,
                        scaledBottomInset, scaledLeftInset, scaledRightInset);
                gridBuilder.x(activeWindowRectangle.x())
                           .y(activeWindowRectangle.y())
                           .width(activeWindowRectangle.width())
                           .height(activeWindowRectangle.height());
            }
        }
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
        setOverlay();
        Synchronization synchronization = currentMode.grid().synchronization();
        if (synchronization == Synchronization.MOUSE_FOLLOWS_GRID_CENTER)
            moveMouseTo(grid.x() + grid.width() / 2, grid.y() + grid.height() / 2);
    }

    private void setOverlay() {
        if (grid.lineVisible())
            WindowsOverlay.setGrid(grid);
        else
            WindowsOverlay.hideGrid();
    }

    @Override
    public void modeTimedOut() {
        // No op.
    }

}

