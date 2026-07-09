package mousemaster.platform.linux;

import mousemaster.platform.MouseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub implementation of MouseController for Milestone 1.
 * TODO: Implement X11 mouse control via XWarpPointer and XTest for Milestone 3.
 */
public class LinuxMouse implements MouseController {

    private static final Logger logger = LoggerFactory.getLogger(LinuxMouse.class);

    @Override
    public void beginMove() {
        // TODO: Begin mouse movement batch
    }

    @Override
    public void endMove() {
        // TODO: End mouse movement batch and flush
    }

    @Override
    public void moveBy(boolean xForward, double dx, boolean yForward, double dy) {
        // TODO: Relative mouse movement
    }

    @Override
    public void synchronousMoveTo(int x, int y) {
        // TODO: Absolute mouse movement via XWarpPointer
        logger.debug("synchronousMoveTo({}, {}) - not yet implemented", x, y);
    }

    @Override
    public void pressLeft() {
        // TODO: Left mouse button press via XTest
    }

    @Override
    public void pressMiddle() {
        // TODO: Middle mouse button press via XTest
    }

    @Override
    public void pressRight() {
        // TODO: Right mouse button press via XTest
    }

    @Override
    public void releaseLeft() {
        // TODO: Left mouse button release via XTest
    }

    @Override
    public void releaseMiddle() {
        // TODO: Middle mouse button release via XTest
    }

    @Override
    public void releaseRight() {
        // TODO: Right mouse button release via XTest
    }

    @Override
    public void wheelHorizontallyBy(boolean forward, double delta) {
        // TODO: Horizontal wheel scroll via XTest
    }

    @Override
    public void wheelVerticallyBy(boolean forward, double delta) {
        // TODO: Vertical wheel scroll via XTest
    }

    @Override
    public void showCursor() {
        // TODO: Show cursor (if hidden)
    }

    @Override
    public void hideCursor() {
        // TODO: Hide cursor
    }

}
