package mousemaster.platform.linux;

import com.sun.jna.Pointer;
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
    private LinuxKeyboardSimulator keyboardSimulator;

    public LinuxPlatform(boolean multipleInstancesAllowed, boolean keyRegurgitationEnabled) {
        logger.info("Initializing LinuxPlatform");

        // Check if running under Wayland
        String sessionType = System.getenv("XDG_SESSION_TYPE");
        String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        isWayland = "wayland".equals(sessionType) || waylandDisplay != null;

        if (isWayland) {
            logger.warn("Running under Wayland - keyboard grabbing will use simulator mode");
            logger.warn("For production use, evdev-based input handling is required");
            keyboardSimulator = new LinuxKeyboardSimulator();
            keyboardSimulator.start();
        }

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

        // rootWindow must be obtained before creating LinuxMouse (mouse needs it for XWarpPointer)
        rootWindow = LibX11.INSTANCE.XDefaultRootWindow(display);
        logger.info("Root window handle: {}", rootWindow);

        mouse = new LinuxMouse(display, rootWindow);
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
        if (isWayland && keyboardSimulator != null && keyboardSimulator.hasKeys()) {
            String keysym = keyboardSimulator.pollKey();
            while (keysym != null) {
                Key key = LinuxVirtualKey.fromKeysym(keysym);
                if (key != null && keyboardManager != null)
                    keyboardManager.keyEvent(new KeyEvent.PressKeyEvent(Instant.now(), key));
                keysym = keyboardSimulator.pollKey();
            }
        }

        KeyEvent event = evdev.pollEvent();
        while (event != null) {
            logger.debug("evdev: {}", event);
            if (keyboardManager != null)
                keyboardManager.keyEvent(event);
            event = evdev.pollEvent();
        }
    }

    @Override
    public void sleep() throws InterruptedException {
        pumpEvents();
        Thread.sleep(10);
        pumpEvents();
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
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down LinuxPlatform");

        if (keyboardSimulator != null) {
            keyboardSimulator.stop();
        }

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
