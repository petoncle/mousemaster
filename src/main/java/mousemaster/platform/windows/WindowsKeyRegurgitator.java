package mousemaster.platform.windows;

import mousemaster.*;

import mousemaster.platform.Keyboard;
import mousemaster.platform.KeyRegurgitator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class WindowsKeyRegurgitator implements KeyRegurgitator {

    private static final Logger logger = LoggerFactory.getLogger(WindowsKeyRegurgitator.class);
    private final Keyboard keyboard;

    public WindowsKeyRegurgitator(Keyboard keyboard) {
        this.keyboard = keyboard;
    }

    @Override
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
        keyboard.sendInputMoves(
                !regurgitate.alsoRelease()
                        ? List.of(new ResolvedKeyMacroMove(key, true, MacroMoveDestination.OS))
                        : List.of(new ResolvedKeyMacroMove(key, true, MacroMoveDestination.OS),
                        new ResolvedKeyMacroMove(key, false, MacroMoveDestination.OS)),
                startRepeat);
    }

}
