package mousemaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;

public class ComboWatcher implements ModeListener {

    private static final Logger logger = LoggerFactory.getLogger(ComboWatcher.class);

    private final CommandRunner commandRunner;
    private final Set<Key> comboPreconditionKeys;
    private Mode currentMode;
    private ComboPreparation comboPreparation;
    private ComboMoveDuration previousComboMoveDuration;
    private List<ComboWaitingForLastMoveToComplete> combosWaitingForLastMoveToComplete = new ArrayList<>();

    private Set<Key> currentlyPressedComboSequenceKeys = new HashSet<>();
    private Set<Key> currentlyPressedComboPreconditionKeys = new HashSet<>();

    public ComboWatcher(CommandRunner commandRunner, Set<Key> comboPreconditionKeys) {
        this.commandRunner = commandRunner;
        this.comboPreconditionKeys = comboPreconditionKeys;
        this.comboPreparation = ComboPreparation.empty();
    }

    public void update(double delta) {
        // Cancel combo if precondition is not satisfied anymore
        combosWaitingForLastMoveToComplete.removeIf(
                comboWaitingForLastMoveToComplete -> !comboWaitingForLastMoveToComplete.comboAndCommands.combo.precondition()
                                                                                                              .satisfied(
                                                                                                                      currentlyPressedComboPreconditionKeys));
        List<ComboWaitingForLastMoveToComplete> completeCombos = new ArrayList<>();
        for (ComboWaitingForLastMoveToComplete comboWaitingForLastMoveToComplete : combosWaitingForLastMoveToComplete) {
            comboWaitingForLastMoveToComplete.remainingWait -= delta;
            if (comboWaitingForLastMoveToComplete.remainingWait < 0)
                completeCombos.add(comboWaitingForLastMoveToComplete);
        }
        List<Command> commandsToRun = longestComboCommandsLastAndDeduplicate(completeCombos.stream()
                                                                                           .map(ComboWaitingForLastMoveToComplete::comboAndCommands)
                                                                                           .toList());
        commandsToRun.forEach(commandRunner::run);
        combosWaitingForLastMoveToComplete.removeAll(completeCombos);
    }

    public PressKeyEventProcessing keyEvent(KeyEvent event) {
        boolean isComboPreconditionKey = comboPreconditionKeys.contains(event.key());
        if (event.isRelease()) {
            // The corresponding press event was either part of a combo sequence or part of a combo precondition,
            // otherwise this method would not have been called.
            boolean isPartOfComboSequence = currentlyPressedComboSequenceKeys.remove(event.key());
            if (currentlyPressedComboPreconditionKeys.remove(event.key()) &&
                !isPartOfComboSequence)
                return null;
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
        ComboMoveDuration newComboDuration = null;
        for (Map.Entry<Combo, List<Command>> entry : currentMode.comboMap()
                                                                .commandsByCombo()
                                                                .entrySet()) {
            Combo combo = entry.getKey();
            int matchingMoveCount = comboPreparation.matchingMoveCount(combo.sequence());
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
                ComboMove currentMove =
                        combo.sequence().moves().get(matchingMoveCount - 1);
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
        logger.debug("currentMode = " + currentMode.name() + ", comboPreparation = " +
                     comboPreparation +
                     ", partOfComboSequence = " + partOfComboSequence +
                     ", mustBeEaten = " + mustBeEaten + ", commandsToRun = " +
                     commandsToRun);
        commandsToRun.forEach(commandRunner::run);
        if (!partOfComboSequence) {
            comboPreparation = ComboPreparation.empty();
        }
        if (event.isPress()) {
            if (partOfComboSequence)
                currentlyPressedComboSequenceKeys.add(event.key());
        }
        if (partOfComboSequence)
            return PressKeyEventProcessing.partOfComboSequence(mustBeEaten);
        return PressKeyEventProcessing.unhandled();
    }

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
     * Also move the Switch commands last: useful for saving a mouse position then switching to position-history mode
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
                            .sorted(Comparator.comparingInt(
                                    command -> command instanceof Command.SwitchMode ? 1 :
                                            0))
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
    }

    @Override
    public void modeTimedOut() {
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
