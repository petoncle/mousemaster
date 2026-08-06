package mousemaster.platform.macos;

import mousemaster.Clock;
import mousemaster.Key;
import mousemaster.KeyEvent;
import mousemaster.KeyEvent.PressKeyEvent;
import mousemaster.KeyEvent.ReleaseKeyEvent;
import mousemaster.KeyRegurgitator;
import mousemaster.KeyboardLayout;
import mousemaster.HintMeshConfiguration;
import mousemaster.KeyboardManager;
import mousemaster.Mode;
import mousemaster.ModeMap;
import mousemaster.MouseManager;
import mousemaster.MousePositionListener;
import mousemaster.Platform;
import mousemaster.QtManager;
import mousemaster.ZoomManager;
import mousemaster.platform.ActiveAppFinder;
import mousemaster.platform.Console;
import mousemaster.platform.KeyboardController;
import mousemaster.platform.MouseController;
import mousemaster.platform.Overlay;
import mousemaster.platform.Screens;
import mousemaster.platform.UiAutomation;
import io.qt.core.QPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MacosPlatform implements Platform {

    private static final Logger logger = LoggerFactory.getLogger(MacosPlatform.class);

    private final MacosKeyboardController keyboard = new MacosKeyboardController();
    private final MacosMouseController mouse = new MacosMouseController();
    private final MacosScreens screens = new MacosScreens();
    private final MacosOverlay overlay = new MacosOverlay(mouse);
    private final MacosUiAutomation uiAutomation = new MacosUiAutomation();
    private final MacosActiveAppFinder activeAppFinder = new MacosActiveAppFinder();
    private final MacosConsole console = new MacosConsole();
    private final KeyRegurgitator keyRegurgitator = new KeyRegurgitator(keyboard);
    private static final Instant clockBase = Instant.now();
    private static final long clockBaseNanos = System.nanoTime();
    /** Monotonic: a wall clock correction must not change how long a key looks held. */
    private final Clock clock =
            () -> clockBase.plusNanos(System.nanoTime() - clockBaseNanos);

    private final boolean keyRegurgitationEnabled;
    private final String grabbedDeviceProduct;
    private final String simulationKeyEvents;
    /** wait_key blocks on its own thread, so events are handed to the loop thread. */
    private final LinkedBlockingDeque<KeyEvent> keyEventQueue = new LinkedBlockingDeque<>();
    /** An event no key maps to, a media key for instance, which is passed on as it is. */
    private final LinkedBlockingDeque<Driverkit.DKEvent> unmappedEventQueue =
            new LinkedBlockingDeque<>();
    private KeyboardManager keyboardManager;
    private List<MousePositionListener> mousePositionListeners;
    private ModeMap modeMap;
    private ZoomManager zoomManager;
    private QPoint lastMousePosition;
    private boolean wasPressingPhysicalButton;
    private boolean grabPaused;
    private double grabPausedCheckTimer;
    private volatile boolean regrabbed;
    private Thread hidThread;
    private Thread simulationThread;
    private volatile boolean grabbed;
    /** Read by the hid thread, which regrabs a released library if it misses the write. */
    private volatile boolean shutdown;
    private volatile boolean killProcessRequested;
    private FileChannel singleInstanceChannel;

    /** A null grabbedDeviceProduct grabs every keyboard but Karabiner's own devices. */
    public MacosPlatform(boolean multipleInstancesAllowed, boolean keyRegurgitationEnabled,
                         String grabbedDeviceProduct, String simulationKeyEvents) {
        this.keyRegurgitationEnabled = keyRegurgitationEnabled;
        this.grabbedDeviceProduct = grabbedDeviceProduct;
        this.simulationKeyEvents = simulationKeyEvents;
        // Before grabbing: a second instance would take the device and fail on that instead.
        if (!multipleInstancesAllowed && !acquireSingleInstanceLock())
            throw new IllegalStateException("Another instance is already running");
        // Without root the driver client retries forever, printing only connect_failed.
        if (!"root".equals(System.getProperty("user.name")))
            throw new IllegalStateException("mousemaster must run as root on macOS");
        if (!Driverkit.INSTANCE.driver_activated())
            throw new IllegalStateException(
                    "The Karabiner VirtualHIDDevice driver is not activated");
        if (!Driverkit.INSTANCE.register_device(grabbedDeviceProduct)) {
            Driverkit.INSTANCE.list_keyboards();
            throw new IllegalStateException("No keyboard to grab" +
                                            (grabbedDeviceProduct == null ? "" :
                                                    " matches '" + grabbedDeviceProduct + "'"));
        }
        int grab = Driverkit.INSTANCE.grab();
        if (grab != 0)
            throw new IllegalStateException("Unable to grab the keyboard, grab returned " +
                                            grab + ". mousemaster must run as root and " +
                                            "have the Input Monitoring permission");
        grabbed = true;
        // The grabbed keyboard reaches the os only through the sink, which takes about a
        // second to come up: keys pressed before that are eaten and never sent on.
        for (int attempt = 0; attempt < 50 && !Driverkit.INSTANCE.is_sink_ready(); attempt++)
            sleepMillis(100);
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        handleTerminationSignals();
    }

    /** The jvm exit sequence disposes Qt from the signal thread, under the main thread. */
    private void handleTerminationSignals() {
        SignalHandler handler = signal -> {
            killProcessRequested = true;
            // The process normally dies during this wait, so the warning below means it did not.
            for (int attempt = 0; attempt < 400; attempt++)
                sleepMillis(5);
            logger.warn("Main thread is not responding, killing the process from " + signal);
            killProcess(0);
        };
        Signal.handle(new Signal("INT"), handler);
        Signal.handle(new Signal("TERM"), handler);
    }

    @Override
    public void update(double delta) {
        if (killProcessRequested)
            killProcess(0);
        updateGrabPaused(delta);
        for (Driverkit.DKEvent unmapped; (unmapped = unmappedEventQueue.poll()) != null;)
            Driverkit.INSTANCE.send_key(unmapped);
        for (KeyEvent keyEvent; (keyEvent = keyEventQueue.poll()) != null;)
            keyEvent(keyEvent);
        keyboard.update(delta);
        overlay.update(delta);
    }

    private void updateGrabPaused(double delta) {
        if (regrabbed) {
            regrabbed = false;
            grabPaused = false;
            keyboardManager.reset();
        }
        grabPausedCheckTimer -= delta;
        if (grabPausedCheckTimer > 0)
            return;
        grabPausedCheckTimer = 0.2d;
        if (grabPaused || !MacosSession.grabPaused())
            return;
        grabPaused = true;
        // Released rather than passed through, so a mousemaster that hangs or is killed while
        // the screen is locked cannot leave the keyboard dead. The hid thread takes it back.
        logger.info(
                "Releasing the keyboard: the screen is locked or another user has the console");
        Driverkit.INSTANCE.release_input_only();
        keyboardManager.reset();
    }

    private void keyEvent(KeyEvent keyEvent) {
        // Whatever was already queued when the keyboard was released still has to reach the os.
        if (grabPaused) {
            keyboard.send(keyEvent.key(), keyEvent.isPress());
            return;
        }
        KeyboardManager.EatAndRegurgitates eatAndRegurgitates =
                keyboardManager.keyEvent(keyEvent);
        if (keyRegurgitationEnabled) {
            for (KeyboardManager.Regurgitate regurgitate : eatAndRegurgitates.regurgitates())
                keyRegurgitator.regurgitate(regurgitate,
                        !regurgitate.alsoRelease() && !keyEvent.isRelease());
        }
        if (!eatAndRegurgitates.mustBeEaten())
            keyboard.send(keyEvent.key(), keyEvent.isPress());
    }

    @Override
    public void pumpEvents() {
    }

    @Override
    public void sleep() throws InterruptedException {
        QPoint mousePosition = mouse.findMousePosition();
        if (!mousePosition.equals(lastMousePosition)) {
            lastMousePosition = mousePosition;
            for (MousePositionListener listener : mousePositionListeners)
                listener.mouseMoved(mousePosition.x(), mousePosition.y());
            overlay.mouseMoved(mousePosition);
        }
        // So that a shift click works while shift is being eaten. The Windows mouse hook
        // does this on the button event; here the button state is polled with the position.
        boolean pressingPhysicalButton = mouse.pressingPhysicalButton();
        if (pressingPhysicalButton && !wasPressingPhysicalButton &&
            keyboardManager.pressingKeys()) {
            logger.info(
                    "Regurgitating pressed keys because physical mouse buttons are being used");
            keyboardManager.regurgitatePressedKeys();
        }
        wasPressingPhysicalButton = pressingPhysicalButton;
        long sleepMillis =
                overlay.hintTransitionAnimating() || zoomManager.animating() ? 1 : 10;
        KeyEvent keyEvent = keyEventQueue.poll(sleepMillis, TimeUnit.MILLISECONDS);
        if (keyEvent != null)
            keyEventQueue.addFirst(keyEvent);
    }

    @Override
    public void reset(MouseManager mouseManager, KeyboardManager keyboardManager,
                      ModeMap newModeMap, ZoomManager zoomManager,
                      List<MousePositionListener> mousePositionListeners,
                      KeyboardLayout activeKeyboardLayout) {
        // The first call after Qt exists and before any overlay window is shown.
        MacosWindow.makeAccessoryApplication();
        this.keyboardManager = keyboardManager;
        this.zoomManager = zoomManager;
        this.mousePositionListeners = mousePositionListeners;
        if (keyboard.activeKeyboardLayout != null &&
            !keyboard.activeKeyboardLayout.equals(activeKeyboardLayout)) {
            keyboardManager.reset();
            keyboard.reset();
        }
        keyboard.activeKeyboardLayout = activeKeyboardLayout;
        Set<HintMeshConfiguration> oldHintMeshConfigurations = modeMap == null ? Set.of() :
                modeMap.modes().stream().map(Mode::hintMesh).collect(Collectors.toSet());
        Set<HintMeshConfiguration> newHintMeshConfigurations =
                newModeMap.modes().stream().map(Mode::hintMesh).collect(Collectors.toSet());
        if (modeMap != null) {
            logger.debug("Flushing overlay cache because the configuration was reloaded");
            overlay.flushCache();
        }
        if (!newHintMeshConfigurations.equals(oldHintMeshConfigurations))
            overlay.preWarmFontsAndWindows(newHintMeshConfigurations);
        this.modeMap = newModeMap;
        QPoint mousePosition = mouse.findMousePosition();
        mousePositionListeners.forEach(
                listener -> listener.mouseMoved(mousePosition.x(), mousePosition.y()));
        overlay.setMessagePump(QtManager::processEvents);
        if (hidThread == null)
            startHidThread();
        if (simulationKeyEvents != null && simulationThread == null)
            startSimulationThread();
    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private void startHidThread() {
        hidThread = new Thread(() -> {
            while (!shutdown) {
                readEvents();
                if (shutdown)
                    break;
                // A paused session released the keyboard on purpose, so wait rather than
                // take it straight back.
                while (!shutdown && MacosSession.grabPaused())
                    sleepMillis(200);
                if (shutdown)
                    break;
                // The devices were released, which happens on a sleep or a hotplug.
                // Regrabbing makes a fresh pipe and listener without touching the sink.
                if (!Driverkit.INSTANCE.regrab_input()) {
                    logger.info("The keyboard was released");
                    grabbed = false;
                    break;
                }
                logger.info("Regrabbed the keyboard");
                regrabbed = true;
            }
        });
        hidThread.setName("macos-hid");
        hidThread.setDaemon(true);
        hidThread.start();
    }

    /** A token is +key, -key, a bare key for a tap, or a number of milliseconds to wait. */
    private void startSimulationThread() {
        simulationThread = new Thread(() -> {
            for (String token : simulationKeyEvents.trim().split("\\s+")) {
                try {
                    if (token.matches("\\d+")) {
                        Thread.sleep(Integer.parseInt(token));
                        continue;
                    }
                }
                catch (InterruptedException e) {
                    return;
                }
                boolean press = !token.startsWith("-");
                boolean tap = !token.startsWith("+") && !token.startsWith("-");
                Key key = Key.ofName(tap ? token : token.substring(1));
                logger.info("Simulating " + (tap ? "" : token.substring(0, 1)) + key);
                keyEventQueue.add(press ? new PressKeyEvent(clock.now(), key) :
                        new ReleaseKeyEvent(clock.now(), key));
                if (tap)
                    keyEventQueue.add(new ReleaseKeyEvent(clock.now(), key));
            }
            logger.info("Finished simulating keys");
        });
        simulationThread.setName("macos-simulated-keys");
        simulationThread.setDaemon(true);
        simulationThread.start();
    }

    /** Returns once the input is released. */
    private void readEvents() {
        Driverkit.DKEvent event = new Driverkit.DKEvent();
        while (Driverkit.INSTANCE.wait_key(event) != 0) {
            event.read();
            Key key = MacosHidUsage.keyFromHidEvent(event.page, event.code,
                    keyboard.activeKeyboardLayout);
            if (key == null) {
                // Dropping it would be swallowing it: a seized device reaches the os only
                // through the sink.
                Driverkit.DKEvent unmapped = new Driverkit.DKEvent();
                unmapped.value = event.value;
                unmapped.page = event.page;
                unmapped.code = event.code;
                unmappedEventQueue.add(unmapped);
                continue;
            }
            if (!(event.isPress() || event.isRelease()))
                continue;
            Instant time = clock.now();
            keyEventQueue.add(event.isPress() ? new PressKeyEvent(time, key) :
                    new ReleaseKeyEvent(time, key));
        }
    }

    /** The same name Windows gives the single instance mutex. */
    private static final String singleInstanceName =
            "e133df8f8434f57e65f4276f6fc761ab356687b3";

    /** A file lock, so that the kernel drops it however the process dies. */
    private boolean acquireSingleInstanceLock() {
        try {
            singleInstanceChannel = FileChannel.open(
                    Path.of(System.getProperty("java.io.tmpdir"), singleInstanceName),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            return singleInstanceChannel.tryLock() != null;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void shutdown() {
        if (shutdown)
            return;
        shutdown = true;
        if (grabbed) {
            Driverkit.INSTANCE.release();
            logger.info("Released the keyboard");
        }
        if (singleInstanceChannel != null) {
            try {
                singleInstanceChannel.close();
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public void killProcess(int exitCode) {
        shutdown();
        Libc.INSTANCE._exit(exitCode);
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
        return MacosKeyboardLayoutFinder.active();
    }

    @Override
    public KeyboardController keyboard() {
        return keyboard;
    }

    @Override
    public MouseController mouse() {
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
    }

    @Override
    public void modeTimedOut() {
    }

}
