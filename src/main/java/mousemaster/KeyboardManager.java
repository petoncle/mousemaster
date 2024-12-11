package mousemaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
            if (!hintManager.finalizingHintSelection()) {
                while (!eventsWaitingForHintSelectionFinalization.isEmpty()) {
                    logger.trace(
                            "Processing event from hint selection finalization queue: " +
                            eventsWaitingForHintSelectionFinalization.getFirst());
                    singleKeyEvent(eventsWaitingForHintSelectionFinalization.removeFirst());
                    nextEventIsUnswallowedHintEnd = false;
                }
            }
            comboWatcher.update(delta);
        }
    }

    public void reset() {
        currentlyPressedKeys.clear();
        comboWatcher.reset();
    }

    record EatAndRegurgitates(boolean mustBeEaten, Set<Key> keysToRegurgitate) {

    }

    private static final EatAndRegurgitates
            mustNotBeEatenOnly = new EatAndRegurgitates(false, Set.of());
    private static final EatAndRegurgitates
            mustBeEatenOnly = new EatAndRegurgitates(true, Set.of());

    private List<KeyEvent> eventsWaitingForHintSelectionFinalization = new ArrayList<>();
    private boolean nextEventIsUnswallowedHintEnd;

    public EatAndRegurgitates keyEvent(KeyEvent keyEvent) {
        if (hintManager.finalizingHintSelection()) {
            logger.trace("Enqueuing keyEvent because finalizing hint selection: " + keyEvent);
            eventsWaitingForHintSelectionFinalization.add(keyEvent);
            return eatAndRegurgitates(true, Set.of());
        }
        while (!eventsWaitingForHintSelectionFinalization.isEmpty()) {
            singleKeyEvent(eventsWaitingForHintSelectionFinalization.removeFirst());
            nextEventIsUnswallowedHintEnd = false;
        }
        return singleKeyEvent(keyEvent);
    }

    private EatAndRegurgitates singleKeyEvent(KeyEvent keyEvent) {
        Key key = keyEvent.key();
        if (keyEvent.isPress()) {
            PressKeyEventProcessing processing = currentlyPressedKeys.get(key);
            Set<Key> keysToRegurgitate = Set.of();
            if (processing == null) {
                if (!pressingUnhandledKey()) {
                    if (!nextEventIsUnswallowedHintEnd)
                        processing = hintManager.keyPressed(keyEvent.key());
                    if (nextEventIsUnswallowedHintEnd || !processing.handled())
                        processing = comboWatcher.keyEvent(keyEvent);
                    else if (processing.isUnswallowedHintEnd()) {
                        logger.trace("Enqueuing unswallowed hint end keyEvent " + keyEvent);
                        eventsWaitingForHintSelectionFinalization.add(keyEvent);
                        nextEventIsUnswallowedHintEnd = true;
                        // Key not put in currentlyPressedKeys.
                        return eatAndRegurgitates(true, keysToRegurgitate);
                    }
                }
                else {
                    // Even if pressing unhandled key, give the hint manager a chance.
                    // This is so the user can hold leftctrl (assuming it is unhandled), then
                    // select a hint to perform a ctrl-click.
                    processing = hintManager.keyPressed(keyEvent.key());
                }
                if (!processing.mustBeEaten()) {
                    keysToRegurgitate = new HashSet<>();
                    for (Map.Entry<Key, PressKeyEventProcessing> entry : Set.copyOf(
                            currentlyPressedKeys.entrySet())) {
                        if (entry.getValue().mustBeEaten()) {
                            Key eatenKey = entry.getKey();
                            keysToRegurgitate.add(eatenKey);
                            // Change the key's processing to must not be eaten
                            // so that it cannot be regurgitated a second time.
                            currentlyPressedKeys.put(key, switch (entry.getValue()) {
                                case PART_OF_COMBO_SEQUENCE_MUST_BE_EATEN -> PressKeyEventProcessing.PART_OF_COMBO_SEQUENCE_MUST_NOT_BE_EATEN;
                                default -> entry.getValue();
                            });
                        }
                    }
                }
                currentlyPressedKeys.put(key, processing);
            }
            return eatAndRegurgitates(processing.mustBeEaten(), keysToRegurgitate);
        }
        else {
            PressKeyEventProcessing processing = currentlyPressedKeys.remove(key);
            if (processing != null) {
                if (processing.handled() ||
                    processing.isPartOfMustRemainUnpressedComboPreconditionOnly()) {
                    if (processing.isPartOfCombo() || processing.isUnswallowedHintEnd())
                        comboWatcher.keyEvent(keyEvent); // Returns null.
                    // Only a released event corresponding to a pressed event that was eaten should be eaten.
                    return eatAndRegurgitates(processing.mustBeEaten(), Set.of());
                }
                else
                    return eatAndRegurgitates(false, Set.of());
            }
            else
                return eatAndRegurgitates(false, Set.of());
        }
    }

    private static EatAndRegurgitates eatAndRegurgitates(boolean mustBeEaten,
                                                         Set<Key> keysToRegurgitate) {
        if (keysToRegurgitate.isEmpty())
            return mustBeEaten ? mustBeEatenOnly : mustNotBeEatenOnly;
        return new EatAndRegurgitates(mustBeEaten, keysToRegurgitate);
    }

    public boolean pressingUnhandledKey() {
        for (PressKeyEventProcessing pressKeyEventProcessing : currentlyPressedKeys.values()) {
            if (!pressKeyEventProcessing.handled())
                return true;
        }
        return false;
    }

}
