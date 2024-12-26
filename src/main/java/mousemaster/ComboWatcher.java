package mousemaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

public class ComboWatcher implements ModeListener {

    private static final Logger logger = LoggerFactory.getLogger(ComboWatcher.class);

    private final CommandRunner commandRunner;
    private final ActiveAppFinder activeAppFinder;
    private final Set<Key> mustRemainPressedComboPreconditionKeys;
    private final Set<Key> mustRemainUnpressedComboPreconditionKeys;
    private Mode currentMode;
    private boolean modeJustTimedOut;
    private ComboPreparation comboPreparation;
    private PressKeyEventProcessingSet lastProcessingSet;
    private ComboMoveDuration previousComboMoveDuration;
    private List<ComboWaitingForLastMoveToComplete> combosWaitingForLastMoveToComplete = new ArrayList<>();

    private Set<Key> currentlyPressedCompletedComboSequenceKeys = new HashSet<>();
    private Set<Key> currentlyPressedComboPreconditionKeys = new HashSet<>();

    public ComboWatcher(CommandRunner commandRunner, ActiveAppFinder activeAppFinder,
                        Set<Key> mustRemainUnpressedComboPreconditionKeys,
                        Set<Key> mustRemainPressedComboPreconditionKeys) {
        this.commandRunner = commandRunner;
        this.activeAppFinder = activeAppFinder;
        this.mustRemainUnpressedComboPreconditionKeys =
                mustRemainUnpressedComboPreconditionKeys;
        this.mustRemainPressedComboPreconditionKeys =
                mustRemainPressedComboPreconditionKeys;
        this.comboPreparation = ComboPreparation.empty();
    }

    public record ComboWatcherUpdateResult(Set<Combo> completedWaitingCombos, boolean preparationIsNotPrefixAnymore) {

    }

    public ComboWatcherUpdateResult update(double delta) {
        boolean preparationIsNotPrefixAnymore = false;
        if (lastProcessingSet != null) {
            // Check if the preparation is still a prefix of at least one combo.
            // If it is not, then it means a key is being pressed for longer than what the combo expects,
            // and the key can be regurgitated (just like it is regurgitated upon key release).
            // (Regurgitate only +key, not #key.)
            boolean atLeastOneProcessingIsComboSequenceMustBeEaten = false;
            boolean preparationIsStillPrefixOfAtLeastOneCombo = false;
            Instant currentTime = Instant.now();
            for (var entry : lastProcessingSet.processingByCombo().entrySet()) {
                Combo combo = entry.getKey();
                PressKeyEventProcessing processing = entry.getValue();
                if (processing.isPartOfComboSequenceMustBeEaten()) {
                    atLeastOneProcessingIsComboSequenceMustBeEaten = true;
                    int matchingMoveCount = comboPreparation.matchingMoveCount(combo.sequence());
                    if (matchingMoveCount != 0) {
                        ComboMove currentMove =
                                combo.sequence().moves().get(matchingMoveCount - 1);
                        KeyEvent currentKeyEvent = comboPreparation.events().getLast();
                        if (!currentMove.duration()
                                        .tooMuchTimeHasPassed(currentKeyEvent.time(),
                                                currentTime))
                            preparationIsStillPrefixOfAtLeastOneCombo = true;
                    }
                }
            }
            if (atLeastOneProcessingIsComboSequenceMustBeEaten && !preparationIsStillPrefixOfAtLeastOneCombo) {
                preparationIsNotPrefixAnymore = true;
            }
        }
        // For a given waiting combo, we know that its precondition has to be satisfied still, because otherwise it
        // would mean that currentlyPressedComboPreconditionKeys has changed. But when currentlyPressedComboPreconditionKeys is changed,
        // combosWaitingForLastMoveToComplete is always reset.
        List<ComboWaitingForLastMoveToComplete> completeCombos = new ArrayList<>();
        Set<Combo> completedCombos = new HashSet<>();
        for (ComboWaitingForLastMoveToComplete comboWaitingForLastMoveToComplete : combosWaitingForLastMoveToComplete) {
            comboWaitingForLastMoveToComplete.remainingWait -= delta;
            if (comboWaitingForLastMoveToComplete.remainingWait < 0) {
                completeCombos.add(comboWaitingForLastMoveToComplete);
                Combo combo = comboWaitingForLastMoveToComplete.comboAndCommands.combo;
                addCurrentlyPressedCompletedComboSequenceKeys(combo);
                // We tell KeyboardManager that a combo was completed,
                // and all the currently pressed keys are part of a completed combo,
                // and they should not be regurgitated.
                completedCombos.add(combo);
            }
        }
        List<Command> commandsToRun = longestComboCommandsLastAndDeduplicate(completeCombos.stream()
                                                                                           .map(ComboWaitingForLastMoveToComplete::comboAndCommands)
                                                                                           .toList());
        if (!commandsToRun.isEmpty()) {
            logger.debug(
                    "Completed combos that were waiting for last move to complete, currentMode = " +
                    currentMode.name() + ", completeCombos = " + completeCombos.stream()
                                                                               .map(ComboWaitingForLastMoveToComplete::comboAndCommands)
                                                                               .map(ComboAndCommands::combo)
                                                                               .toList() +
                    ", commandsToRun = " + commandsToRun);
        }
        Mode beforeMode = currentMode;
        commandsToRun.forEach(commandRunner::run);
        combosWaitingForLastMoveToComplete.removeAll(completeCombos);
        if (currentMode != beforeMode) {
            PressKeyEventProcessingSet processingSet =
                    processKeyEventForCurrentMode(null, true);
            completedCombos.addAll(processingSet.completedCombos());
        }
        return new ComboWatcherUpdateResult(completedCombos, preparationIsNotPrefixAnymore);
    }

