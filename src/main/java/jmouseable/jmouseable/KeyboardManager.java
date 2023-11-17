package jmouseable.jmouseable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class KeyboardManager {

    private static final Logger logger = LoggerFactory.getLogger(KeyboardManager.class);

    private final ComboWatcher comboWatcher;
    private final Map<Key, KeyEventProcessing> currentlyPressedKeys = new HashMap<>();
    private boolean pressingNonComboKey;

    public KeyboardManager(ComboWatcher comboWatcher) {
        this.comboWatcher = comboWatcher;
    }

    public void update(double delta) {
        if (delta > 10) {
            logger.info("Tick took " + delta + "s, skipping update, clearing currentlyPressedKeys, and interrupting combos");
            currentlyPressedKeys.clear();
            comboWatcher.interrupt();
        }
    }

    public boolean keyEvent(KeyEvent keyEvent) {
        Key key = keyEvent.action().key();
        if (keyEvent.action().state().pressed()) {
            KeyEventProcessing keyEventProcessing = currentlyPressedKeys.get(key);
            if (keyEventProcessing == null) {
                if (allCurrentlyPressedArePartOfCombo()) {
                    keyEventProcessing = comboWatcher.keyEvent(keyEvent);
                    pressingNonComboKey = !keyEventProcessing.partOfCombo();
                }
                else {
                    keyEventProcessing = new KeyEventProcessing(false, false);
                    pressingNonComboKey = true;
                }
                currentlyPressedKeys.put(key, keyEventProcessing);
            }
            return keyEventProcessing.partOfComboAndMustBeEaten();
        }
        else {
            KeyEventProcessing pressedKeyEventProcessing = currentlyPressedKeys.remove(key);
            if (pressedKeyEventProcessing != null) {
                if (pressedKeyEventProcessing.partOfCombo()) {
                    KeyEventProcessing releasedKeyEventProcessing =
                            comboWatcher.keyEvent(keyEvent);
                    // Only a released event corresponding to pressed event that was eaten must be eaten.
                    // TODO No need for non-eatable release move ;^
                    return pressedKeyEventProcessing.partOfComboAndMustBeEaten();
                }
                else {
                    pressingNonComboKey = !allCurrentlyPressedArePartOfCombo();
                    return false;
                }
            }
            else
                return false;
        }
    }

    public boolean currentlyPressed(Key key) {
        return currentlyPressedKeys.containsKey(key);
    }

    public boolean allCurrentlyPressedArePartOfCombo() {
        return currentlyPressedKeys.values()
                                   .stream()
                                   .allMatch(KeyEventProcessing::partOfCombo);
    }

    public boolean pressingNonHandledKey() {
        return pressingNonComboKey;
    }

}
