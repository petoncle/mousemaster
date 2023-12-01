package jmouseable.jmouseable;

import jmouseable.jmouseable.Grid.GridBuilder;

import java.util.ArrayList;
import java.util.List;

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
        int rowWidth = Math.max(1, grid.width() / grid.rowCount());
        int columnHeight = Math.max(1, grid.height() / grid.columnCount());
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
        if (currentMode.gridConfiguration().synchronization() ==
            Synchronization.GRID_CENTER_FOLLOWS_MOUSE) {
            grid = gridCenteredAroundMouse(grid.builder()).build();
            gridChanged();
        }
    }

    @Override
    public void modeChanged(Mode newMode) {
        GridConfiguration gridConfiguration = newMode.gridConfiguration();
        GridBuilder gridBuilder = //
                new GridBuilder().rowCount(gridConfiguration.rowCount())
                                 .columnCount(gridConfiguration.columnCount())
                                 .lineVisible(gridConfiguration.lineVisible())
                                 .lineHexColor(gridConfiguration.lineHexColor())
                                 .lineThickness(gridConfiguration.lineThickness());
        GridBuilder newGridBuilder = switch (gridConfiguration.area()) {
            case Area.ActiveScreen activeScreen -> {
                Monitor monitor = monitorManager.activeMonitor();
                int gridWidth = (int) (monitor.width() * activeScreen.screenWidthPercent());
                int gridHeight =
                        (int) (monitor.height() * activeScreen.screenHeightPercent());
                yield gridBuilder.x(monitor.x() + (monitor.width() - gridWidth) / 2)
                                 .y(monitor.y() + (monitor.height() - gridHeight) / 2)
                                 .width(gridWidth)
                                 .height(gridHeight);
            }
            case Area.ActiveWindow activeWindow ->
                    WindowsOverlay.gridFittingActiveWindow(gridBuilder,
                            activeWindow.windowWidthPercent(),
                            activeWindow.windowHeightPercent());
        };
        if (gridConfiguration.synchronization() ==
            Synchronization.GRID_CENTER_FOLLOWS_MOUSE)
            gridCenteredAroundMouse(newGridBuilder);
        buildHints(newGridBuilder, gridConfiguration);
        Grid newGrid = newGridBuilder.build();
        if (currentMode != null &&
            newMode.gridConfiguration().equals(currentMode.gridConfiguration()) &&
            newGrid.equals(grid))
            return;
        currentMode = newMode;
        grid = newGrid;
        gridChanged();
    }

    private void buildHints(GridBuilder grid, GridConfiguration gridConfiguration) {
        grid.hintEnabled(gridConfiguration.hintEnabled())
            .hintFontName(gridConfiguration.hintFontName())
            .hintFontSize(gridConfiguration.hintFontSize())
            .hintFontHexColor(gridConfiguration.hintFontHexColor())
            .hintBoxHexColor(gridConfiguration.hintBoxHexColor());
        if (!gridConfiguration.hintEnabled())
            return;
        if (currentMode != null) {
            GridConfiguration oldGridConfiguration = currentMode.gridConfiguration();
            if (oldGridConfiguration.hintEnabled() &&
                oldGridConfiguration.rowCount() == gridConfiguration.rowCount() &&
                oldGridConfiguration.columnCount() == gridConfiguration.columnCount() &&
                oldGridConfiguration.hintKeys().equals(gridConfiguration.hintKeys()) &&
                oldGridConfiguration.hintFontName()
                                    .equals(gridConfiguration.hintFontName()) &&
                oldGridConfiguration.hintFontSize() == gridConfiguration.hintFontSize() &&
                oldGridConfiguration.hintFontHexColor()
                                    .equals(gridConfiguration.hintFontHexColor()) &&
                oldGridConfiguration.hintBoxHexColor()
                                    .equals(gridConfiguration.hintBoxHexColor())) {
                grid.hints(this.grid.hints());
                return;
            }
        }
        int rowCount = gridConfiguration.rowCount();
        int columnCount = gridConfiguration.columnCount();
        List<Key> keys = gridConfiguration.hintKeys();
        int hintCount = rowCount * columnCount;
        // Find hintLength such that hintKeyCount^hintLength >= rowCount*columnCount
        int hintLength = (int) Math.ceil(Math.log(hintCount) / Math.log(keys.size()));
        Hint[][] hints = new Hint[rowCount][columnCount];
        int cellIndex = 0;
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                List<Key> keySequence = new ArrayList<>();
                // We want the hints to look like this:
                // aa, ab, ac, ..., az,
                // ba, bb, bc, ..., bz,
                // za, zb, zc, ..., zz
                // The ideal situation is when rowCount = columnCount = hintKeys.size().
                for (int i = 0; i < hintLength; i++)
                    keySequence.add(keys.get(
                            (int) (cellIndex / Math.pow(keys.size(), hintLength - 1 - i) %
                                   keys.size())));
                cellIndex++;
                hints[rowIndex][columnIndex] = new Hint(keySequence);
            }
        }
        grid.hints(hints);
    }

    private void gridChanged() {
        if (grid.lineVisible() || grid.hintEnabled())
            WindowsOverlay.setGrid(grid);
        else
            WindowsOverlay.hideGrid();
        if (currentMode.gridConfiguration().synchronization() ==
            Synchronization.MOUSE_FOLLOWS_GRID_CENTER)
            mouseController.moveTo(grid.x() + grid.width() / 2,
                    grid.y() + grid.height() / 2);
    }

    @Override
    public void modeTimedOut() {
        // No op.
    }

}
