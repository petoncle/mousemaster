package jmouseable.jmouseable;

import java.util.Set;

public class MonitorManager implements MousePositionListener {

    private int mouseX;
    private int mouseY;

    public Monitor activeMonitor() {
        return null;
    }

    public Set<Monitor> monitors() {
        return WindowsMonitor.findMonitors();
    }

    @Override
    public void mouseMoved(int x, int y) {
        this.mouseX = x;
        this.mouseY = y;
    }

    public record Monitor(int x, int y, int width, int height) {

    }

}