    public PressKeyEventProcessingSet keyEvent(KeyEvent event) {
        modeJustTimedOut = false;
        boolean isMustRemainUnpressedComboPreconditionKey =
                mustRemainUnpressedComboPreconditionKeys.contains(event.key());
        boolean isMustRemainPressedComboPreconditionKey =
                mustRemainPressedComboPreconditionKeys.contains(event.key());
        boolean isComboPreconditionKey =
                isMustRemainUnpressedComboPreconditionKey ||
                isMustRemainPressedComboPreconditionKey;
        if (event.isRelease()) {
            // The corresponding press event was either part of a combo sequence or part of a combo precondition,
            // otherwise this method would not have been called.
            currentlyPressedCompletedComboSequenceKeys.remove(event.key());
            currentlyPressedComboPreconditionKeys.remove(event.key());
        }
        else {
            if (isComboPreconditionKey) {
                currentlyPressedComboPreconditionKeys.add(event.key());
            }
        }
        if (!combosWaitingForLastMoveToComplete.isEmpty())
            combosWaitingForLastMoveToComplete.clear();
        KeyEvent previousEvent = comboPreparation.events().isEmpty() ? null :
                comboPreparation.events().getLast();
        if (previousEvent != null &&
            !previousComboMoveDuration.satisfied(previousEvent.time(), event.time()))
            comboPreparation = ComboPreparation.empty();
        comboPreparation.events().add(event);
        Mode beforeMode = currentMode;
        PressKeyEventProcessingSet processingSet =
                processKeyEventForCurrentMode(event, false);
        if (currentMode != beforeMode) {
            // Second pass to give a chance to new mode's combos to run now.
            processingSet.processingByCombo().putAll(
                    processKeyEventForCurrentMode(event, true).processingByCombo());
        }
        if (!processingSet.isPartOfComboSequence()) {
            comboPreparation = ComboPreparation.empty();
            if (event.isPress() && isComboPreconditionKey) {
                // We don't really need to know which combo(s) this is for, that is why
                // we use dummyCombo instead. But it would be cleaner if we knew the combos.
                // If we are here, it means the key is used as a precondition key,
                // possibly for a mode different from the current mode.
                processingSet = new PressKeyEventProcessingSet(
                        new HashMap<>(Map.of(PressKeyEventProcessingSet.dummyCombo,
                                isMustRemainPressedComboPreconditionKey ?
                                        PressKeyEventProcessing.partOfMustRemainPressedComboPreconditionOnly() :
                                        PressKeyEventProcessing.partOfMustRemainUnpressedComboPreconditionOnly())));
            }
        }
        else {
            if (event.isPress())
                currentlyPressedComboPreconditionKeys.add(event.key());
        }
        lastProcessingSet = processingSet;
        return processingSet;
    }

