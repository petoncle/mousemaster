package mousemaster.platform.windows;

import mousemaster.*;

import mousemaster.platform.Keyboard;

import java.util.List;

public class WindowsKeyboardAdapter implements Keyboard {

    @Override
    public void update(double delta) {
        WindowsKeyboard.update(delta);
    }

    @Override
    public void reset() {
        WindowsKeyboard.reset();
    }

    @Override
    public void sendInputMoves(List<ResolvedMacroMove> moves, boolean startRepeat) {
        WindowsKeyboard.sendInputMoves(moves, startRepeat);
    }

    @Override
    public void keyPressedNotEaten(Key key) {
        WindowsKeyboard.keyPressedNotEaten(key);
    }

    @Override
    public void keyReleasedNotEaten(Key key) {
        WindowsKeyboard.keyReleasedNotEaten(key);
    }

    @Override
    public void recordEarlyReleaseForQueuedPress(Key key) {
        WindowsKeyboard.recordEarlyReleaseForQueuedPress(key);
    }

    @Override
    public void clearEarlyReleaseForQueuedPress(Key key) {
        WindowsKeyboard.clearEarlyReleaseForQueuedPress(key);
    }

}
