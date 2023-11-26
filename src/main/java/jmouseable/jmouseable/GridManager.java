package jmouseable.jmouseable;

/**
 * Displays the grid and handles grid commands.
 */
public class GridManager implements MousePositionListener {

    private final MonitorManager monitorManager;
    private GridConfiguration gridConfiguration;
    private Grid grid;
    private int mouseX, mouseY;

    public GridManager(MonitorManager monitorManager) {
        this.monitorManager = monitorManager;
    }

    public void setGrid(GridConfiguration gridConfiguration) {
        this.gridConfiguration = gridConfiguration;
//        monitorManager.monitors()
//        new Grid()
        // TODO
    }

    public void keepGridTop() {

    }

    public void keepGridBottom() {

    }

    public void keepGridLeft() {

    }

    public void keepGridRight() {

    }

    @Override
    public void mouseMoved(int x, int y) {
        this.mouseX = x;
        this.mouseY = y;
    }
}