    private PressKeyEventProcessingSet processKeyEventForCurrentMode(
            KeyEvent event,
            boolean ignoreSwitchModeCommands) {
        Map<Combo, PressKeyEventProcessing> processingByCombo = new HashMap<>();
        List<ComboAndCommands> comboAndCommandsToRun = new ArrayList<>();
        Set<Key> currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten =
                new HashSet<>(currentlyPressedComboPreconditionKeys);
//        currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten.removeIf(
//                Predicate.not(mustRemainPressedComboPreconditionKeys::contains));
        currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten.removeAll(
                currentlyPressedCompletedComboSequenceKeys); // We don't want to skip +a which moves mouse slowly after pressing and holding +v for slow mode
        if (event != null)
            currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten.remove(
                    event.key());
        ComboMoveDuration newComboDuration = null;
        for (Map.Entry<Combo, List<Command>> entry : currentMode.comboMap()
                                                                .commandsByCombo()
                                                                .entrySet()) {
            // When a precondition key is pressed, and another key is pressed,
            // that other key should be processed only for combos that
            // contains the pressed precondition key.
            Combo combo = entry.getKey();
            if (!combo.precondition()
                      .appPrecondition()
                      .satisfied(activeAppFinder.activeApp()))
                continue;
            int matchingMoveCount = comboPreparation.matchingMoveCount(combo.sequence());
            ComboMove currentMove = matchingMoveCount == 0 ? null :
                    combo.sequence().moves().get(matchingMoveCount - 1);
            // releaseCombo == the combo is not just a mustRemainUnpressed combo (it has a sequence or a mustRemainPressed precondition)
            boolean releaseCombo =
                    combo.precondition()
                         .keyPrecondition()
                         .mustRemainPressedKeySets()
                         .isEmpty() &&
                    (combo.sequence().moves().isEmpty() || combo.sequence()
                                                                .moves()
                                                                .stream()
                                                                .allMatch(
                                                                        ComboMove::isRelease)); // This condition (check sequence is all releases)
            // could be removed to not execute combos that have a sequence and whose mustRemainPress condition is not satisfied.
            if (!currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten.isEmpty()
                // If currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten is not part of the combo's mustRemainPressedKeySets...
                && combo.precondition().keyPrecondition()
                        .mustRemainPressedKeySets()
                        .stream()
                        .noneMatch(
                                comboMustRemainPressedKeySet -> comboMustRemainPressedKeySet.containsAll(
                                        currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten)) &&
                // ...and the combo is not a release combo
                !releaseCombo &&
                // ...and the combo's current move is not a press of that currentlyPressedComboPreconditionKey...
                !combo.keysPressedInComboPriorToMoveOfIndex(
                        matchingMoveCount - 1).containsAll(
                        currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten)) {
                // ...Then it's as if the currently pressed precondition key is an unhandled key:
                // other keys that are pressed should not even be considered but passed onto other apps.
                // logger.info("currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten = " +
                //             currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten +
                //             ", skipping combo: " + combo);
                continue;
            }
            boolean mustBeEaten = false;
            boolean partOfComboSequence = false;
            if (matchingMoveCount == 0) {
                if (combo.sequence().moves().isEmpty() &&
                    !combo.precondition().isEmpty()) {
                    if (!combo.precondition().keyPrecondition().satisfied(
                            currentlyPressedComboPreconditionKeys)) {
                        continue;
                    }
                }
            }
            else {
                if (!combo.precondition().isEmpty()) {
                    if (!combo.precondition().keyPrecondition().satisfied(
                            currentlyPressedComboPreconditionKeys))
                        continue;
                }
                boolean currentMoveMustBeEaten =
                        currentMove instanceof ComboMove.PressComboMove pressComboMove &&
                        pressComboMove.eventMustBeEaten();
                mustBeEaten = currentMoveMustBeEaten;
                partOfComboSequence = true;
                if (newComboDuration == null) {
                    newComboDuration = currentMove.duration();
                }
                else {
                    if (newComboDuration.min().compareTo(currentMove.duration().min()) >
                        0)
                        newComboDuration =
                                new ComboMoveDuration(currentMove.duration().min(),
                                        newComboDuration.max());
                    if (newComboDuration.max() != null &&
                        (currentMove.duration().max() == null ||
                         newComboDuration.max().compareTo(currentMove.duration().max()) <
                         0))
                        newComboDuration = new ComboMoveDuration(newComboDuration.min(),
                                currentMove.duration().max());
                }
            }
            boolean preparationComplete =
                    matchingMoveCount == combo.sequence().moves().size();
            ComboMove comboLastMove = combo.sequence().moves().isEmpty() ? null :
                    combo.sequence().moves().getLast();
            boolean lastMoveIsWaitingMove = comboLastMove != null &&
                                        !comboLastMove.duration().min().equals(Duration.ZERO);
            if (event != null) {
                if (partOfComboSequence)
                    // This processingByCombo does not need to have entries about
                    // non-combo sequences (i.e. combo preconditions).
                    // That is because preconditions are managed by the caller (keyEvent)
                    // which checks across all modes, not just the current one. (isComboPreconditionKey)
                    processingByCombo.put(combo,
                            PressKeyEventProcessing.partOfComboSequence(mustBeEaten,
                                    preparationComplete && !lastMoveIsWaitingMove));
            }
            if (!preparationComplete)
                continue;
            List<Command> commands = entry.getValue();
            if (ignoreSwitchModeCommands &&
                commands.stream().anyMatch(Command.SwitchMode.class::isInstance)) {
                logger.debug(
                        "Ignoring the following SwitchMode commands since the mode was just changed to " +
                        currentMode.name() + ": " + commands.stream()
                                                            .filter(Command.SwitchMode.class::isInstance)
                                                            .toList());
                commands = commands.stream()
                                   .filter(Predicate.not(
                                           Command.SwitchMode.class::isInstance))
                                   .toList();
            }
            ComboAndCommands comboAndCommands = new ComboAndCommands(combo, commands);
            if (lastMoveIsWaitingMove) {
                combosWaitingForLastMoveToComplete.add(
                        new ComboWaitingForLastMoveToComplete(comboAndCommands,
                                comboLastMove.duration().min().toNanos() / 1e9d));
            }
            else {
                comboAndCommandsToRun.add(comboAndCommands);
            }
        }
        if (newComboDuration != null)
            previousComboMoveDuration = newComboDuration;
        List<Command> commandsToRun =
                longestComboCommandsLastAndDeduplicate(comboAndCommandsToRun);
        PressKeyEventProcessingSet processingSet =
                new PressKeyEventProcessingSet(processingByCombo);
        logger.debug("currentMode = " + currentMode.name() +
                     ", currentlyPressedComboPreconditionKeys = " + currentlyPressedComboPreconditionKeys +
                     ", comboPreparation = " + comboPreparation +
                     ", partOfComboSequence = " + processingSet.isPartOfComboSequence() +
                     ", mustBeEaten = " + processingSet.mustBeEaten() + ", commandsToRun = " +
                     commandsToRun);
        commandsToRun.forEach(commandRunner::run);
        if (event != null && event.isPress()) {
            if (processingSet.isPartOfComboSequence()) {
                for (ComboAndCommands comboAndCommands : comboAndCommandsToRun) {
                    Combo combo = comboAndCommands.combo;
                    addCurrentlyPressedCompletedComboSequenceKeys(combo);
                }
                if (processingSet.isPartOfCompletedComboSequence())
                    currentlyPressedCompletedComboSequenceKeys.add(event.key());
            }
        }
        return processingSet;
    }

