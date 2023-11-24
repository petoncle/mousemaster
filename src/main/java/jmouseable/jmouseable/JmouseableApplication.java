package jmouseable.jmouseable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class JmouseableApplication {

    private static final Logger logger =
            (Logger) LoggerFactory.getLogger(JmouseableApplication.class);

    public static void main(String[] args) throws InterruptedException, IOException {
        Stream.of(args)
              .filter(arg -> arg.startsWith("--log-level="))
              .map(arg -> arg.split("=")[1])
              .findFirst()
              .ifPresent(JmouseableApplication::setLogLevel);
        String configurationPath = Stream.of(args)
                                         .filter(arg -> arg.startsWith(
                                                 "--configuration-file="))
                                         .map(arg -> arg.split("=")[1])
                                         .findFirst()
                                         .orElse("jmouseable.properties");
        Configuration configuration =
                ConfigurationParser.parse(Paths.get(configurationPath));
        String defaultModeName = Mode.NORMAL_MODE_NAME;
        Mode currentMode = configuration.modeMap().get(defaultModeName);
        MouseManager mouseManager =
                new MouseManager(currentMode.mouse(), currentMode.wheel(),
                        currentMode.attach());
        ModeManager modeManager = new ModeManager(configuration.modeMap(), mouseManager);
        modeManager.switchMode(defaultModeName);
        CommandRunner commandRunner = new CommandRunner(modeManager, mouseManager);
        ComboWatcher comboWatcher = new ComboWatcher(modeManager, commandRunner);
        modeManager.setComboWatcher(comboWatcher);
        KeyboardManager keyboardManager = new KeyboardManager(comboWatcher);
        IndicatorManager indicatorManager =
                new IndicatorManager(modeManager, mouseManager, keyboardManager);
        Ticker ticker = new Ticker(modeManager, mouseManager, keyboardManager,
                indicatorManager);
        if (Stream.of(args).anyMatch(Predicate.isEqual(("--graalvm-agent-run")))) {
            logger.info("--graalvm-agent-run flag found, exiting in 5s");
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

    private static void setLogLevel(String level) {
       Logger logger = (Logger) LoggerFactory.getLogger("jmouseable");
       logger.setLevel(Level.valueOf(level));
    }

}
