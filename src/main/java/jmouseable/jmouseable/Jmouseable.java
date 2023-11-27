package jmouseable.jmouseable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public class Jmouseable {

    private static final Logger logger = LoggerFactory.getLogger(Jmouseable.class);

    private final Path configurationPath;
    private final Platform platform;
    private final WatchService watchService;
    private Configuration configuration;
    private MouseController mouseController;
    private KeyboardManager keyboardManager;
    private ModeController modeController;

    public Jmouseable(Path configurationPath, Platform platform) throws IOException {
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
            keyboardManager.update(delta);
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
        mouseController = new MouseController();
        MouseState mouseState = new MouseState(mouseController);
        MonitorManager monitorManager = new MonitorManager();
        GridManager gridManager = new GridManager(monitorManager, mouseController);
        CommandRunner commandRunner = new CommandRunner(mouseController, gridManager);
        ComboWatcher comboWatcher = new ComboWatcher(commandRunner);
        keyboardManager = new KeyboardManager(comboWatcher);
        KeyboardState keyboardState = new KeyboardState(keyboardManager);
        IndicatorManager indicatorManager =
                new IndicatorManager(mouseState, keyboardState);
        modeController = new ModeController(configuration.modeMap(), mouseController, mouseState,
                List.of(comboWatcher, indicatorManager, gridManager));
        commandRunner.setModeController(modeController);
        mouseController.setGridListeners(List.of(modeController)); // TODO REMOVE
        platform.reset(mouseController, keyboardManager, configuration.keyboardLayout(),
                configuration.modeMap(), List.of(gridManager, monitorManager));
    }

}
