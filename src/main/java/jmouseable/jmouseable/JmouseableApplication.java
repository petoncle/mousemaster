package jmouseable.jmouseable;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JmouseableApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(JmouseableApplication.class, args);
    }

    private final ConfigurationParser configurationParser;

    public JmouseableApplication(ConfigurationParser configurationParser) {
        this.configurationParser = configurationParser;
    }

    @Override
    public void run(String... args) throws InterruptedException {
        Configuration configuration = configurationParser.parse();
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
        new WindowsHook(mouseManager, keyboardManager, ticker).installHooks();
    }

}
