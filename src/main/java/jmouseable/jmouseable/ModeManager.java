package jmouseable.jmouseable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModeManager {

    private static final Logger logger = LoggerFactory.getLogger(ModeManager.class);

    private final ModeMap modeMap;
    private final MouseManager mouseManager;
    private ComboWatcher comboWatcher;
    private Mode currentMode;
    private double currentModeRemainingDuration;

    public ModeManager(ModeMap modeMap, MouseManager mouseManager) {
        this.modeMap = modeMap;
        this.mouseManager = mouseManager;
    }

    public void setComboWatcher(ComboWatcher comboWatcher) {
        this.comboWatcher = comboWatcher;
    }

    public Mode currentMode() {
        return currentMode;
    }

    public void update(double delta) {
        if (currentMode.timeout() != null) {
            if (mouseManager.moving() || mouseManager.pressing() || mouseManager.wheeling()) {
                resetCurrentModeRemainingDuration();
            }
            else {
                currentModeRemainingDuration -= delta;
                if (currentModeRemainingDuration <= 0) {
                    logger.debug("Current " + currentMode.name() +
                                 " has timed out, changing to " +
                                 currentMode.timeout().nextModeName());
                    comboWatcher.interrupt();
                    changeMode(currentMode.timeout().nextModeName());
                }
            }
        }
    }

    public void changeMode(String newModeName) {
        Mode newMode = modeMap.get(newModeName);
        currentMode = newMode;
        if (newMode.timeout() != null)
            resetCurrentModeRemainingDuration();
        mouseManager.changeMouse(newMode.mouse());
        mouseManager.changeWheel(newMode.wheel());
        mouseManager.changeAttach(newMode.attach());
    }

    private void resetCurrentModeRemainingDuration() {
        currentModeRemainingDuration = currentMode.timeout().duration().toNanos() / 1e9d;
    }

}
