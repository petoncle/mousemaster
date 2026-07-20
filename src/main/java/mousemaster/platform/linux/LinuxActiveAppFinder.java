package mousemaster.platform.linux;

import mousemaster.App;
import mousemaster.platform.ActiveAppFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub implementation of ActiveAppFinder for Milestone 1.
 * TODO: Implement using XGetInputFocus + _NET_WM_PID + /proc for Milestone 3.
 */
public class LinuxActiveAppFinder implements ActiveAppFinder {

    private static final Logger logger = LoggerFactory.getLogger(LinuxActiveAppFinder.class);

    @Override
    public App activeApp() {
        // TODO: Get active window via XGetInputFocus and read process name from /proc
        return new App("unknown");
    }

}