    private void addCurrentlyPressedCompletedComboSequenceKeys(Combo combo) {
        Set<Key> keys = combo.keysPressedInComboPriorToMoveOfIndex(
                combo.sequence().moves().size() - 1);
        // logger.info("Combo completed, pressed keys: " + keys);
        currentlyPressedCompletedComboSequenceKeys.addAll(keys);
    }

    private static final List<? extends Class<? extends Command>> commandOrder =
            List.of(
                    Command.SwitchMode.class
            );

    /**
     * Assuming the following configuration:
     * - +up: start move up
     * - -up|+up -up +up: stop move up
     * - +up -up +up: start wheel up
     * When up is pressed, the move starts. Then, when up is released then pressed,
     * the wheel starts.
     * The 3 combos are completed, but we ultimately want the move to stop,
     * i.e. the stop move command (+up -up +up) should be run after the
     * start move command (+up).
     * Longest combos "have the last word".
     * Also deduplicate commands: if start-move-up is +up|#rightctrl +up: holding rightctrl
     * then up should not trigger two commands.
     * - Move the Switch commands last: useful for saving a mouse position then switching to position-history mode
     */
    private List<Command> longestComboCommandsLastAndDeduplicate(List<ComboAndCommands> commandsToRun) {
        return commandsToRun.stream()
                            .sorted(Comparator.comparing(ComboAndCommands::combo,
                                    Comparator.comparing(Combo::sequence,
                                            Comparator.comparing(ComboSequence::moves,
                                                    Comparator.comparingInt(
                                                            List::size)))))
                            .map(ComboAndCommands::commands)
                            .flatMap(Collection::stream)
                            .distinct()
                            .sorted(Comparator.comparingInt(command ->
                                    commandOrder.indexOf(command.getClass())))
                            .toList();
    }

