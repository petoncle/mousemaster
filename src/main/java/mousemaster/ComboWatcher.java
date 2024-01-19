package mousemaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;

public class ComboWatcher implements ModeListener {

    private static final Logger logger = LoggerFactory.getLogger(ComboWatcher.class);

    private final CommandRunner commandRunner;
    private final Set<Key> mustRemainPressedComboPreconditionKeys;
    private final Set<Key> mustRemainUnpressedComboPreconditionKeys;
    private Mode currentMode;
    private boolean modeJustTimedOut;
    private ComboPreparation comboPreparation;
    private ComboMoveDuration previousComboMoveDuration;
    private List<ComboWaitingForLastMoveToComplete> combosWaitingForLastMoveToComplete = new ArrayList<>();

    private Set<Key> currentlyPressedComboSequenceKeys = new HashSet<>();
    private Set<Key> currentlyPressedComboPreconditionKeys = new HashSet<>();

    public ComboWatcher(CommandRunner commandRunner,
                        Set<Key> mustRemainUnpressedComboPreconditionKeys,
                        Set<Key> mustRemainPressedComboPreconditionKeys) {
        this.commandRunner = commandRunner;
        this.mustRemainUnpressedComboPreconditionKeys =
                mustRemainUnpressedComboPreconditionKeys;
        this.mustRemainPressedComboPreconditionKeys =
                mustRemainPressedComboPreconditionKeys;
        this.comboPreparation = ComboPreparation.empty();
    }

    public void update(double delta) {
        // For a given waiting combo, we know that its precondition has to be satisfied still, because otherwise it
        // would mean that currentlyPressedComboPreconditionKeys has changed. But when currentlyPressedComboPreconditionKeys is changed,
        // combosWaitingForLastMoveToComplete is always reset.
        List<ComboWaitingForLastMoveToComplete> completeCombos = new ArrayList<>();
        for (ComboWaitingForLastMoveToComplete comboWaitingForLastMoveToComplete : combosWaitingForLastMoveToComplete) {
            comboWaitingForLastMoveToComplete.remainingWait -= delta;
            if (comboWaitingForLastMoveToComplete.remainingWait < 0)
                completeCombos.add(comboWaitingForLastMoveToComplete);
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
            processKeyEventForCurrentMode(null, true);
        }
    }

