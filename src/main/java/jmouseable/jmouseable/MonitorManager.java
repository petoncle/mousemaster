package jmouseable.jmouseable;

import java.util.Set;

public class MonitorManager {

    public Monitor activeMonitor() {
        return null;
    }

    public Set<Monitor> monitors() {
        return WindowsMonitor.findMonitors();
    }

    public record Monitor(int x, int y, int width, int height) {

    }

}
