package jmouseable.jmouseable;

import java.util.Set;

public class MonitorManager implements MousePositionListener {

    private int mouseX;
    private int mouseY;

    public Monitor activeMonitor() {
        Set<Monitor> monitors = monitors();
        for (Monitor monitor : monitors) {
            if (mouseX >= monitor.x() && mouseX <= monitor.x() + monitor.width() &&
                mouseY >= monitor.y() && mouseY <= monitor.y() + monitor.height())
                return monitor;
        }
        throw new IllegalStateException(
                "Unable to find active monitor for mouse position " + mouseX + " " +
                mouseY);
    }

    public Set<Monitor> monitors() {
        return WindowsMonitor.findMonitors();
    }

    @Override
    public void mouseMoved(int x, int y) {
        this.mouseX = x;
        this.mouseY = y;
    }

}
