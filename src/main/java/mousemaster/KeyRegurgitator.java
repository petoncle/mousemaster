package mousemaster;

import mousemaster.platform.KeyboardController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class KeyRegurgitator {

    private static final Logger logger = LoggerFactory.getLogger(KeyRegurgitator.class);
    private final KeyboardController keyboard;
    private final KeyRedactor keyRedactor;

    public KeyRegurgitator(KeyboardController keyboard, KeyRedactor keyRedactor) {
        this.keyboard = keyboard;
        this.keyRedactor = keyRedactor;
    }

    public void regurgitate(KeyboardManager.Regurgitate regurgitate, boolean startRepeat) {
        logger.debug("Regurgitating +" + keyRedactor.key(regurgitate.key()) +
                     (regurgitate.alsoRelease() ?
                             " -" + keyRedactor.key(regurgitate.key()) : "") +
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
