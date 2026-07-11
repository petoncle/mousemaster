package mousemaster.platform.macos;

import mousemaster.Key;
import mousemaster.KeyboardLayout;
import mousemaster.ResolvedMacroMove;
import mousemaster.platform.KeyboardController;

import java.util.List;

public class MacosKeyboardController implements KeyboardController {

    private KeyboardLayout activeKeyboardLayout;

    void activeKeyboardLayout(KeyboardLayout activeKeyboardLayout) {
        this.activeKeyboardLayout = activeKeyboardLayout;
    }

    KeyboardLayout activeKeyboardLayout() {
        return activeKeyboardLayout;
    }

    @Override
    public void update(double delta) {
    }

    @Override
    public void reset() {
    }

    @Override
    public void sendInputMoves(List<ResolvedMacroMove> moves, boolean startRepeat) {
        throw new UnsupportedOperationException("macOS keyboard event synthesis is not implemented yet");
    }

    @Override
    public void keyPressedNotEaten(Key key) {
    }

    @Override
    public void keyReleasedNotEaten(Key key) {
    }

    @Override
    public void recordEarlyReleaseForQueuedPress(Key key) {
    }

    @Override
    public void clearEarlyReleaseForQueuedPress(Key key) {
    }
}
