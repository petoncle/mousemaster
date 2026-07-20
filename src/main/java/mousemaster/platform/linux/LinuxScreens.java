package mousemaster.platform.linux;

import com.sun.jna.Pointer;
import mousemaster.Rectangle;
import mousemaster.Screen;
import mousemaster.platform.Screens;
import mousemaster.platform.linux.LibXRandr.IntByReference;
import mousemaster.platform.linux.LibXRandr.XRRMonitorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class LinuxScreens implements Screens {

    private static final Logger logger = LoggerFactory.getLogger(LinuxScreens.class);
    private final Pointer display;

    public LinuxScreens(Pointer display) {
        this.display = display;
    }

    @Override
    public Set<Screen> findScreens() {
        Set<Screen> screens = new HashSet<>();

        long rootWindow = LibX11.INSTANCE.XDefaultRootWindow(display);
        IntByReference nmonitorsRef = new IntByReference(0);

        // This is a synchronous X server round-trip called on the hot path (every
        // Mousemaster.run() tick, uncached). If the X server ever stalls, this is the
        // most likely place the whole single-threaded main loop would hang - log
        // loudly (WARN, so it shows regardless of configured log level) if it ever
        // takes unusually long, to pin down a future freeze instead of just seeing a
        // gap in the log.
        long callStartNanos = System.nanoTime();
        Pointer monitorsPtr = LibXRandr.INSTANCE.XRRGetMonitors(display, rootWindow, true, nmonitorsRef);
        double callDurationSeconds = (System.nanoTime() - callStartNanos) / 1_000_000_000.0;
        if (callDurationSeconds > 1)
            logger.warn("XRRGetMonitors took {}s - possible X server stall", callDurationSeconds);

        if (monitorsPtr == null) {
            logger.warn("XRRGetMonitors returned null - no monitors detected");
            return screens;
        }

        int nmonitors = nmonitorsRef.getValue();
        logger.debug("Detected {} monitor(s)", nmonitors);

        XRRMonitorInfo monitorInfo = new XRRMonitorInfo(monitorsPtr);
        XRRMonitorInfo[] monitorArray = (XRRMonitorInfo[]) monitorInfo.toArray(nmonitors);

        for (int i = 0; i < nmonitors; i++) {
            XRRMonitorInfo monitor = monitorArray[i];

            Rectangle rectangle = new Rectangle(
                    monitor.x,
                    monitor.y,
                    monitor.width,
                    monitor.height
            );

            int dpi = calculateDpi(monitor.width, monitor.mwidth);
            double scale = dpi / 96.0;

            logger.debug("Monitor {}: {}x{} at ({},{}), {}mm x {}mm, DPI={}, scale={}",
                    i, monitor.width, monitor.height, monitor.x, monitor.y,
                    monitor.mwidth, monitor.mheight, dpi, scale);

            screens.add(new Screen(rectangle, dpi, scale));
        }

        LibXRandr.INSTANCE.XRRFreeMonitors(monitorsPtr);

        return screens;
    }

    private int calculateDpi(int pixels, int millimeters) {
        if (millimeters <= 0) {
            logger.warn("Invalid physical dimension ({}mm), using default 96 DPI", millimeters);
            return 96;
        }

        double inches = millimeters / 25.4;
        int dpi = (int) Math.round(pixels / inches);

        if (dpi < 50 || dpi > 500) {
            logger.warn("Calculated DPI {} is out of reasonable range, using 96 DPI", dpi);
            return 96;
        }

        return dpi;
    }

}
