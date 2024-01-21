package mousemaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class KeyboardManager {

    private static final Logger logger = LoggerFactory.getLogger(KeyboardManager.class);

    private final ComboWatcher comboWatcher;
    private final HintManager hintManager;
    private final Map<Key, PressKeyEventProcessing> currentlyPressedKeys = new HashMap<>();

    public KeyboardManager(ComboWatcher comboWatcher, HintManager hintManager) {
        this.comboWatcher = comboWatcher;
        this.hintManager = hintManager;
    }

    public void update(double delta) {
        if (delta > 10) {
            logger.info("Tick took " + delta + "s, skipping update, clearing currentlyPressedKeys, and breaking combos");
            reset();
        }
        else {
            comboWatcher.update(delta);
        }
    }

    public void reset() {
        currentlyPressedKeys.clear();
        comboWatcher.reset();
    }

    public boolean keyEvent(KeyEvent keyEvent) {
        Key key = keyEvent.key();
        if (keyEvent.isPress()) {
            PressKeyEventProcessing processing = currentlyPressedKeys.get(key);
            if (processing == null) {
                if (!pressingUnhandledKey()) {
                    processing = hintManager.keyPressed(keyEvent.key());
                    if (!processing.handled())
                        processing = comboWatcher.keyEvent(keyEvent);
                    else if (processing.isHintEnd())
                        comboWatcher.keyEvent(keyEvent);
                }
                else {
                    // Even if pressing unhandled key, give the hint manager a chance.
                    // This is so the user can hold leftctrl (assuming it is unhandled), then
                    // select a hint to perform a ctrl-click.
                    processing = hintManager.keyPressed(keyEvent.key());
                }
                currentlyPressedKeys.put(key, processing);
            }
            return processing.mustBeEaten();
        }
        else {
            PressKeyEventProcessing processing = currentlyPressedKeys.remove(key);
            if (processing != null) {
                if (processing.handled()) {
                    if (processing.isPartOfCombo() || processing.isHintEnd())
                        comboWatcher.keyEvent(keyEvent); // Returns null.
                    // Only a released event corresponding to a pressed event that was eaten should be eaten.
                    return processing.mustBeEaten();
                }
                else
                    return false;
            }
            else
                return false;
        }
    }

    /**
     * Handled means part of combo or part of hint.
     */
    public boolean pressingUnhandledKeysOnly() {
        if (currentlyPressedKeys.isEmpty())
            return false;
        for (PressKeyEventProcessing pressKeyEventProcessing : currentlyPressedKeys.values()) {
            if (pressKeyEventProcessing.handled())
                return false;
        }
        return true;
    }

    public boolean pressingUnhandledKey() {
        for (PressKeyEventProcessing pressKeyEventProcessing : currentlyPressedKeys.values()) {
            if (!pressKeyEventProcessing.handled())
                return true;
        }
        return false;
    }

}
