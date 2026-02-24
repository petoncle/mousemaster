package mousemaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * This should be an interface with one implementation per platform.
 */
public class KeyRegurgitator {

    private static final Logger logger = LoggerFactory.getLogger(KeyRegurgitator.class);

    public void regurgitate(KeyboardManager.Regurgitate regurgitate, boolean startRepeat) {
        logger.debug(
                "Regurgitating " + regurgitate.key() + ", startRepeat = " + startRepeat +
                ", release = " + regurgitate.alsoRelease());
        // Note about release:
        // If the following combo is defined: +leftwin-0 +e,
        // Then, when pressing leftwin + g, the Windows Game popup shows up.
        // Then, when pressing and releasing leftwin, the popup is closed.
        // But, if leftwin is not released by mousemaster after being regurgitated,
        // then just pressing g again would open the Windows Game popup, as if leftwin
        // was still being pressed.
        Key key = regurgitate.key();
        WindowsKeyboard.sendInputMoves(
                !regurgitate.alsoRelease()
                        ? List.of(new ResolvedKeyMacroMove(key, true, MacroMoveDestination.OS))
                        : List.of(new ResolvedKeyMacroMove(key, true, MacroMoveDestination.OS),
                        new ResolvedKeyMacroMove(key, false, MacroMoveDestination.OS)),
                startRepeat);
    }

}
