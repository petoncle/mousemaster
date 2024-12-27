package mousemaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * This should be an interface with one implementation per platform.
 */
public class KeyRegurgitator {

    private static final Logger logger = LoggerFactory.getLogger(KeyRegurgitator.class);

    public void regurgitate(Key keyToRegurgitate, boolean keyAlreadyReleasedByUser) {
        logger.info("Regurgitating " + keyToRegurgitate, new Exception()); // TODO trace
        WindowsKeyboard.sendInput(List.of(new RemappingMove(keyToRegurgitate, true)),
                !keyAlreadyReleasedByUser, true);
    }

}
