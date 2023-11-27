package jmouseable.jmouseable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class KeyboardManager {

    private static final Logger logger = LoggerFactory.getLogger(KeyboardManager.class);

    private final ComboWatcher comboWatcher;
    private final Map<Key, PressKeyEventProcessing> currentlyPressedKeys = new HashMap<>();
    private boolean pressingNonComboKey;

    public KeyboardManager(ComboWatcher comboWatcher) {
        this.comboWatcher = comboWatcher;
    }

    public void update(double delta) {
        if (delta > 10) {
            logger.info("Tick took " + delta + "s, skipping update, clearing currentlyPressedKeys, and interrupting combos");
            reset();
        }
        else {
            comboWatcher.update(delta);
        }
    }

    public void reset() {
        currentlyPressedKeys.clear();
        comboWatcher.interrupt();
    }

    public boolean keyEvent(KeyEvent keyEvent) {
        Key key = keyEvent.key();
        if (keyEvent.isPress()) {
            PressKeyEventProcessing processing = currentlyPressedKeys.get(key);
            if (processing == null) {
                if (allCurrentlyPressedKeysArePartOfCombo()) {
                    processing = comboWatcher.keyEvent(keyEvent);
                    pressingNonComboKey = !processing.partOfCombo();
                }
                else {
                    processing = PressKeyEventProcessing.NOT_PART_OF_COMBO;
                    pressingNonComboKey = true;
                }
                currentlyPressedKeys.put(key, processing);
            }
            return processing.mustBeEaten();
        }
        else {
            PressKeyEventProcessing processing = currentlyPressedKeys.remove(key);
            if (processing != null) {
                if (processing.partOfCombo()) {
                    comboWatcher.keyEvent(keyEvent); // Returns null.
                    // Only a released event corresponding to a pressed event that was eaten should be eaten.
                    return processing.mustBeEaten();
                }
                else {
                    pressingNonComboKey = !allCurrentlyPressedKeysArePartOfCombo();
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

    public boolean allCurrentlyPressedKeysArePartOfCombo() {
        return currentlyPressedKeys.values()
                                   .stream()
                                   .allMatch(PressKeyEventProcessing::partOfCombo);
    }

    public boolean pressingNonComboKey() {
        return pressingNonComboKey;
    }

}
