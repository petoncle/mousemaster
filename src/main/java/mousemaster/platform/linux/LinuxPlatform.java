package mousemaster.platform.linux;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import mousemaster.*;
import mousemaster.platform.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Linux platform implementation. Wires together evdev input capture, uinput passthrough,
 * X11 mouse control, and Qt overlay rendering.
 */
public class LinuxPlatform implements Platform {

    private static final Logger logger = LoggerFactory.getLogger(LinuxPlatform.class);

    private final Pointer display;
    private final LinuxClock clock;
    private final LinuxKeyboard keyboard;
    private final LinuxMouse mouse;
    private final LinuxScreens screens;
    private final LinuxOverlay overlay;
    private final LinuxUiAutomation uiAutomation;
    private final LinuxActiveAppFinder activeAppFinder;
    private final LinuxConsole console;
    private final KeyRegurgitator keyRegurgitator;

    private final LinuxEvdev evdev;
    private KeyboardLayout activeKeyboardLayout;
    private MouseManager mouseManager;
    private KeyboardManager keyboardManager;
    private List<MousePositionListener> mousePositionListeners;
    private ModeMap modeMap;
    private long rootWindow;
    private final boolean isWayland;
    private Integer lastMouseX;
    private Integer lastMouseY;

    public LinuxPlatform(boolean multipleInstancesAllowed, boolean keyRegurgitationEnabled) {
        logger.info("Initializing LinuxPlatform");

        // Check if running under Wayland
        String sessionType = System.getenv("XDG_SESSION_TYPE");
        String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        isWayland = "wayland".equals(sessionType) || waylandDisplay != null;

        // Open X11 display connection (works even under XWayland for rendering)
        display = LibX11.INSTANCE.XOpenDisplay(null);
        if (display == null) {
            throw new IllegalStateException("Unable to open X11 display - is DISPLAY environment variable set?");
        }
        logger.info("X11 display opened successfully (Wayland={})", isWayland);

        // Initialize all platform components
        clock = new LinuxClock();
        int uinputKeyboardFd = LibUinput.createKeyboardDevice();
        keyboard = new LinuxKeyboard(uinputKeyboardFd);
        screens = new LinuxScreens(display);
        overlay = new LinuxOverlay(display);
        uiAutomation = new LinuxUiAutomation();
        activeAppFinder = new LinuxActiveAppFinder();
        console = new LinuxConsole();
        keyRegurgitator = new KeyRegurgitator(keyboard);

        // rootWindow must be obtained before creating X11Mouse (mouse needs it for XWarpPointer)
        rootWindow = LibX11.INSTANCE.XDefaultRootWindow(display);
        logger.info("Root window handle: {}", rootWindow);

        mouse = isWayland ? new WaylandMouse(screens) : new X11Mouse(display, rootWindow);
        evdev = new LinuxEvdev();

        logger.info("LinuxPlatform initialized successfully");

        // Default to US QWERTY layout until XKB detection is implemented (Milestone 3)
        activeKeyboardLayout = KeyboardLayout.keyboardLayoutByIdentifier.get("00000409");
        if (activeKeyboardLayout == null && !KeyboardLayout.keyboardLayoutByIdentifier.isEmpty()) {
            activeKeyboardLayout = KeyboardLayout.keyboardLayoutByIdentifier.values().iterator().next();
            logger.warn("US layout not found, using fallback: {}", activeKeyboardLayout.identifier());
        }
    }

    @Override
    public void update(double delta) {
        pumpEvents();
        overlay.update(delta);
    }

    @Override
    public void pumpEvents() {
        KeyEvent event = evdev.pollEvent();
        while (event != null) {
            logger.debug("evdev: {}", event);
            if (keyboardManager != null)
                handleKeyEvent(event);
            event = evdev.pollEvent();
        }
    }

