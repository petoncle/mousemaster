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
    private final PlatformClock clock;
    private final Set<Key> pressedComboPreconditionKeys;
    private final boolean logRedactKeys;
    private final Set<Key> unpressedComboPreconditionKeys;
    private List<ComboListener> listeners;
    private Mode currentMode;
    private boolean modeJustTimedOut;
    private ComboPreparation comboPreparation;
    private PressKeyEventProcessingSet lastProcessingSet;
    private ComboMoveDuration previousComboMoveDuration;
    private List<ComboWaitingForLastMoveToComplete> combosWaitingForLastMoveToComplete = new ArrayList<>();
    private List<Command> commandsWaitingForAtomicCommandToComplete = new ArrayList<>();

    private Set<Key> currentlyPressedCompletedComboSequenceKeys = new HashSet<>();
    private Set<Key> currentlyPressedComboKeys = new HashSet<>();

    public ComboWatcher(CommandRunner commandRunner, ActiveAppFinder activeAppFinder,
                        PlatformClock clock,
                        Set<Key> unpressedComboPreconditionKeys,
                        Set<Key> pressedComboPreconditionKeys, boolean logRedactKeys) {
        this.commandRunner = commandRunner;
        this.activeAppFinder = activeAppFinder;
        this.clock = clock;
        this.unpressedComboPreconditionKeys =
                unpressedComboPreconditionKeys;
        this.pressedComboPreconditionKeys =
                pressedComboPreconditionKeys;
        this.logRedactKeys = logRedactKeys;
        this.comboPreparation = ComboPreparation.empty();
    }

    public void setListeners(List<ComboListener> listeners) {
        this.listeners = listeners;
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
            Instant currentTime = clock.now();
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
                                                currentTime)) {
                            preparationIsStillPrefixOfAtLeastOneCombo = true;
                        }
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
        if (!completeCombos.isEmpty()) {
            listeners.forEach(ComboListener::completedCombo);
        }
        List<Command> commandsToRun = new ArrayList<>(commandsWaitingForAtomicCommandToComplete);
        commandsWaitingForAtomicCommandToComplete.clear();
        List<Command> completeCombosCommands = longestComboCommandsLastAndDeduplicate(completeCombos.stream()
                                                                               .map(ComboWaitingForLastMoveToComplete::comboAndCommands)
                                                                               .toList());
        commandsToRun.addAll(completeCombosCommands);
        if (!completeCombosCommands.isEmpty()) {
            logger.debug(
                    "Completed combos that were waiting for last move to complete, currentMode = " +
                    currentMode.name() + ", completeCombos = " + completeCombos.stream()
                                                                               .map(ComboWaitingForLastMoveToComplete::comboAndCommands)
                                                                               .map(ComboAndCommands::combo)
                                                                               .toList() +
                    ", commandsToRun = " + commandsToRun);
        }
        Mode beforeMode = currentMode;
        runCommands(commandsToRun);
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
        boolean isUnpressedComboPreconditionKey =
                unpressedComboPreconditionKeys.contains(event.key());
        boolean isPressedComboPreconditionKey =
                pressedComboPreconditionKeys.contains(event.key());
        boolean isComboPreconditionKey =
                isUnpressedComboPreconditionKey ||
                isPressedComboPreconditionKey;
        if (event.isRelease()) {
            // The corresponding press event was either part of a combo sequence or part of a combo precondition,
            // otherwise this method would not have been called.
            currentlyPressedCompletedComboSequenceKeys.remove(event.key());
            currentlyPressedComboKeys.remove(event.key());
        }
        else {
            if (isComboPreconditionKey) {
                currentlyPressedComboKeys.add(event.key());
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
                                isPressedComboPreconditionKey ?
                                        PressKeyEventProcessing.partOfPressedComboPreconditionOnly() :
                                        PressKeyEventProcessing.partOfUnpressedComboPreconditionOnly())));
            }
        }
        else {
            if (event.isPress())
                currentlyPressedComboKeys.add(event.key());
        }
        lastProcessingSet = processingSet;
        return processingSet;
    }

    private PressKeyEventProcessingSet processKeyEventForCurrentMode(
            KeyEvent event,
            boolean ignoreSwitchModeCommands) {
//        logger.info("currentMode = " + currentMode.name() + ", processKeyEventForCurrentMode event " + event + ", ignoreSwitchModeCommands = " + ignoreSwitchModeCommands, new Throwable());
        Map<Combo, PressKeyEventProcessing> processingByCombo = new HashMap<>();
        List<ComboAndCommands> comboAndCommandsToRun = new ArrayList<>();
        Set<Key> currentlyPressedKeys = new HashSet<>(currentlyPressedComboKeys);
        if (event != null && event.isPress())
            currentlyPressedKeys.add(event.key());
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
            boolean releaseCombo =
                    combo.precondition()
                         .keyPrecondition()
                         .pressedKeySets()
                         .isEmpty() &&
                    (combo.sequence().moves().isEmpty() || combo.sequence()
                                                                .moves()
                                                                .stream()
                                                                .allMatch(
                                                                        ComboMove::isRelease));
            // For release combos (like -up), we ignore the currently pressed keys:
            // even if there is a pressed key that is not in the combo precondition, we
            // still consider the combo as completed.
            if (!releaseCombo &&
                !comboPressedPreconditionSatisfied(combo, currentlyPressedKeys,
                        currentlyPressedCompletedComboSequenceKeys, matchingMoveCount)) {
                // Then it's as if the currently pressed precondition key is an unhandled key:
                // other keys that are pressed should not even be considered but passed onto other apps.
                // logger.info("currentlyPressedPressedComboKeysNotPartOfAlreadyCompletedCombo = " +
                //             currentlyPressedPressedComboKeysNotPartOfAlreadyCompletedCombo +
                //             ", skipping combo: " + combo);
                continue;
            }
            if (!comboUnpressedPreconditionSatisfied(combo, currentlyPressedComboKeys, matchingMoveCount)) {
                continue;
            }
            boolean mustBeEaten = false;
            boolean partOfComboSequence = false;
            if (matchingMoveCount != 0) {
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
                        "Ignoring the following SwitchMode commands because the mode was just changed to " +
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
        List<Command> commandsToRun = new ArrayList<>(commandsWaitingForAtomicCommandToComplete);
        commandsWaitingForAtomicCommandToComplete.clear();
        List<Command> completeCombosCommandsToRun =
                longestComboCommandsLastAndDeduplicate(comboAndCommandsToRun);
        commandsToRun.addAll(completeCombosCommandsToRun);
        PressKeyEventProcessingSet processingSet =
                new PressKeyEventProcessingSet(processingByCombo);
        logger.debug("currentMode = " + currentMode.name() +
                     ", currentlyPressedComboKeys = " + (logRedactKeys ? "<redacted>" : currentlyPressedComboKeys) +
                     ", comboPreparation = " +
                     (logRedactKeys ? "<redacted>" : comboPreparation.toString()) +
                     ", partOfComboSequence = " + processingSet.isPartOfComboSequence() +
                     ", mustBeEaten = " + processingSet.mustBeEaten() + ", commandsToRun = " +
                     completeCombosCommandsToRun);
        if (!comboAndCommandsToRun.isEmpty()) {
            listeners.forEach(ComboListener::completedCombo);
        }
        runCommands(commandsToRun);
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

    private void runCommands(List<Command> commandsToRun) {
        List<Command> commands = new ArrayList<>(commandsToRun);
        while (!commands.isEmpty() && !commandRunner.runningAtomicCommand()) {
            Command command = commands.removeFirst();
            commandRunner.run(command);
        }
        commandsWaitingForAtomicCommandToComplete.addAll(commands);
    }

    private boolean comboPressedPreconditionSatisfied(Combo combo,
                                                      Set<Key> currentlyPressedKeys,
                                                      Set<Key> currentlyPressedCompletedComboSequenceKeys,
                                                      int matchingMoveCount) {
        Set<Set<Key>> preconditionPressedKeySets = combo.precondition()
                                                        .keyPrecondition()
                                                        .pressedKeySets();
        if (preconditionPressedKeySets.isEmpty())
            preconditionPressedKeySets = Set.of(Set.of());
        for (Set<Key> preconditionPressedKeySet : preconditionPressedKeySets) {
            Set<Key> keysPressedInComboPriorToMove =
                    combo.keysPressedInComboPriorToMoveOfIndex(preconditionPressedKeySet,
                            matchingMoveCount - 1);
            Set<Key> currentlyPressedKeysNotPartOfAlreadyCompletedComboSequence =
                    new HashSet<>(currentlyPressedKeys);
            for (Key currentlyPressedCompletedComboSequenceKey : currentlyPressedCompletedComboSequenceKeys) {
                if (!keysPressedInComboPriorToMove.contains(
                        currentlyPressedCompletedComboSequenceKey))
                    currentlyPressedKeysNotPartOfAlreadyCompletedComboSequence.remove(
                            currentlyPressedCompletedComboSequenceKey);
            }
            if (currentlyPressedKeysNotPartOfAlreadyCompletedComboSequence.equals(
                    keysPressedInComboPriorToMove))
                return true;
        }
        return false;
    }

    private boolean comboUnpressedPreconditionSatisfied(Combo combo,
                                                        Set<Key> currentlyPressedComboKeys,
                                                        int matchingMoveCount) {
        for (Key unpressedPreconditionKey : combo.precondition()
                                                 .keyPrecondition()
                                                 .unpressedKeySet()) {
            if (currentlyPressedComboKeys.contains(unpressedPreconditionKey) &&
                !combo.keysPressedInComboPriorToMoveOfIndex(Set.of(),
                        matchingMoveCount - 1).contains(unpressedPreconditionKey))
                return false;
        }
        return true;
    }

        private void addCurrentlyPressedCompletedComboSequenceKeys(Combo combo) {
        Set<Key> keys = combo.keysPressedInComboPriorToMoveOfIndex(
                Set.of(), combo.sequence().moves().size() - 1);
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
        logger.debug("Breaking combos, comboPreparation = " +
                     (logRedactKeys ? "<redacted>" : comboPreparation.toString()) +
                     ", combosWaitingForLastMoveToComplete = " +
                     combosWaitingForLastMoveToComplete);
        comboPreparation = ComboPreparation.empty();
        combosWaitingForLastMoveToComplete.clear();
    }

    public void reset() {
        breakComboPreparation();
        // When a mode times out to a new mode, the currentlyPressedComboKeys should not be reset.
        currentlyPressedCompletedComboSequenceKeys.clear();
        currentlyPressedComboKeys.clear();
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
