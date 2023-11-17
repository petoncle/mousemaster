package jmouseable.jmouseable;

import java.io.IOException;
import java.nio.file.Paths;

public class JmouseableApplication {

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
        new WindowsHook(mouseManager, keyboardManager, ticker).installHooks();
    }

}
