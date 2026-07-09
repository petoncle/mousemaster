package mousemaster.platform.linux;

import mousemaster.Key;
import mousemaster.ResolvedMacroMove;
import mousemaster.platform.KeyboardController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Stub implementation of KeyboardController for Milestone 1.
 * TODO: Implement X11 keyboard event reading and injection for Milestone 2.
 */
public class LinuxKeyboard implements KeyboardController {

    private static final Logger logger = LoggerFactory.getLogger(LinuxKeyboard.class);

    @Override
    public void update(double delta) {
        // TODO: Process keyboard events from X11
    }

    @Override
    public void reset() {
        // TODO: Reset keyboard state
    }

    @Override
    public void sendInputMoves(List<ResolvedMacroMove> moves, boolean startRepeat) {
        // TODO: Send keyboard input via XTest
        logger.debug("sendInputMoves() called with {} moves", moves.size());
    }

    @Override
    public void keyPressedNotEaten(Key key) {
        // TODO: Handle key press that wasn't consumed
    }

    @Override
    public void keyReleasedNotEaten(Key key) {
        // TODO: Handle key release that wasn't consumed
    }

    @Override
    public void recordEarlyReleaseForQueuedPress(Key key) {
        // TODO: Track early releases
    }

    @Override
    public void clearEarlyReleaseForQueuedPress(Key key) {
        // TODO: Clear early release tracking
    }

}