    /**
     * Unlike Windows' low-level hook return value, Linux has no way to suppress a key
     * after grabbing the device exclusively (EVIOCGRAB) — regurgitates must be
     * explicitly re-injected here or they're silently dropped.
     */
    private void handleKeyEvent(KeyEvent event) {
        KeyboardManager.EatAndRegurgitates eatAndRegurgitates = keyboardManager.keyEvent(event);
        for (KeyboardManager.Regurgitate regurgitate : eatAndRegurgitates.regurgitates()) {
            keyRegurgitator.regurgitate(regurgitate, !regurgitate.alsoRelease());
        }
    }

    @Override
    public void sleep() throws InterruptedException {
        pumpEvents();
        notifyMousePositionListenersIfMoved();
        Thread.sleep(10);
        pumpEvents();
    }

    /**
     * Windows notifies MousePositionListener (e.g. MouseManager, to detect a smooth
     * jump reaching its destination) via its low-level mouse hook. X11 has no such
     * hook, so the position is polled instead.
     */
    private void notifyMousePositionListenersIfMoved() {
        int x;
        int y;
        // XWayland's XQueryPointer-mirrored pointer state doesn't reliably reflect motion
        // injected via zwlr_virtual_pointer_v1 (WaylandMouse), so prefer the position we
        // ourselves last told the compositor to move to over an XQueryPointer round-trip.
        Integer syntheticX = mouse.lastSyntheticX();
        Integer syntheticY = mouse.lastSyntheticY();
        if (syntheticX != null && syntheticY != null) {
            x = syntheticX;
            y = syntheticY;
        } else {
            LongByReference root = new LongByReference();
            LongByReference child = new LongByReference();
            IntByReference rootX = new IntByReference();
            IntByReference rootY = new IntByReference();
            IntByReference winX = new IntByReference();
            IntByReference winY = new IntByReference();
            IntByReference mask = new IntByReference();
            boolean sameScreen = LibX11.INSTANCE.XQueryPointer(display, rootWindow, root, child,
                    rootX, rootY, winX, winY, mask);
            if (!sameScreen)
                return;
            x = rootX.getValue();
            y = rootY.getValue();
        }
        boolean positionUnchanged = lastMouseX != null && lastMouseX == x &&
                                     lastMouseY != null && lastMouseY == y;
        if (positionUnchanged)
            return;
        lastMouseX = x;
        lastMouseY = y;
        for (MousePositionListener listener : mousePositionListeners) {
            listener.mouseMoved(x, y);
        }
    }

    @Override
    public void reset(MouseManager mouseManager, KeyboardManager keyboardManager,
                      ModeMap modeMap, List<MousePositionListener> mousePositionListeners,
                      KeyboardLayout activeKeyboardLayout) {
        this.mouseManager = mouseManager;
        this.keyboardManager = keyboardManager;
        this.mousePositionListeners = mousePositionListeners;
        this.modeMap = modeMap;
        this.activeKeyboardLayout = activeKeyboardLayout;
        overlay.setMessagePump(this::pumpEvents);
        lastMouseX = null;
        lastMouseY = null;
        notifyMousePositionListenersIfMoved();
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down LinuxPlatform");

        evdev.destroy();
        keyboard.destroy();
        mouse.destroy();

        if (display != null) {
            LibX11.INSTANCE.XCloseDisplay(display);
            logger.info("X11 display closed");
        }
    }

    @Override
    public KeyRegurgitator keyRegurgitator() {
        return keyRegurgitator;
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public KeyboardLayout activeKeyboardLayout() {
        return activeKeyboardLayout;
    }

    @Override
    public KeyboardController keyboard() {
        return keyboard;
    }

    @Override
    public mousemaster.platform.MouseController mouse() {
        return mouse;
    }

    @Override
    public Screens screens() {
        return screens;
    }

    @Override
    public Overlay overlay() {
        return overlay;
    }

    @Override
    public UiAutomation uiAutomation() {
        return uiAutomation;
    }

    @Override
    public ActiveAppFinder activeAppFinder() {
        return activeAppFinder;
    }

    @Override
    public Console console() {
        return console;
    }

    @Override
    public void modeChanged(Mode newMode) {
        logger.debug("Mode changed to: {}", newMode.name());
    }

    @Override
    public void modeTimedOut() {
    }

}
