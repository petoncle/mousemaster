package mousemaster.platform.linux;

import com.sun.jna.Pointer;
import mousemaster.*;
import mousemaster.platform.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Linux platform implementation.
 * Milestone 1: Basic structure with stubs - focuses on grid display.
 * TODO: Implement full keyboard/mouse hooks for Milestones 2-3.
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

    private KeyboardLayout activeKeyboardLayout;
    private MouseManager mouseManager;
    private KeyboardManager keyboardManager;
    private List<MousePositionListener> mousePositionListeners;
    private ModeMap modeMap;
    private long rootWindow;
    private boolean keyboardGrabbed = false;
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
        keyboard = new LinuxKeyboard();
        mouse = new LinuxMouse();
        screens = new LinuxScreens(display);
        overlay = new LinuxOverlay(display);
        overlay.setPlatform(this);
        uiAutomation = new LinuxUiAutomation();
        activeAppFinder = new LinuxActiveAppFinder();
        console = new LinuxConsole();
        keyRegurgitator = new KeyRegurgitator(keyboard);

        logger.info("LinuxPlatform initialized successfully");

        // Get root window for event monitoring
        rootWindow = LibX11.INSTANCE.XDefaultRootWindow(display);
        logger.info("Root window handle: {}", rootWindow);

        if (!isWayland) {
            LibX11.INSTANCE.XSelectInput(display, rootWindow,
                    LibX11.KeyPressMask | LibX11.KeyReleaseMask);
            logger.info("XSelectInput called with mask: {}",
                    (LibX11.KeyPressMask | LibX11.KeyReleaseMask));
            LibX11.INSTANCE.XFlush(display);
            logger.info("Keyboard event monitoring setup on root window");
        }

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
            String key = keyboardSimulator.pollKey();
            while (key != null) {
                logger.info("Simulated keypress: {}", key);
                overlay.handleKeyPress(key);
                key = keyboardSimulator.pollKey();
            }
        }

        int pending = LibX11.INSTANCE.XPending(display);
        if (pending > 0 && !isWayland) {
            logger.debug("X11 events pending: {}", pending);
        }

        int eventCount = 0;
        while (LibX11.INSTANCE.XPending(display) > 0) {
            LibX11.XEvent event = new LibX11.XEvent();
            LibX11.INSTANCE.XNextEvent(display, event);
            eventCount++;

            if (!isWayland) {
                if (event.type == LibX11.KeyPress) {
                    LibX11.XKeyEvent keyEvent = event.getKeyEvent();
                    long keysym = LibX11.INSTANCE.XLookupKeysym(keyEvent, 0);
                    String keyString = LibX11.INSTANCE.XKeysymToString(keysym);

                    logger.info("KeyPress detected: {} (keycode: {}, state: {}, window: {})",
                            keyString, keyEvent.keycode, keyEvent.state, keyEvent.window);

                    if (keyString != null) {
                        overlay.handleKeyPress(keyString);
                    }
                } else if (event.type == LibX11.KeyRelease) {
                    LibX11.XKeyEvent keyEvent = event.getKeyEvent();
                    long keysym = LibX11.INSTANCE.XLookupKeysym(keyEvent, 0);
                    String keyString = LibX11.INSTANCE.XKeysymToString(keysym);
                    logger.debug("KeyRelease: {} (keycode: {}, window: {})",
                            keyString, keyEvent.keycode, keyEvent.window);
                }
            }
        }

        if (eventCount > 0 && !isWayland) {
            logger.trace("Processed {} X11 events total", eventCount);
        }
    }

    public void grabKeyboard() {
        if (isWayland) {
            logger.info("Running on Wayland - using keyboard simulator instead of X11 grab");
            logger.info("Type letters in the terminal and press Enter to simulate keypresses");
            keyboardGrabbed = true;
            return;
        }

        if (!keyboardGrabbed) {
            logger.info("Attempting to grab keyboard on root window: {}", rootWindow);
            LibX11.INSTANCE.XFlush(display);

            int result = LibX11.INSTANCE.XGrabKeyboard(display, rootWindow, 1,
                    LibX11.GrabModeAsync, LibX11.GrabModeAsync, LibX11.CurrentTime);

            LibX11.INSTANCE.XFlush(display);
            logger.info("XGrabKeyboard returned: {}", result);

            if (result == LibX11.GrabSuccess) {
                keyboardGrabbed = true;
                logger.info("Keyboard grabbed successfully on window {}", rootWindow);
                pumpEvents();
            } else {
                String errorMsg = switch (result) {
                    case 1 -> "AlreadyGrabbed";
                    case 2 -> "GrabInvalidTime";
                    case 3 -> "GrabNotViewable";
                    case 4 -> "GrabFrozen";
                    default -> "Unknown error " + result;
                };
                logger.error("Failed to grab keyboard: {} (code: {})", errorMsg, result);
            }
        } else {
            logger.debug("Keyboard already grabbed");
        }
    }

    public void ungrabKeyboard() {
        if (keyboardGrabbed) {
            if (!isWayland) {
                LibX11.INSTANCE.XUngrabKeyboard(display, LibX11.CurrentTime);
                LibX11.INSTANCE.XFlush(display);
            }
            keyboardGrabbed = false;
            logger.info("Keyboard ungrabbed");
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
        logger.debug("reset() called");
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
