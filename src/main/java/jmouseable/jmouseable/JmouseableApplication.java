package jmouseable.jmouseable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;

public class JmouseableApplication {

    private static final Logger logger = LoggerFactory.getLogger(JmouseableApplication.class);

    public static void main(String[] args) throws InterruptedException, IOException {
        Configuration configuration =
                ConfigurationParser.parse(Paths.get("jmouseable.properties"));
        String defaultModeName = Mode.NORMAL_MODE_NAME;
        Mode currentMode = configuration.modeMap().get(defaultModeName);
        MouseManager mouseManager = new MouseManager(currentMode.mouse(), currentMode.wheel());
        ModeManager modeManager = new ModeManager(configuration.modeMap(), mouseManager);
        modeManager.changeMode(defaultModeName);
        CommandRunner commandRunner = new CommandRunner(modeManager, mouseManager);
        ComboWatcher comboWatcher = new ComboWatcher(modeManager, commandRunner);
        modeManager.setComboWatcher(comboWatcher);
        KeyboardManager keyboardManager = new KeyboardManager(comboWatcher);
        IndicatorManager indicatorManager =
                new IndicatorManager(modeManager, mouseManager, keyboardManager);
        Ticker ticker = new Ticker(modeManager, mouseManager, keyboardManager,
                indicatorManager);
        if (args.length == 1 && args[0].equals("-graalvm-agent-run")) {
            logger.info("-graalvm-agent-run flag found, exiting in 5s");
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                System.exit(0);
            }).start();
        }
        new WindowsHook(mouseManager, keyboardManager, ticker).installHooks();
    }

}
