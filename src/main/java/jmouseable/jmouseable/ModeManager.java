package jmouseable.jmouseable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class ModeManager {

    private static final Logger logger = LoggerFactory.getLogger(ModeManager.class);

    private final ModeMap modeMap;
    private final MouseManager mouseManager;
    private boolean currentModeTimedOut;
    private boolean currentModeCursorHidden;
    private Mode currentMode;
    private double timeoutIdleTimer;
    private double hideCursorIdleTimer;

    public ModeManager(ModeMap modeMap, MouseManager mouseManager) {
        this.modeMap = modeMap;
        this.mouseManager = mouseManager;
        String defaultModeName = Mode.NORMAL_MODE_NAME;
        switchMode(defaultModeName);
    }

    public Mode currentMode() {
        return currentMode;
    }

    public boolean pollCurrentModeTimedOut() {
        try {
            return currentModeTimedOut;
        } finally {
            currentModeTimedOut = false;
        }
    }

    public void update(double delta) {
        boolean mouseIdling = !mouseManager.moving() && !mouseManager.pressing() &&
                              !mouseManager.wheeling();
        if (!mouseIdling) {
            resetIdleTimers();
            resetCurrentModeCursorHidden();
        }
        else {
            if (currentMode.timeout().enabled()) {
                timeoutIdleTimer -= delta;
                if (timeoutIdleTimer <= 0) {
                    logger.debug("Current " + currentMode.name() +
                                 " has timed out, switching to " +
                                 currentMode.timeout().nextModeName());
                    currentModeTimedOut = true;
                }
            }
            if (currentMode.hideCursor().enabled() && !currentModeCursorHidden) {
                hideCursorIdleTimer -= delta;
                if (hideCursorIdleTimer <= 0) {
                    logger.debug("Hide cursor timer for " + currentMode.name() +
                                 " has elapsed");
                    currentModeCursorHidden = true;
                    mouseManager.hideCursor();
                }
            }
            if (currentModeTimedOut)
                switchMode(currentMode.timeout().nextModeName());
        }
    }

    public void switchMode(String newModeName) {
        Mode newMode = modeMap.get(newModeName);
        currentMode = newMode;
        currentModeTimedOut = false;
        resetCurrentModeCursorHidden();
        resetIdleTimers();
        mouseManager.setMouse(newMode.mouse());
        mouseManager.setWheel(newMode.wheel());
        mouseManager.setAttach(newMode.attach());
    }

    private void resetCurrentModeCursorHidden() {
        if (currentModeCursorHidden) {
            if (!currentMode.hideCursor().enabled() ||
                !currentMode.hideCursor().idleDuration().equals(Duration.ZERO)) {
                mouseManager.showCursor();
                currentModeCursorHidden = false;
            }
        }
        else {
            if (currentMode.hideCursor().enabled() &&
                currentMode.hideCursor().idleDuration().equals(Duration.ZERO)) {
                mouseManager.hideCursor();
                currentModeCursorHidden = true;
            }
        }
    }

    public void attached() {
        resetIdleTimers();
    }

    private void resetIdleTimers() {
        if (currentMode.timeout().enabled())
            timeoutIdleTimer = currentMode.timeout().idleDuration().toNanos() / 1e9d;
        if (currentMode.hideCursor().enabled())
            hideCursorIdleTimer =
                    currentMode.hideCursor().idleDuration().toNanos() / 1e9d;
    }

}
