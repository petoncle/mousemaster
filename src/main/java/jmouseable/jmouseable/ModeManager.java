package jmouseable.jmouseable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModeManager {

    private static final Logger logger = LoggerFactory.getLogger(ModeManager.class);

    private final ModeMap modeMap;
    private final MouseMover mouseMover;
    private ComboWatcher comboWatcher;
    private Mode currentMode;
    private double currentModeRemainingDuration;

    public ModeManager(ModeMap modeMap, MouseMover mouseMover) {
        this.modeMap = modeMap;
        this.mouseMover = mouseMover;
    }

    public void setComboWatcher(ComboWatcher comboWatcher) {
        this.comboWatcher = comboWatcher;
    }

    public Mode currentMode() {
        return currentMode;
    }

    public void update(double delta) {
        if (currentMode.timeout() != null) {
            if (mouseMover.moving() || mouseMover.pressing() || mouseMover.wheeling()) {
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
        mouseMover.changeMouse(newMode.mouse());
        mouseMover.changeWheel(newMode.wheel());
        if (newMode.indicator().enabled())
            WindowsIndicator.show(newMode.indicator().hexColor());
        else
            WindowsIndicator.hide();
    }

    private void resetCurrentModeRemainingDuration() {
        currentModeRemainingDuration = currentMode.timeout().duration().toNanos() / 1e9d;
    }

}
