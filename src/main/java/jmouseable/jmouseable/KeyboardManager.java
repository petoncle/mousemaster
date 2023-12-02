package jmouseable.jmouseable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class KeyboardManager {

    private static final Logger logger = LoggerFactory.getLogger(KeyboardManager.class);

    private final ComboWatcher comboWatcher;
    private final HintManager hintManager;
    private final Map<Key, PressKeyEventProcessing> currentlyPressedKeys = new HashMap<>();
    private boolean pressingNotHandledKey; // Handled means part of combo or part of hint.

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
                if (allCurrentlyPressedKeysAreHandled()) {
                    if (hintManager.keyPressed(keyEvent.key()))
                        processing = PressKeyEventProcessing.partOfHint();
                    else
                        processing = comboWatcher.keyEvent(keyEvent);
                    pressingNotHandledKey = !processing.handled();
                }
                else {
                    processing = PressKeyEventProcessing.NOT_HANDLED;
                    pressingNotHandledKey = true;
                }
                currentlyPressedKeys.put(key, processing);
            }
            return processing.mustBeEaten();
        }
        else {
            PressKeyEventProcessing processing = currentlyPressedKeys.remove(key);
            if (processing != null) {
                if (processing.handled()) {
                    if (processing.isPartOfCombo())
                        comboWatcher.keyEvent(keyEvent); // Returns null.
                    // Only a released event corresponding to a pressed event that was eaten should be eaten.
                    return processing.mustBeEaten();
                }
                else {
                    pressingNotHandledKey = !allCurrentlyPressedKeysAreHandled();
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

    public boolean allCurrentlyPressedKeysAreHandled() {
        return currentlyPressedKeys.values()
                                   .stream()
                                   .allMatch(PressKeyEventProcessing::handled);
    }

    public boolean pressingNotHandledKey() {
        return pressingNotHandledKey;
    }

}
