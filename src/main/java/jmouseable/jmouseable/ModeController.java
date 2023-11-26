package jmouseable.jmouseable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

public class ModeController implements GridListener {

    private static final Logger logger = LoggerFactory.getLogger(ModeController.class);

    private final ModeMap modeMap;
    private final MouseController mouseController;
    private final MouseState mouseState;
    private final List<ModeListener> listeners;
    private boolean currentModeCursorHidden;
    private Mode currentMode;
    private double timeoutIdleTimer;
    private double hideCursorIdleTimer;
    private boolean justSnappedToGrid;

    public ModeController(ModeMap modeMap, MouseController mouseController, MouseState mouseState,
                          List<ModeListener> listeners) {
        this.modeMap = modeMap;
        this.mouseController = mouseController;
        this.mouseState = mouseState;
        this.listeners = listeners;
        String defaultModeName = Mode.NORMAL_MODE_NAME;
        switchMode(defaultModeName);
    }

    public Mode currentMode() {
        return currentMode;
    }

    public void update(double delta) {
        boolean mouseIdling = !mouseState.moving() && !mouseState.pressing() &&
                              !mouseState.wheeling() && !justSnappedToGrid;
        justSnappedToGrid = false;
        if (!mouseIdling) {
            resetIdleTimers();
            resetCurrentModeCursorHidden();
        }
        else {
            boolean currentModeTimedOut = false;
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
                    mouseController.hideCursor();
                }
            }
            if (currentModeTimedOut) {
                listeners.forEach(ModeListener::modeTimedOut);
                switchMode(currentMode.timeout().nextModeName());
            }
        }
    }

    public void switchMode(String newModeName) {
        Mode newMode = modeMap.get(newModeName);
        currentMode = newMode;
        resetCurrentModeCursorHidden();
        resetIdleTimers();
        mouseController.setMouse(newMode.mouse());
        mouseController.setWheel(newMode.wheel());
        mouseController.setGrid(newMode.gridConfiguration());
        listeners.forEach(listener -> listener.modeChanged(newMode));
    }

    private void resetCurrentModeCursorHidden() {
        if (currentModeCursorHidden) {
            if (!currentMode.hideCursor().enabled() ||
                !currentMode.hideCursor().idleDuration().equals(Duration.ZERO)) {
                mouseController.showCursor();
                currentModeCursorHidden = false;
            }
        }
        else {
            if (currentMode.hideCursor().enabled() &&
                currentMode.hideCursor().idleDuration().equals(Duration.ZERO)) {
                mouseController.hideCursor();
                currentModeCursorHidden = true;
            }
        }
    }

    private void resetIdleTimers() {
        if (currentMode.timeout().enabled())
            timeoutIdleTimer = currentMode.timeout().idleDuration().toNanos() / 1e9d;
        if (currentMode.hideCursor().enabled())
            hideCursorIdleTimer =
                    currentMode.hideCursor().idleDuration().toNanos() / 1e9d;
    }

    @Override
    public void snappedToGrid() {
        this.justSnappedToGrid = true;
    }

}
