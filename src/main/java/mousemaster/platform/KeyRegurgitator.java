package mousemaster.platform;

import mousemaster.Key;
import mousemaster.KeyboardManager;
import mousemaster.MacroMoveDestination;
import mousemaster.ResolvedKeyMacroMove;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class KeyRegurgitator {

    private static final Logger logger = LoggerFactory.getLogger(KeyRegurgitator.class);
    private final PlatformKeyboard keyboard;

    public KeyRegurgitator(PlatformKeyboard keyboard) {
        this.keyboard = keyboard;
    }

    public void regurgitate(KeyboardManager.Regurgitate regurgitate, boolean startRepeat) {
        logger.debug(
                "Regurgitating " + regurgitate.key() + ", startRepeat = " + startRepeat +
                ", release = " + regurgitate.alsoRelease());
        Key key = regurgitate.key();
        keyboard.sendInputMoves(
                !regurgitate.alsoRelease()
                        ? List.of(new ResolvedKeyMacroMove(key, true, MacroMoveDestination.OS))
                        : List.of(new ResolvedKeyMacroMove(key, true, MacroMoveDestination.OS),
                        new ResolvedKeyMacroMove(key, false, MacroMoveDestination.OS)),
                startRepeat);
    }

}
