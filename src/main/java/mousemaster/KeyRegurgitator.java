package mousemaster;

import mousemaster.platform.KeyboardController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class KeyRegurgitator {

    private static final Logger logger = LoggerFactory.getLogger(KeyRegurgitator.class);
    private final KeyboardController keyboard;
    private final boolean logRedactKeys;

    public KeyRegurgitator(KeyboardController keyboard, boolean logRedactKeys) {
        this.keyboard = keyboard;
        this.logRedactKeys = logRedactKeys;
    }

    public void regurgitate(KeyboardManager.Regurgitate regurgitate, boolean startRepeat) {
        logger.debug("Regurgitating " +
                     (logRedactKeys ? "<redacted>" : "+" + regurgitate.key() +
                                                     (regurgitate.alsoRelease() ?
                                                             " -" + regurgitate.key() :
                                                             "")) +
                     (startRepeat ? ", starting repeat" : ""));
        Key key = regurgitate.key();
        keyboard.sendInputMoves(
                !regurgitate.alsoRelease()
                        ? List.of(new ResolvedKeyMacroMove(key, true, MacroMoveDestination.OS))
                        : List.of(new ResolvedKeyMacroMove(key, true, MacroMoveDestination.OS),
                        new ResolvedKeyMacroMove(key, false, MacroMoveDestination.OS)),
                startRepeat);
    }

}