    public PressKeyEventProcessing keyEvent(KeyEvent event) {
        modeJustTimedOut = false;
        boolean isComboPreconditionKey =
                mustRemainUnpressedComboPreconditionKeys.contains(event.key()) ||
                mustRemainPressedComboPreconditionKeys.contains(event.key());
        if (event.isRelease()) {
            // The corresponding press event was either part of a combo sequence or part of a combo precondition,
            // otherwise this method would not have been called.
            currentlyPressedComboSequenceKeys.remove(event.key());
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
        PressKeyEventProcessing processing = processKeyEventForCurrentMode(event, false);
        boolean partOfComboSequence = processing.isPartOfComboSequence();
        boolean mustBeEaten = processing.mustBeEaten();
        if (currentMode != beforeMode) {
            // Second pass to give a chance to new mode's combos to run now.
            processing = processKeyEventForCurrentMode(event, true);
            partOfComboSequence |= processing.isPartOfComboSequence();
            mustBeEaten |= processing.mustBeEaten();
        }
        if (event.isRelease())
            return null;
        if (partOfComboSequence)
            return PressKeyEventProcessing.partOfComboSequence(mustBeEaten);
        boolean partOfComboPreconditionOnly = isComboPreconditionKey;
        return partOfComboPreconditionOnly ?
                PressKeyEventProcessing.partOfComboPreconditionOnly() :
                PressKeyEventProcessing.unhandled();
    }

    private PressKeyEventProcessing processKeyEventForCurrentMode(KeyEvent event,
                                                                  boolean ignoreSwitchModeCommands) {
        boolean mustBeEaten = false;
        boolean partOfComboSequence = false;
        List<ComboAndCommands> comboAndCommandsToRun = new ArrayList<>();
        Set<Key> currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten =
                new HashSet<>(currentlyPressedComboPreconditionKeys);
        currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten.removeIf(
                Predicate.not(mustRemainPressedComboPreconditionKeys::contains));
        currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten.removeAll(
                currentlyPressedComboSequenceKeys);
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
            int matchingMoveCount = comboPreparation.matchingMoveCount(combo.sequence());
            ComboMove currentMove = matchingMoveCount == 0 ? null :
                    combo.sequence().moves().get(matchingMoveCount - 1);
            // releaseCombo == the combo is not just a mustRemainUnpressed combo (it has a sequence or a mustRemainPressed precondition)
            boolean releaseCombo =
                    combo.precondition().mustRemainPressedKeySets().isEmpty() &&
                    (combo.sequence().moves().isEmpty() || combo.sequence()
                                                                .moves()
                                                                .stream()
                                                                .allMatch(
                                                                        ComboMove::isRelease)); // This condition (check sequence is all releases)
            // could be removed to not execute combos that have a sequence and whose mustRemainPress condition is not satisfied.
            if (!currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten.isEmpty()
                // If currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten is not part of the combo's mustRemainPressedKeySets...
                && combo.precondition()
                        .mustRemainPressedKeySets()
                        .stream()
                        .noneMatch(Predicate.isEqual(
                                currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten)) &&
                // ...and the combo is not a release combo
                !releaseCombo &&
                // ...and the combo's current move is not a press of that currentlyPressedComboPreconditionKey...
                (currentMove == null ||
                 !currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten.equals(
                         Set.of(currentMove.key())))) {
                // Then it's as if the currently pressed precondition key is an unhandled key:
                // other keys that are pressed should not even be considered but passed onto other apps.
                // logger.info("currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten = " +
                //             currentlyPressedMustRemainPressedComboPreconditionKeysNotAlreadyEaten +
                //             ", skipping combo: " + combo);
                continue;
            }
            if (matchingMoveCount == 0) {
                if (combo.sequence().moves().isEmpty() &&
                    !combo.precondition().isEmpty()) {
                    if (!combo.precondition().satisfied(
                            currentlyPressedComboPreconditionKeys)) {
                        continue;
                    }
                }
            }
            else {
                if (!combo.precondition().isEmpty()) {
                    if (!combo.precondition().satisfied(
                            currentlyPressedComboPreconditionKeys))
                        continue;
                }
                boolean currentMoveMustBeEaten =
                        currentMove instanceof ComboMove.PressComboMove pressComboMove &&
                        pressComboMove.eventMustBeEaten();
                mustBeEaten |= currentMoveMustBeEaten;
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
            ComboMove comboLastMove = combo.sequence().moves().isEmpty() ? null :
                    combo.sequence().moves().getLast();
            if (comboLastMove != null &&
                !comboLastMove.duration().min().equals(Duration.ZERO)) {
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
        logger.debug("currentMode = " + currentMode.name() +
                     ", currentlyPressedComboPreconditionKeys = " + currentlyPressedComboPreconditionKeys +
                     ", comboPreparation = " + comboPreparation +
                     ", partOfComboSequence = " + partOfComboSequence +
                     ", mustBeEaten = " + mustBeEaten + ", commandsToRun = " +
                     commandsToRun);
        commandsToRun.forEach(commandRunner::run);
        if (!partOfComboSequence) {
            comboPreparation = ComboPreparation.empty();
        }
        if (event != null && event.isPress()) {
            if (partOfComboSequence)
                currentlyPressedComboSequenceKeys.add(event.key());
        }
        if (partOfComboSequence)
            return PressKeyEventProcessing.partOfComboSequence(mustBeEaten);
        return PressKeyEventProcessing.unhandled();
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
        currentlyPressedComboSequenceKeys.clear();
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
