package mousemaster.platform.macos;

import com.sun.jna.Pointer;
import io.qt.widgets.QWidget;

/**
 * The overlay properties of a window, the counterpart of the extended styles Windows
 * applies: above every other window, on every space, and never hit by the mouse.
 */
public final class MacosWindow {

    private static final ObjectiveC objectiveC = ObjectiveC.INSTANCE;
    private static final Pointer window = objectiveC.sel_registerName("window");
    private static final Pointer setLevel = objectiveC.sel_registerName("setLevel:");
    private static final Pointer setCollectionBehavior =
            objectiveC.sel_registerName("setCollectionBehavior:");
    private static final Pointer setIgnoresMouseEvents =
            objectiveC.sel_registerName("setIgnoresMouseEvents:");
    /** macOS derives a window's shadow from its alpha, so hints would each cast one. */
    private static final Pointer setHasShadow =
            objectiveC.sel_registerName("setHasShadow:");

    private static final Pointer sharedApplication =
            objectiveC.sel_registerName("sharedApplication");
    private static final Pointer setActivationPolicy =
            objectiveC.sel_registerName("setActivationPolicy:");

    private static final long accessoryPolicy = 1;
    private static final long screenSaverLevel = 1000;
    public static final long belowOverlaysLevel = screenSaverLevel - 1;
    private static final long canJoinAllSpaces = 1;
    private static final long stationary = 1 << 4;
    private static final long fullScreenAuxiliary = 1 << 8;

    private MacosWindow() {
    }

    /** The widget must be shown once for winId to have a window behind it. */
    public static Pointer nsWindow(QWidget widget) {
        return objectiveC.objc_msgSend(new Pointer(widget.winId()), window);
    }

    /**
     * A regular application shows a Dock icon and takes the foreground when it shows a window.
     * Must run before the first window is shown, or the application activates once anyway.
     */
    public static void makeAccessoryApplication() {
        Pointer application = objectiveC.objc_msgSend(
                objectiveC.objc_getClass("NSApplication"), sharedApplication);
        objectiveC.objc_msgSend(application, setActivationPolicy, accessoryPolicy);
    }

    public static void applyOverlayProperties(QWidget widget) {
        applyOverlayProperties(widget, screenSaverLevel);
    }

    /** The zoom goes below the overlays it magnifies the screen for. */
    public static void applyOverlayProperties(QWidget widget, long level) {
        Pointer nsWindow = nsWindow(widget);
        if (nsWindow == null)
            return;
        objectiveC.objc_msgSend(nsWindow, setLevel, level);
        objectiveC.objc_msgSend(nsWindow, setCollectionBehavior,
                canJoinAllSpaces | stationary | fullScreenAuxiliary);
        objectiveC.objc_msgSend(nsWindow, setIgnoresMouseEvents, 1);
        objectiveC.objc_msgSend(nsWindow, setHasShadow, 0);
    }

}
