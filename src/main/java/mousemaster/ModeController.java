package mousemaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

public class ModeController implements ComboListener {

    private static final Logger logger = LoggerFactory.getLogger(ModeController.class);

    private final ModeMap modeMap;
    private final MouseController mouseController;
    private final MouseState mouseState;
    private final KeyboardState keyboardState;
    private final HintManager hintManager;
    private final ComboWatcher comboWatcher;
    private boolean currentModeCursorHidden;
    private Mode currentMode;
    private final Deque<Mode> modeHistoryStack = new ArrayDeque<>();
    private double modeTimeoutTimer;
    private double hideCursorIdleTimer;
    private boolean justCompletedCombo;
    private boolean previousTimeoutEnabled;
    private boolean previousHideCursorEnabled;

    public ModeController(ModeMap modeMap, MouseController mouseController,
                          MouseState mouseState, KeyboardState keyboardState,
                          HintManager hintManager,
                          ComboWatcher comboWatcher) {
        this.modeMap = modeMap;
        this.mouseController = mouseController;
        this.mouseState = mouseState;
        this.keyboardState = keyboardState;
        this.hintManager = hintManager;
        this.comboWatcher = comboWatcher;
    }

    public void update(double delta) {
        hintManager.completePendingUiHintQuery();
        Mode mutatedMode = comboWatcher.getMutatedMode();
        // The flag catches briefly-pressed unhandled keys that are released before this poll.
        // The poll catches held keys that become unhandled due to a mode change (no key event to set the flag).
        boolean hadUnhandledKeyPress =
                keyboardState.pollHadUnhandledKeyPressInCurrentMode();
        if (keyboardState.pressingUnhandledKeyInCurrentMode() ||
            hadUnhandledKeyPress) {
            if (mutatedMode.modeAfterUnhandledKeyPress() != null) {
                logger.debug("Unhandled key press, switching to " +
                             mutatedMode.modeAfterUnhandledKeyPress());
                switchMode(mutatedMode.modeAfterUnhandledKeyPress());
                return;
            }
        }
        boolean idling = !mouseState.moving() &&
                         !mouseState.leftPressing() && !mouseState.middlePressing() && !mouseState.rightPressing() &&
                         !mouseState.wheeling() && !justCompletedCombo;
        boolean mustResetHideCursorTimeout = !idling || hintManager.showingHintMesh();
        justCompletedCombo = false;
        boolean hideCursorEnabled = mutatedMode.hideCursor().enabled();
        if (!previousHideCursorEnabled && hideCursorEnabled)
            resetHideCursorTimer(mutatedMode);
        else if (previousHideCursorEnabled && !hideCursorEnabled)
            resetCurrentModeCursorHidden(mutatedMode);
        previousHideCursorEnabled = hideCursorEnabled;
        if (mustResetHideCursorTimeout) {
            resetHideCursorTimer(mutatedMode);
            resetCurrentModeCursorHidden(mutatedMode);
        }
        else {
            if (hideCursorEnabled && !currentModeCursorHidden) {
                hideCursorIdleTimer -= delta;
                if (hideCursorIdleTimer <= 0) {
                    logger.debug("Hide cursor timer for " + currentMode.name() +
                                 " has elapsed");
                    currentModeCursorHidden = true;
                    mouseController.hideCursor();
                }
            }
        }
        boolean timeoutEnabled = mutatedMode.timeout().enabled();
        if (!previousTimeoutEnabled && timeoutEnabled)
            modeTimeoutTimer = mutatedMode.timeout().duration().toNanos() / 1e9d;
        previousTimeoutEnabled = timeoutEnabled;
        boolean mustResetModeTimeout = mutatedMode.timeout().onlyIfIdle() && !idling ||
                                       hintManager.showingHintMesh();
        if (mustResetModeTimeout) {
            resetModeTimeoutTimer(mutatedMode);
        }
        else {
            if (timeoutEnabled) {
                modeTimeoutTimer -= delta;
                if (modeTimeoutTimer <= 0) {
                    logger.debug("Current " + mutatedMode.name() +
                                 " has timed out, switching to " +
                                 mutatedMode.timeout().modeName());
                    comboWatcher.modeTimedOut();
                    switchMode(mutatedMode.timeout().modeName());
                }
            }
        }
    }

    public void switchMode(String newModeName) {
        keyboardState.pollHadUnhandledKeyPressInCurrentMode();
        Mode newMode;
        Mode previousMode = modeHistoryStack.peek();
        if (newModeName.equals(Mode.PREVIOUS_MODE_FROM_HISTORY_STACK_IDENTIFIER)) {
            if (previousMode == null)
                throw new IllegalStateException(
                        "Unable to switch to previous mode as the mode history stack is empty");
            newMode = previousMode;
        }
        else
            newMode = modeMap.get(newModeName);
        if (modeHistoryStack.contains(newMode)) {
            while (!newMode.equals(modeHistoryStack.peek()))
                modeHistoryStack.poll();
            modeHistoryStack.poll();
        }
        else if (currentMode != null &&
                 comboWatcher.getMutatedMode().pushModeToHistoryStack() &&
                 !modeHistoryStack.contains(currentMode))
            modeHistoryStack.push(currentMode);
        currentMode = newMode;
        comboWatcher.modeChanged(newMode);
        Mode mutatedMode = comboWatcher.getMutatedMode();
        resetCurrentModeCursorHidden(mutatedMode);
        resetHideCursorTimer(mutatedMode);
        resetModeTimeoutTimer(mutatedMode);
        previousTimeoutEnabled = mutatedMode.timeout().enabled();
        previousHideCursorEnabled = mutatedMode.hideCursor().enabled();
    }

    private void resetCurrentModeCursorHidden(Mode mode) {
        if (currentModeCursorHidden) {
            if (!mode.hideCursor().enabled() ||
                !mode.hideCursor().idleDuration().equals(Duration.ZERO)) {
                mouseController.showCursor();
                currentModeCursorHidden = false;
            }
        }
        else {
            if (mode.hideCursor().enabled() &&
                mode.hideCursor().idleDuration().equals(Duration.ZERO)) {
                mouseController.hideCursor();
                currentModeCursorHidden = true;
            }
        }
    }

    private void resetModeTimeoutTimer(Mode mode) {
        if (mode.timeout().enabled())
            modeTimeoutTimer = mode.timeout().duration().toNanos() / 1e9d;
    }

    private void resetHideCursorTimer(Mode mode) {
        if (mode.hideCursor().enabled())
            hideCursorIdleTimer =
                    mode.hideCursor().idleDuration().toNanos() / 1e9d;
    }

    @Override
    public void completedCombo() {
        justCompletedCombo = true;
    }

}