    public void breakComboPreparation() {
        logger.debug("Breaking combos, comboPreparation = " + comboPreparation +
                     ", combosWaitingForLastMoveToComplete = " +
                     combosWaitingForLastMoveToComplete);
        comboPreparation = ComboPreparation.empty();
        combosWaitingForLastMoveToComplete.clear();
    }

    public void reset() {
        breakComboPreparation();
        // When a mode times out to a new mode, the currentlyPressedComboKeys should not be reset.
        currentlyPressedCompletedComboSequenceKeys.clear();
        currentlyPressedComboPreconditionKeys.clear();
    }

    @Override
    public void modeChanged(Mode newMode) {
        currentMode = newMode;
        if (modeJustTimedOut) {
            modeJustTimedOut = false;
            processKeyEventForCurrentMode(null, false);
        }
    }

    @Override
    public void modeTimedOut() {
        modeJustTimedOut = true;
        breakComboPreparation();
    }

     private static final class ComboWaitingForLastMoveToComplete {
        private final ComboAndCommands comboAndCommands;
        private double remainingWait;

        private ComboWaitingForLastMoveToComplete(ComboAndCommands comboAndCommands,
                                                  double remainingWait) {
            this.comboAndCommands = comboAndCommands;
            this.remainingWait = remainingWait;
        }

        public ComboAndCommands comboAndCommands() {
            return comboAndCommands;
        }

        @Override
        public String toString() {
            return "ComboWaitingForLastMoveToComplete[" + "comboAndCommands=" +
                   comboAndCommands + ", remainingWait=" + remainingWait + ']';
        }
    }

    private record ComboAndCommands(Combo combo, List<Command> commands) {
    }

}
