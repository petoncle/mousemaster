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
        MouseMover mouseMover =
                new MouseMover(configuration.modeMap().get(defaultModeName).mouse());
        ModeManager modeManager = new ModeManager(configuration.modeMap(), mouseMover);
        modeManager.changeMode(defaultModeName);
        CommandRunner commandRunner = new CommandRunner(modeManager, mouseMover);
        ComboWatcher comboWatcher = new ComboWatcher(modeManager, commandRunner,
                configuration.defaultComboBreakingTimeout());
        Ticker ticker = new Ticker(modeManager, mouseMover);
        new WindowsHook(mouseMover, comboWatcher, ticker).installHooks();
    }

}
