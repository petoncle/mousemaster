package mousemaster;

import java.util.logging.Logger;

public class KeyResolver {

    private static final Logger logger = Logger.getLogger(KeyResolver.class.getName());

    private final KeyboardLayout activeKeyboardLayout;
    private final KeyboardLayout configurationKeyboardLayout;

    public KeyResolver(KeyboardLayout activeKeyboardLayout,
                       KeyboardLayout configurationKeyboardLayout) {
        this.activeKeyboardLayout = activeKeyboardLayout;
        this.configurationKeyboardLayout = configurationKeyboardLayout;
    }

    /**
     * If configuration-keyboard-layout is defined and set to uk-azerty,
     * and the key name is 2,
     * and the active layout is fr-azerty,
     * then the resolved key will be é.
     */
    public Key resolve(String keyName) {
        if (configurationKeyboardLayout.equals(activeKeyboardLayout))
            return Key.ofName(keyName);
        Key keyInConfigurationLayout = Key.ofName(keyName);
        int scanCode = configurationKeyboardLayout.scanCode(keyInConfigurationLayout);
        Key keyInActiveLayout = activeKeyboardLayout.keyFromScanCode(scanCode);
        if (keyInActiveLayout == null) {
            logger.warning(
                    "Unable to convert configuration key " + keyInConfigurationLayout +
                    " to the active keyboard layout " + activeKeyboardLayout);
            return keyInConfigurationLayout;
        }
        return keyInActiveLayout;
    }

}
