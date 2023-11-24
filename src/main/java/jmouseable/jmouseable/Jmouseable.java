package jmouseable.jmouseable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;

public class Jmouseable {

    private static final Logger logger = LoggerFactory.getLogger(Jmouseable.class);

    private final Path configurationPath;
    private final OsManager osManager;
    private final WatchService watchService;
    private Configuration configuration;
    private MouseManager mouseManager;
    private KeyboardManager keyboardManager;
    private ModeManager modeManager;
    private IndicatorManager indicatorManager;

    public Jmouseable(Path configurationPath, OsManager osManager) throws IOException {
        this.configurationPath = configurationPath;
        this.osManager = osManager;
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
            osManager.update(delta);
            modeManager.update(delta);
            mouseManager.update(delta);
            keyboardManager.update(delta);
            indicatorManager.update(delta);
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
        mouseManager = new MouseManager();
        modeManager = new ModeManager(configuration.modeMap(), mouseManager);
        CommandRunner commandRunner = new CommandRunner(modeManager, mouseManager);
        ComboWatcher comboWatcher = new ComboWatcher(modeManager, commandRunner);
        keyboardManager = new KeyboardManager(comboWatcher);
        indicatorManager =
                new IndicatorManager(modeManager, mouseManager, keyboardManager);
        osManager.reset(mouseManager, keyboardManager, configuration.keyboardLayout(),
                configuration.modeMap());
    }

}
