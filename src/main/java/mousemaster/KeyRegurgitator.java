package mousemaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * This should be an interface with one implementation per platform.
 */
public class KeyRegurgitator {

    private static final Logger logger = LoggerFactory.getLogger(KeyRegurgitator.class);

    public void regurgitate(Key keyToRegurgitate, boolean startRepeat,
                            boolean releaseRegurgitatedKey) {
        logger.trace(
                "Regurgitating " + keyToRegurgitate + ", startRepeat = " + startRepeat +
                ", releaseRegurgitatedKey = " + releaseRegurgitatedKey);
        // Note about releaseRegurgitatedKey:
        // If the following combo is defined: +leftwin-0 +e,
        // Then, when pressing leftwin + g, the Windows Game popup shows up.
        // Then, when pressing and releasing leftwin, the popup is closed.
        // But, if leftwin is not released by mousemaster after being regurgitated,
        // then just pressing g again would open the Windows Game popup, as if leftwin
        // was still being pressed.
        WindowsKeyboard.sendInput(
                !releaseRegurgitatedKey ? List.of(new RemappingMove(keyToRegurgitate, true)) :
                List.of(new RemappingMove(keyToRegurgitate, true), new RemappingMove(keyToRegurgitate, false)),
                startRepeat, true);
    }

}
