package mousemaster.platform;

import mousemaster.Key;
import mousemaster.ResolvedMacroMove;

import java.util.List;

public interface Keyboard {

    void update(double delta);

    void reset();

    void sendInputMoves(List<ResolvedMacroMove> moves, boolean startRepeat);

    void keyPressedNotEaten(Key key);

    void keyReleasedNotEaten(Key key);

    void recordEarlyReleaseForQueuedPress(Key key);

    void clearEarlyReleaseForQueuedPress(Key key);
}
