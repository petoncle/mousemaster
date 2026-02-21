package mousemaster;

import mousemaster.ComboWatcher.ComboWatcherUpdateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KeyboardManager {

    private static final Logger logger = LoggerFactory.getLogger(KeyboardManager.class);

    private final ComboWatcher comboWatcher;
    private final KeyRegurgitator keyRegurgitator;
    // LinkedHashMap preserves insertion (press) order for correct regurgitation order
    // (e.g. shift before a, not a before shift).
    private final Map<Key, PressKeyEventProcessingSet> currentlyPressedKeys = new LinkedHashMap<>();
    private MacroPlayer macroPlayer;

    public KeyboardManager(ComboWatcher comboWatcher, HintManager hintManager,
                           KeyRegurgitator keyRegurgitator) {
        this.comboWatcher = comboWatcher;
        this.keyRegurgitator = keyRegurgitator;
    }

    public void setMacroPlayer(MacroPlayer macroPlayer) {
        this.macroPlayer = macroPlayer;
    }

    public void update(double delta) {
        if (delta > 10) {
            logger.info("Tick took " + delta + "s, skipping update, clearing currentlyPressedKeys, and breaking combos");
            reset();
        }
        else {
            ComboWatcherUpdateResult watcherUpdateResult = comboWatcher.update(delta);
            if (!watcherUpdateResult.completedCombos().isEmpty())
                markOtherKeysOfTheseCombosAsCompleted(
                        watcherUpdateResult.completedCombos(),
                        watcherUpdateResult.hasComboPreparationBreaker());
            if (watcherUpdateResult.preparationIsNotPrefixAnymore()) {
                regurgitatePressedKeys();
            }
            if (watcherUpdateResult.hasComboPreparationBreaker()) {
                if (watcherUpdateResult.comboPreparationBreakerKey() != null)
                    comboWatcher.reset(watcherUpdateResult.comboPreparationBreakerKey());
                else
                    comboWatcher.breakComboPreparation();
            }
        }
    }

    public boolean pressingKeys() {
        return !currentlyPressedKeys.isEmpty();
    }

    public void reset() {
        regurgitatePressedKeys();
        currentlyPressedKeys.clear();
        comboWatcher.reset();
        macroPlayer.reset();
    }

    public void regurgitatePressedKeys() {
        for (Key keyToRegurgitate : regurgitatePressedKeys(null)) {
            keyRegurgitator.regurgitate(keyToRegurgitate, true, false);
        }
    }

    record EatAndRegurgitates(boolean mustBeEaten, Set<Key> keysToRegurgitate) {

    }

    private static final EatAndRegurgitates
            mustNotBeEatenOnly = new EatAndRegurgitates(false, Set.of());
    private static final EatAndRegurgitates
            mustBeEatenOnly = new EatAndRegurgitates(true, Set.of());

    public EatAndRegurgitates keyEvent(KeyEvent keyEvent) {
        return singleKeyEvent(keyEvent);
    }

    private EatAndRegurgitates singleKeyEvent(KeyEvent keyEvent) {
        Key key = keyEvent.key();
        PressKeyEventProcessingSet processingSet = currentlyPressedKeys.get(key);
        if (keyEvent.isPress()) {
            Set<Key> keysToRegurgitate = Set.of();
            if (processingSet == null) {
                if (!pressingUnhandledKey()) {
                    PressKeyEventProcessingSet comboWatcherProcessingSet =
                            comboWatcher.keyEvent(keyEvent);
                    Map<Combo, PressKeyEventProcessing> processingByCombo =
                            new HashMap<>(comboWatcherProcessingSet.processingByCombo());
                    processingSet = new PressKeyEventProcessingSet(processingByCombo,
                            new HashMap<>(comboWatcherProcessingSet.matchByCombo()));
                }
                else {
                    processingSet = new PressKeyEventProcessingSet(Map.of(), Map.of());
                }
                if (!processingSet.isPartOfComboSequence()) {
                    keysToRegurgitate = regurgitatePressedKeys(null);
                }
                else if (processingSet.isPartOfComboSequence()) {
                    // Regurgitate pressed keys that are not in any of the combos
                    // associated to the key that triggered the current event.
                    Set<Combo> currentCombos = processingSet.processingByCombo().keySet();
                    keysToRegurgitate = new LinkedHashSet<>();
                    for (Map.Entry<Key, PressKeyEventProcessingSet> entry : currentlyPressedKeys.entrySet()) {
                        if (entry.getValue().processingByCombo().keySet().stream().anyMatch(currentCombos::contains))
                            continue;
                        regurgitate(entry.getValue(), keysToRegurgitate, entry.getKey(), false);
                    }
                }
                currentlyPressedKeys.put(key, processingSet);
                if (processingSet.isPartOfCompletedComboSequence()) {
                    markOtherKeysOfTheseCombosAsCompleted(
                            processingSet.partOfCompletedComboSequenceCombosWithMatches(), false);
                }
                if (processingSet.isComboPreparationBreaker()) {
                    comboWatcher.reset(key);
                }
            }
            boolean mustBeEaten = processingSet.mustBeEaten() ||
                                  macroPlayer.isKeyPressedByMacro(key);
            return eatAndRegurgitates(mustBeEaten, keysToRegurgitate);
        }
        else { // Key release.
            if (processingSet != null) {
                if (processingSet.handled() ||
                    processingSet.isPartOfUnpressedComboPreconditionOnly()) {
                    Set<Key> keysToRegurgitate = Set.of();
                    // Avoid passing release event to comboWatcher if the key was a combo preparation breaker.
                    // We could add a property for choosing whether we want to ignore
                    // the release of the combo preparation breaker key. But for now,
                    // we always ignore it.
                    if (!processingSet.isComboPreparationBreaker()) {
                        if (processingSet.isPartOfCombo()) {
                            PressKeyEventProcessingSet releaseProcessingSet =
                                    comboWatcher.keyEvent(keyEvent);
                            if (!currentlyPressedKeys.containsKey(key)) {
                                // comboWatcher runs a command which can show the hint which calls
                                // window.show() from WindowsOverlay#transitionHintContainers.
                                // When doing that, control goes back to JNA which can call the Windows callback,
                                // which calls WindowsPlatform#keyEvent.
                                // This second callback should not happen because
                                // we are already in the callback. Also, the second callback does not
                                // execute everything: a log line placed in WindowsPlatform#keyEvent would be
                                // displayed only by the first callback.
                                return eatAndRegurgitates(processingSet.mustBeEaten(), Set.of());
                            }
                            if (!releaseProcessingSet.handled()) {
                                keysToRegurgitate = regurgitatePressedKeys(null);
                            }
                            else if (releaseProcessingSet.isPartOfCompletedComboSequence()) {
                                for (Map.Entry<Combo, PressKeyEventProcessing> entry : releaseProcessingSet.processingByCombo()
                                                                                                           .entrySet()) {
                                    // Mark current combo as completed (so it is not regurgitated, e.g. +rightalt -rightalt).
                                    processingSet.processingByCombo()
                                                 .compute(entry.getKey(),
                                                         (combo, existingProcessing) -> {
                                                             return existingProcessing ==
                                                                    null ?
                                                                     entry.getValue() :
                                                                     PressKeyEventProcessing.partOfComboSequence(
                                                                             existingProcessing.mustBeEaten(),
                                                                             entry.getValue()
                                                                                  .isPartOfCompletedComboSequence(),
                                                                             existingProcessing.isComboPreparationBreaker());
                                                         });
                                }
                                markOtherKeysOfTheseCombosAsCompleted(
                                        releaseProcessingSet.partOfCompletedComboSequenceCombosWithMatches(),
                                        false);
                                keysToRegurgitate = regurgitatePressedKeys(key);
                            }
                        }
                        if (processingSet.isComboPreparationBreaker()) {
                            comboWatcher.reset(key);
                        }
                    }
                    PressKeyEventProcessingSet pressedProcessingSet = currentlyPressedKeys.remove(key);
//                    logger.info("Removed key " + key, new Exception());
                    boolean mustBeEaten =
                            !macroPlayer.isKeyPressedByMacro(key) &&
                            pressedProcessingSet.mustBeEaten();
                    if (!mustBeEaten)
                        macroPlayer.keyReleasedNotEaten(key);
                    return eatAndRegurgitates(mustBeEaten, keysToRegurgitate);
                }
                else {
                    currentlyPressedKeys.remove(key);
//                    logger.info("Removed key " + key, new Exception());
                    macroPlayer.keyReleasedNotEaten(key);
                    return eatAndRegurgitates(false, regurgitatePressedKeys(null));
                }
            }
            else {
                macroPlayer.keyReleasedNotEaten(key);
                return eatAndRegurgitates(false, Set.of());
            }
        }
    }

    private boolean markOtherKeysOfTheseCombosAsCompleted(List<ComboAndMatch> completedCombos,
                                                          boolean forceIsComboPreparationBreaker) {
        boolean completedCombosHavePressedKeys = false;
        for (ComboAndMatch comboAndMatch : completedCombos) {
            Combo combo = comboAndMatch.combo();
            ComboSequenceMatch match = comboAndMatch.match();
            Set<Key> pressedKeysInCompletedCombo =
                    combo.keysPressedAfterMoves(Set.of(), match.matchedKeyMoves());
            completedCombosHavePressedKeys |= !pressedKeysInCompletedCombo.isEmpty();

            for (Key key : pressedKeysInCompletedCombo) {
                PressKeyEventProcessingSet processingSet = currentlyPressedKeys.get(key);
                if (processingSet == null)
                    continue;
                Map<Combo, PressKeyEventProcessing> processingByCombo =
                        processingSet.processingByCombo();
                PressKeyEventProcessing processing = processingByCombo.get(combo);
                // Can be null with (when going from temp-screen-snap-mode.to.idle-mode,
                // , is pressed and idle-mode's alttab combo is not registered for +rightalt):
                // idle-mode.remapping.alttab=+rightalt-0 +, | _{rightalt} +, -> +leftalt +tab -tab -leftalt
                // temp-screen-snap-mode.remapping.alttab=_{rightalt} +, -> +leftalt +tab -tab -leftalt
                // temp-screen-snap-mode.to.idle-mode=_{rightalt} +,
                // Better solution could be to create the missing processing for that key.
                if (processing != null)
                    processingByCombo.put(combo,
                            PressKeyEventProcessing.partOfComboSequence(
                                    processing.mustBeEaten(), true,
                                    processing.isComboPreparationBreaker() || forceIsComboPreparationBreaker));
            }
        }
        return completedCombosHavePressedKeys;
    }

    private Set<Key> regurgitatePressedKeys(Key releasedKey) {
        Set<Key> keysToRegurgitate = new LinkedHashSet<>();
        for (Map.Entry<Key, PressKeyEventProcessingSet> setEntry : currentlyPressedKeys.entrySet()) {
            Key eatenKey = setEntry.getKey();
            if (releasedKey != null && !eatenKey.equals(releasedKey))
                continue;
            PressKeyEventProcessingSet processingSet = setEntry.getValue();
            regurgitate(processingSet, keysToRegurgitate, eatenKey, releasedKey != null);
        }
        return keysToRegurgitate;
    }

    private void regurgitate(PressKeyEventProcessingSet processingSet,
                           Set<Key> keysToRegurgitate, Key eatenKey, boolean isRelease) {
        // One of the combo is mustBeEaten, and there is no mustBeEaten combo that is completed.
        if (processingSet.mustBeEaten() &&
            !processingSet.isPartOfCompletedComboSequenceAndMustBeEaten() &&
            // Releases are always PART_OF_COMPLETED_COMBO_SEQUENCE_MUST_NOT_BE_EATEN
            // (instead of PART_OF_COMPLETED_COMBO_SEQUENCE_MUST_BE_EATEN).
            // And if a key is released and it is part of a completed combo sequence (and
            // if we're here it means this key release was the last move of the combo),
            // then it should not be regurgitated.
            !(isRelease && processingSet.isPartOfCompletedComboSequence())
        ) {
            keysToRegurgitate.add(eatenKey);
            // Change the key's processing to must not be eaten
            // so that it cannot be regurgitated a second time.
            for (Map.Entry<Combo, PressKeyEventProcessing> entry : Set.copyOf(
                    processingSet.processingByCombo().entrySet())) {
                Combo combo = entry.getKey();
                PressKeyEventProcessing processing = entry.getValue();
                if (processing.isPartOfComboSequence())
                    processingSet.processingByCombo()
                                 .put(combo,
                                         PressKeyEventProcessing.partOfComboSequence(
                                                 false,
                                                 processing.isPartOfCompletedComboSequence(),
                                                 false));
            }
        }
    }

    private static EatAndRegurgitates eatAndRegurgitates(boolean mustBeEaten,
                                                         Set<Key> keysToRegurgitate) {
        if (keysToRegurgitate.isEmpty())
            return mustBeEaten ? mustBeEatenOnly : mustNotBeEatenOnly;
        return new EatAndRegurgitates(mustBeEaten, keysToRegurgitate);
    }

    public boolean pressingUnhandledKey() {
        for (PressKeyEventProcessingSet processingSet : currentlyPressedKeys.values()) {
            if (!processingSet.handled())
                return true;
        }
        return false;
    }

    /**
     * Returns true if the key is currently pressed by the user and the press was not eaten
     * (i.e. the rest of the OS apps saw the press).
     */
    public boolean isPressedKeyNotEaten(Key key) {
        PressKeyEventProcessingSet processingSet = currentlyPressedKeys.get(key);
        return processingSet != null && !processingSet.mustBeEaten();
    }

}
