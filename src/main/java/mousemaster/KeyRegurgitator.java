package mousemaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * This should be an interface with one implementation per platform.
 */
public class KeyRegurgitator {

    private static final Logger logger = LoggerFactory.getLogger(KeyRegurgitator.class);

    public void regurgitate(Key keyToRegurgitate, boolean startRepeat) {
        logger.trace("Regurgitating " + keyToRegurgitate);
        WindowsKeyboard.sendInput(List.of(new RemappingMove(keyToRegurgitate, true)),
                startRepeat, true);
    }

}
