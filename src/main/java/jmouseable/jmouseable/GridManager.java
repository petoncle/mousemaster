package jmouseable.jmouseable;

/**
 * Displays the grid and handles grid commands.
 */
public class GridManager {

    private final MonitorManager monitorManager;
    private GridConfiguration gridConfiguration;
    private Grid grid;

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

}
