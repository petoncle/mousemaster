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
    private MouseController mouseController;
    private CommandRunner commandRunner;
    private MacroPlayer macroPlayer;
    private KeyboardManager keyboardManager;
    private IndicatorManager indicatorManager;
    private ZoomManager zoomManager;
    private ModeController modeController;
    private List<String> configurationProperties;
    private KeyboardLayout activeKeyboardLayout;
    private KeyboardLayout forcedActiveKeyboardLayout;

    public Mousemaster(Path configurationPath, Platform platform) throws IOException {
        this.configurationPath = configurationPath;
        this.platform = platform;
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
            updateConfiguration();
            long timeAfterOp = System.nanoTime();
            long updateConfigurationDuration = (long) ((timeAfterOp - timeBeforeOp) / 1e6);
            platform.windowsMessagePump();
            timeBeforeOp = timeAfterOp;
            updateActiveKeyboardLayout(delta);
            timeAfterOp = System.nanoTime();
            long updateActiveKeyboardLayoutDuration = (long) ((timeAfterOp - timeBeforeOp) / 1e6);
            platform.windowsMessagePump();
            timeBeforeOp = timeAfterOp;
            QtManager.processEvents();
            platform.update(delta);
            timeAfterOp = System.nanoTime();
            long platformDuration = (long) ((timeAfterOp - timeBeforeOp) / 1e6);
            platform.windowsMessagePump();
            timeBeforeOp = timeAfterOp;
            modeController.update(delta);
            timeAfterOp = System.nanoTime();
            long modeControllerDuration = (long) ((timeAfterOp - timeBeforeOp) / 1e6);
            platform.windowsMessagePump();
            timeBeforeOp = timeAfterOp;
            mouseController.update(delta);
            timeAfterOp = System.nanoTime();
            long mouseControllerDuration = (long) ((timeAfterOp - timeBeforeOp) / 1e6);
            platform.windowsMessagePump();
            timeBeforeOp = timeAfterOp;
            keyboardManager.update(delta);
            timeAfterOp = System.nanoTime();
            long keyboardManagerDuration = (long) ((timeAfterOp - timeBeforeOp) / 1e6);
            platform.windowsMessagePump();
            timeBeforeOp = timeAfterOp;
            indicatorManager.update(delta);
            timeAfterOp = System.nanoTime();
            long indicatorManagerDuration = (long) ((timeAfterOp - timeBeforeOp) / 1e6);
            platform.windowsMessagePump();
            timeBeforeOp = timeAfterOp;
            zoomManager.update(delta);
            timeAfterOp = System.nanoTime();
            platform.windowsMessagePump();
            timeBeforeOp = timeAfterOp;
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
                             "platformDuration = " + platformDuration + "ms, " +
                             "modeControllerDuration = " + modeControllerDuration + "ms, " +
                             "mouseControllerDuration = " + mouseControllerDuration + "ms, " +
                             "keyboardManagerDuration = " + keyboardManagerDuration + "ms, " +
                             "indicatorManagerDuration = " + indicatorManagerDuration + "ms, " +
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
        if (configuration.logToFile())
            MousemasterApplication.enableLogToFile();
        else
            MousemasterApplication.disableLogToFile();
        if (configuration.hideConsole())
            MousemasterApplication.hideConsole();
        else
            MousemasterApplication.showConsole();
        logger.info((reload ? "Reloaded" : "Loaded") + " configuration " +
                    (readFile ? "file " + configurationPath + " " : "") +
                    "with active keyboard layout " + activeKeyboardLayout);
        ScreenManager screenManager = new ScreenManager();
        mouseController = new MouseController(screenManager);
        MouseState mouseState = new MouseState(mouseController);
        GridManager gridManager = new GridManager(screenManager, mouseController);
        HintManager hintManager = new HintManager(configuration.maxPositionHistorySize(),
                screenManager, mouseController);
        commandRunner = new CommandRunner(mouseController, gridManager, hintManager);
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
                new ComboWatcher(commandRunner, hintManager, new ActiveAppFinder(),
                        platform.clock(),
                        unpressedComboPreconditionKeys,
                        pressedComboPreconditionKeys, configuration.logRedactKeys());
        keyboardManager = new KeyboardManager(comboWatcher, hintManager,
                platform.keyRegurgitator());
        macroPlayer = new MacroPlayer(platform.clock(), comboWatcher, keyboardManager);
        keyboardManager.setMacroPlayer(macroPlayer);
        KeyboardState keyboardState = new KeyboardState(keyboardManager);
        indicatorManager = new IndicatorManager(mouseState, keyboardState);
        zoomManager = new ZoomManager(screenManager, hintManager);
        // ComboWatcher is the sole broadcaster to ModeListeners: it broadcasts
        // on mode switch (delegated from ModeController) and on mode mutation.
        // ZoomManager must be notified after HintManager because it calls
        // lastSelectedHintPoint() which is updated by HintManager#modeChanged.
        comboWatcher.setModeListeners(
                List.of(platform, mouseController, indicatorManager, gridManager,
                        hintManager, zoomManager));
        modeController =
                new ModeController(configuration.modeMap(), mouseController, mouseState,
                        keyboardState,
                        hintManager,
                        comboWatcher);
        commandRunner.setModeController(modeController);
        commandRunner.setMacroPlayer(macroPlayer);
        hintManager.setModeController(modeController);
        comboWatcher.setComboListeners(List.of(modeController));
        modeController.switchMode(Mode.IDLE_MODE_NAME);
        platform.reset(mouseController, keyboardManager,
                configuration.modeMap(),
                List.of(mouseController, gridManager, hintManager, screenManager,
                        zoomManager), activeKeyboardLayout);
    }

}
