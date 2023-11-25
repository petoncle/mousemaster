package jmouseable.jmouseable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModeManager {

    private static final Logger logger = LoggerFactory.getLogger(ModeManager.class);

    private final ModeMap modeMap;
    private final MouseManager mouseManager;
    private boolean currentModeTimedOut;
    private Mode currentMode;
    private double currentModeRemainingDuration;

    public ModeManager(ModeMap modeMap, MouseManager mouseManager) {
        this.modeMap = modeMap;
        this.mouseManager = mouseManager;
        String defaultModeName = Mode.NORMAL_MODE_NAME;
        switchMode(defaultModeName);
    }

    public Mode currentMode() {
        return currentMode;
    }

    public boolean poolCurrentModeTimedOut() {
        try {
            return currentModeTimedOut;
        } finally {
            currentModeTimedOut = false;
        }
    }

    public void update(double delta) {
        if (currentMode.timeout().idleDuration() != null) {
            if (mouseManager.moving() || mouseManager.pressing() || mouseManager.wheeling()) {
                resetCurrentModeRemainingDuration();
            }
            else {
                currentModeRemainingDuration -= delta;
                if (currentModeRemainingDuration <= 0) {
                    logger.debug("Current " + currentMode.name() +
                                 " has timed out, switching to " +
                                 currentMode.timeout().nextModeName());
                    currentModeTimedOut = true;
                    switchMode(currentMode.timeout().nextModeName());
                }
            }
        }
    }

    public void switchMode(String newModeName) {
        Mode newMode = modeMap.get(newModeName);
        currentMode = newMode;
        if (newMode.timeout().idleDuration() != null)
            resetCurrentModeRemainingDuration();
        mouseManager.setMouse(newMode.mouse());
        mouseManager.setWheel(newMode.wheel());
        mouseManager.setAttach(newMode.attach());
    }

    public void attached() {
        if (currentMode.timeout().idleDuration() != null)
            resetCurrentModeRemainingDuration();
    }

    private void resetCurrentModeRemainingDuration() {
        currentModeRemainingDuration = currentMode.timeout().idleDuration().toNanos() / 1e9d;
    }

}
