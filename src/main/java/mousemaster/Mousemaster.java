package mousemaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Mousemaster {

    private static final Logger logger = LoggerFactory.getLogger(Mousemaster.class);

    private final Path configurationPath;
    private final Platform platform;
    private final WatchService watchService;
    private Configuration configuration;
    private MouseManager mouseManager;
    private CommandRunner commandRunner;
    private MacroPlayer macroPlayer;
    private KeyboardManager keyboardManager;
    private IndicatorManager indicatorManager;
    private ZoomManager zoomManager;
    private ModeController modeController;
    private List<String> configurationProperties;
    private KeyboardLayout activeKeyboardLayout;
    private KeyboardLayout forcedActiveKeyboardLayout;
    private final boolean preWarmHints;
    private boolean logToFileEnabledByConfiguration;

    public Mousemaster(Path configurationPath, Platform platform, boolean preWarmHints)
            throws IOException {
        this.configurationPath = configurationPath;
        this.platform = platform;
        this.preWarmHints = preWarmHints;
        this.activeKeyboardLayout = platform.activeKeyboardLayout();
        QtManager.initialize();
        loadConfiguration(true);
        watchService = FileSystems.getDefault().newWatchService();
        configurationPath.toAbsolutePath()
                         .getParent()
                         .register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
    }

    public void run() throws InterruptedException {
        long previousIterationBeginTime = System.nanoTime();
        while (true) {
            long iterationBeginTime = System.nanoTime();
            long deltaNanos = iterationBeginTime - previousIterationBeginTime;
            previousIterationBeginTime = iterationBeginTime;
            double delta = deltaNanos / 1e9d;
            long timeBeforeOp = iterationBeginTime;
            long pumpEventsNanos = 0;
            updateConfiguration();
            long timeAfterOp = System.nanoTime();
            long updateConfigurationDuration = (long) ((timeAfterOp - timeBeforeOp) / 1e6);
            platform.pumpEvents();
            timeBeforeOp = System.nanoTime();
            pumpEventsNanos += timeBeforeOp - timeAfterOp;
            updateActiveKeyboardLayout(delta);
            timeAfterOp = System.nanoTime();
            long updateActiveKeyboardLayoutDuration = (long) ((timeAfterOp - timeBeforeOp) / 1e6);
            platform.pumpEvents();
            timeBeforeOp = System.nanoTime();
            pumpEventsNanos += timeBeforeOp - timeAfterOp;
            QtManager.processEvents();
            timeAfterOp = System.nanoTime();
            long qtDuration = (long) ((timeAfterOp - timeBeforeOp) / 1e6);
            timeBeforeOp = timeAfterOp;
            platform.update(delta);
            timeAfterOp = System.nanoTime();
            long platformDuration = (long) ((timeAfterOp - timeBeforeOp) / 1e6);
            platform.pumpEvents();
            timeBeforeOp = System.nanoTime();
            pumpEventsNanos += timeBeforeOp - timeAfterOp;
            modeController.update(delta);
            timeAfterOp = System.nanoTime();
            long modeControllerDuration = (long) ((timeAfterOp - timeBeforeOp) / 1e6);
            platform.pumpEvents();
            timeBeforeOp = System.nanoTime();
            pumpEventsNanos += timeBeforeOp - timeAfterOp;
            mouseManager.update(delta);
            timeAfterOp = System.nanoTime();
            long mouseControllerDuration = (long) ((timeAfterOp - timeBeforeOp) / 1e6);
            platform.pumpEvents();
            timeBeforeOp = System.nanoTime();
            pumpEventsNanos += timeBeforeOp - timeAfterOp;
            keyboardManager.update(delta);
            timeAfterOp = System.nanoTime();
            long keyboardManagerDuration = (long) ((timeAfterOp - timeBeforeOp) / 1e6);
            platform.pumpEvents();
            timeBeforeOp = System.nanoTime();
            pumpEventsNanos += timeBeforeOp - timeAfterOp;
            modeController.updateMouseAndKeyboardKeys();
            indicatorManager.update(delta);
            timeAfterOp = System.nanoTime();
            long indicatorManagerDuration = (long) ((timeAfterOp - timeBeforeOp) / 1e6);
            platform.pumpEvents();
            timeBeforeOp = System.nanoTime();
            pumpEventsNanos += timeBeforeOp - timeAfterOp;
            zoomManager.update(delta);
            timeAfterOp = System.nanoTime();
            long zoomManagerDuration = (long) ((timeAfterOp - timeBeforeOp) / 1e6);
            platform.pumpEvents();
            timeBeforeOp = System.nanoTime();
            pumpEventsNanos += timeBeforeOp - timeAfterOp;
            macroPlayer.update(delta);
            timeAfterOp = System.nanoTime();
            long macroPlayerDuration = (long) ((timeAfterOp - timeBeforeOp) / 1e6);
            long iterationEndTime = System.nanoTime();
            long iterationDuration =
                    (long) ((iterationEndTime - iterationBeginTime) / 1e6);
            if (iterationDuration > 10L && logger.isTraceEnabled()) {
                logger.trace("Long iteration duration: " + iterationDuration + "ms, " +
                             "updateConfigurationDuration = " + updateConfigurationDuration + "ms, " +
                             "updateActiveKeyboardLayoutDuration = " + updateActiveKeyboardLayoutDuration + "ms, " +
                             "pumpEventsDuration = " + pumpEventsNanos / 1_000_000 + "ms, " +
                             "qtDuration = " + qtDuration + "ms, " +
                             "platformDuration = " + platformDuration + "ms, " +
                             "modeControllerDuration = " + modeControllerDuration + "ms, " +
                             "mouseControllerDuration = " + mouseControllerDuration + "ms, " +
                             "keyboardManagerDuration = " + keyboardManagerDuration + "ms, " +
                             "indicatorManagerDuration = " + indicatorManagerDuration + "ms, " +
                             "zoomManagerDuration = " + zoomManagerDuration + "ms, " +
                             "macroPlayerDuration = " + macroPlayerDuration + "ms");
            }
            platform.sleep();
        }
    }

    private void updateActiveKeyboardLayout(double delta) {
        if (forcedActiveKeyboardLayout != null)
            return;
        KeyboardLayout newActiveKeyboardLayout = platform.activeKeyboardLayout();
        if (!newActiveKeyboardLayout.equals(activeKeyboardLayout)) {
            activeKeyboardLayout = newActiveKeyboardLayout;
            tryLoadConfiguration(false);
        }
    }

    private void updateConfiguration() {
        WatchKey key = watchService.poll();
        if (key == null)
            return;
        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            Path path = (Path) event.context();
            if (!path.getFileName()
                     .toString()
                     .equals(configurationPath.getFileName().toString()))
                continue;
            if (kind.equals(StandardWatchEventKinds.ENTRY_DELETE))
                logger.info("Configuration file " + configurationPath + " was deleted");
            else {
                logger.info("Configuration file " + configurationPath + " has changed");
                tryLoadConfiguration(true);
            }
        }
        key.reset();
    }

    private void tryLoadConfiguration(boolean reReadFile) {
        try {
            loadConfiguration(reReadFile);
        } catch (Exception e) {
            logger.error(
                    "Unable to load configuration file " + configurationPath, e);
        }
    }

    private void loadConfiguration(boolean readFile) throws IOException {
        boolean reload = configuration != null;
        if (readFile) {
            try (BufferedReader reader = Files.newBufferedReader(configurationPath,
                    StandardCharsets.UTF_8)) {
                configurationProperties = PropertiesReader.readPropertiesFile(reader);
            }
        }
        configuration =
                ConfigurationParser.parse(configurationProperties, activeKeyboardLayout);
        // User can override the layout. When active layout is dvorak, Windows HKL only
        // gives the language identifier, which is 0409. But it is missing the other part
        // of the layout identifier (00010409).
        forcedActiveKeyboardLayout = configuration.forcedActiveKeyboardLayout();
        if (forcedActiveKeyboardLayout != null)
            activeKeyboardLayout = forcedActiveKeyboardLayout;
        if (configuration.logLevel() != null)
            MousemasterApplication.setLogLevel(configuration.logLevel());
        MousemasterApplication.setModeColumnWidth(
                configuration.modeMap().modes().stream()
                             .mapToInt(mode -> mode.name().length())
                             .max().orElse(0));
        // Only what a configuration turned on is turned back off: --log-to-file asked for the
        // file before there was a configuration, and no configuration mentioning nothing undoes it.
        if (configuration.logToFile()) {
            MousemasterApplication.enableLogToFile();
            logToFileEnabledByConfiguration = true;
        }
        else if (logToFileEnabledByConfiguration) {
            MousemasterApplication.disableLogToFile();
            logToFileEnabledByConfiguration = false;
        }
        if (configuration.hideConsole())
            platform.console().hide();
        else
            platform.console().show();
        logger.info((reload ? "Reloaded" : "Loaded") + " configuration " +
                    (readFile ? "file " + configurationPath + " " : "") +
                    "with active keyboard layout " + activeKeyboardLayout);
        ScreenManager screenManager = new ScreenManager(platform::screens);
        mouseManager = new MouseManager(screenManager, platform.mouse());
        MouseState mouseState = new MouseState(mouseManager);
        GridManager gridManager = new GridManager(screenManager, mouseManager, platform.overlay());
        HintManager hintManager =
                new HintManager(configuration.positionHistoryConfigurationByName(),
                        screenManager, mouseManager, platform.overlay(),
                        platform.uiAutomation(), platform.activeAppFinder(),
                        configuration.logRedactKeys());
        commandRunner = new CommandRunner(mouseManager, gridManager, hintManager);
        Set<Key> unpressedComboPreconditionKeys = new HashSet<>();
        Set<Key> pressedComboPreconditionKeys = new HashSet<>();
        for (Mode mode : configuration.modeMap().modes()) {
            for (Combo combo : mode.comboMap().commandsByCombo().keySet()) {
                unpressedComboPreconditionKeys.addAll(combo.precondition()
                                                           .keyPrecondition()
                                                           .unpressedKeySet()
                                                           .stream()
                                                           .toList());
                pressedComboPreconditionKeys.addAll(combo.precondition()
                                                         .keyPrecondition()
                                                         .pressedKeyPrecondition()
                                                         .allKeys());
            }
        }
        ComboWatcher comboWatcher =
                new ComboWatcher(commandRunner, hintManager, platform.activeAppFinder(),
                        screenManager, platform.clock(),
                        unpressedComboPreconditionKeys,
                        pressedComboPreconditionKeys, configuration.logRedactKeys(),
                        configuration.modeMap(), configuration.initiallySetVariables(),
                        configuration.virtualKeys(), configuration.initiallyPressedVirtualKeys());
        KeyRegurgitator keyRegurgitator = new KeyRegurgitator(platform.keyboard(),
                configuration.logRedactKeys());
        keyboardManager = new KeyboardManager(comboWatcher, hintManager, keyRegurgitator);
        macroPlayer = new MacroPlayer(platform.clock(), comboWatcher, keyboardManager,
                platform.keyboard(), configuration.logRedactKeys());
        keyboardManager.setMacroPlayer(macroPlayer);
        KeyboardState keyboardState = new KeyboardState(keyboardManager);
        indicatorManager = new IndicatorManager(platform.overlay());
        zoomManager = new ZoomManager(screenManager, hintManager, platform.overlay());
        // ComboWatcher is the sole broadcaster to ModeListeners: it broadcasts
        // on mode switch (delegated from ModeController) and on mode mutation.
        // ZoomManager must be notified after HintManager because it calls
        // lastSelectedHintPoint() which is updated by HintManager#modeChanged.
        comboWatcher.setModeListeners(
                List.of(platform, mouseManager, indicatorManager, gridManager,
                        hintManager, zoomManager));
        modeController =
                new ModeController(configuration.modeMap(), mouseManager, mouseState,
                        keyboardState,
                        hintManager,
                        comboWatcher);
        commandRunner.setModeController(modeController);
        commandRunner.setMacroPlayer(macroPlayer);
        hintManager.setModeController(modeController);
        modeController.switchMode(Mode.IDLE_MODE_NAME);
        platform.reset(mouseManager, keyboardManager, keyRegurgitator,
                configuration.logRedactKeys(),
                configuration.logLastKeyEventsOnExit(),
                configuration.modeMap(),
                zoomManager,
                indicatorManager,
                List.of(mouseManager, gridManager, hintManager, screenManager,
                        zoomManager), activeKeyboardLayout);
        if (preWarmHints)
            hintManager.preWarmHintMeshes(configuration.modeMap());
    }

}
