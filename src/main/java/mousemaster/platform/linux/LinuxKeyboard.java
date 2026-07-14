package mousemaster.platform.linux;

import mousemaster.Key;
import mousemaster.MacroMoveDestination;
import mousemaster.ResolvedKeyMacroMove;
import mousemaster.ResolvedMacroMove;
import mousemaster.platform.KeyboardController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Implements keyboard passthrough via a uinput virtual keyboard device.
 *
 * Physical keys are exclusively grabbed by LinuxEvdev (EVIOCGRAB). Keys that the
 * combo engine does not consume are re-emitted here so other apps see them normally.
 * Macro/regurgitated keys are also injected here. The uinput device is named
 * LibUinput.KEYBOARD_DEVICE_NAME so the evdev reader skips it (no feedback loop).
 */
public class LinuxKeyboard implements KeyboardController {

    private static final Logger logger = LoggerFactory.getLogger(LinuxKeyboard.class);

    private final int uinputFd;

    public LinuxKeyboard(int uinputFd) {
        this.uinputFd = uinputFd;
    }

    public void destroy() {
        LibUinput.destroyDevice(uinputFd);
    }

    @Override
    public void update(double delta) {
    }

    @Override
    public void reset() {
    }

    @Override
    public void sendInputMoves(List<ResolvedMacroMove> moves, boolean startRepeat) {
        for (ResolvedMacroMove move : moves) {
            switch (move) {
                case ResolvedKeyMacroMove km -> {
                    if (km.destination() == MacroMoveDestination.OS)
                        emitKey(km.key(), km.press() ? 1 : 0);
                }
                default -> logger.warn("sendInputMoves: unsupported move type {}", move.getClass().getSimpleName());
            }
        }
    }

    @Override
    public void keyPressedNotEaten(Key key) {
        emitKey(key, 1);
    }

    @Override
    public void keyReleasedNotEaten(Key key) {
        emitKey(key, 0);
    }

    @Override
    public void recordEarlyReleaseForQueuedPress(Key key) {
        // No send queue on Linux — writes are synchronous, nothing to track
    }

    @Override
    public void clearEarlyReleaseForQueuedPress(Key key) {
    }

    private void emitKey(Key key, int value) {
        Integer code = LinuxVirtualKey.toEvdevCode(key);
        if (code == null) {
            logger.warn("No evdev code for key {}, cannot emit", key);
            return;
        }
        LibUinput.writeInputEvent(uinputFd, LibUinput.EV_KEY, code, value);
        LibUinput.writeInputEvent(uinputFd, LibUinput.EV_SYN, LibUinput.SYN_REPORT, 0);
    }
}
