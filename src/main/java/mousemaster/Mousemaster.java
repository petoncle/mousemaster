package mousemaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Mousemaster {

    private static final Logger logger = LoggerFactory.getLogger(Mousemaster.class);

    private final Path configurationPath;
    private final Platform platform;
    private final WatchService watchService;
    private Configuration configuration;
    private MouseController mouseController;
    private HintManager hintManager;
    private Remapper remapper;
    private KeyboardManager keyboardManager;
    private IndicatorManager indicatorManager;
    private ModeController modeController;

    public Mousemaster(Path configurationPath, Platform platform) throws IOException {
        this.configurationPath = configurationPath;
        this.platform = platform;
        loadConfiguration();
        watchService = FileSystems.getDefault().newWatchService();
        configurationPath.toAbsolutePath()
                         .getParent()
                         .register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
    }

    public void run() throws InterruptedException {
        long previousNanoTime = System.nanoTime();
        while (true) {
            long currentNanoTime = System.nanoTime();
            long deltaNanos = currentNanoTime - previousNanoTime;
            previousNanoTime = currentNanoTime;
            double delta = deltaNanos / 1e9d;
            updateConfiguration();
            platform.update(delta);
            modeController.update(delta);
            mouseController.update(delta);
            hintManager.update(delta);
            keyboardManager.update(delta);
            indicatorManager.update(delta);
            remapper.update(delta);
            Thread.sleep(10L);
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
                try {
                    loadConfiguration();
                } catch (Exception e) {
                    logger.error(
                            "Unable to load configuration file " + configurationPath, e);
                }
            }
        }
        key.reset();
    }

    private void loadConfiguration() throws IOException {
        boolean reload = configuration != null;
        configuration = ConfigurationParser.parse(configurationPath);
        logger.info((reload ? "Reloaded" : "Loaded") + " configuration file " +
                    configurationPath);
        ScreenManager screenManager = new ScreenManager();
        mouseController = new MouseController(screenManager);
        MouseState mouseState = new MouseState(mouseController);
        GridManager gridManager = new GridManager(screenManager, mouseController);
        hintManager =
                new HintManager(configuration.maxPositionHistorySize(),
                        screenManager, mouseController);
        remapper = new Remapper();
        CommandRunner commandRunner = new CommandRunner(mouseController, gridManager,
                hintManager, remapper);
        Set<Key> mustRemainUnpressedComboPreconditionKeys = new HashSet<>();
        Set<Key> mustRemainPressedComboPreconditionKeys = new HashSet<>();
        for (Mode mode : configuration.modeMap().modes()) {
            for (Combo combo : mode.comboMap().commandsByCombo().keySet()) {
                mustRemainUnpressedComboPreconditionKeys.addAll(combo.precondition()
                                                                     .keyPrecondition()
                                                                     .mustRemainUnpressedKeySet()
                                                                     .stream()
                                                                     .toList());
                mustRemainPressedComboPreconditionKeys.addAll(combo.precondition()
                                                                   .keyPrecondition()
                                                                   .mustRemainPressedKeySets()
                                                                   .stream()
                                                                   .flatMap(
                                                                           Collection::stream)
                                                                   .toList());
            }
        }
        ComboWatcher comboWatcher =
                new ComboWatcher(commandRunner, new ActiveAppFinder(),
                        mustRemainUnpressedComboPreconditionKeys,
                        mustRemainPressedComboPreconditionKeys);
        keyboardManager = new KeyboardManager(comboWatcher, hintManager);
        KeyboardState keyboardState = new KeyboardState(keyboardManager);
        indicatorManager = new IndicatorManager(mouseState, keyboardState);
        modeController =
                new ModeController(configuration.modeMap(), mouseController, mouseState,
                        keyboardState,
                        List.of(comboWatcher, mouseController, indicatorManager,
                                gridManager, hintManager));
        commandRunner.setModeController(modeController);
        hintManager.setModeController(modeController);
        gridManager.setListeners(List.of(modeController));
        hintManager.setPositionHistoryListener(List.of(modeController));
        modeController.switchMode(Mode.IDLE_MODE_NAME);
        platform.reset(mouseController, keyboardManager, configuration.keyboardLayout(),
                configuration.modeMap(),
                List.of(mouseController, gridManager, hintManager, screenManager));
    }

}
