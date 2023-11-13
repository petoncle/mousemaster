package jmouseable.jmouseable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ComboWatcher {

    private static final Logger logger = LoggerFactory.getLogger(ComboWatcher.class);

    private final ModeManager modeManager;
    private final CommandRunner commandRunner;
    private ComboPreparation comboPreparation;
    private ComboMoveDuration previousComboMoveDuration;
    private List<ComboWaitingForLastMoveToComplete> combosWaitingForLastMoveToComplete = new ArrayList<>();

    public ComboWatcher(ModeManager modeManager, CommandRunner commandRunner) {
        this.modeManager = modeManager;
        this.commandRunner = commandRunner;
        this.comboPreparation = ComboPreparation.empty();
    }

    public void update(double delta) {
        List<ComboWaitingForLastMoveToComplete> completeCombos = new ArrayList<>();
        for (ComboWaitingForLastMoveToComplete comboWaitingForLastMoveToComplete : combosWaitingForLastMoveToComplete) {
            comboWaitingForLastMoveToComplete.remainingWait -= delta;
            if (comboWaitingForLastMoveToComplete.remainingWait < 0)
                completeCombos.add(comboWaitingForLastMoveToComplete);
        }
        for (ComboWaitingForLastMoveToComplete completeCombo : completeCombos)
            completeCombo.commands.forEach(commandRunner::run);
        combosWaitingForLastMoveToComplete.removeAll(completeCombos);
    }

    public KeyEventProcessing keyEvent(KeyEvent event) {
        if (!combosWaitingForLastMoveToComplete.isEmpty())
            combosWaitingForLastMoveToComplete.clear();
        KeyEvent previousEvent = comboPreparation.events().isEmpty() ? null :
                comboPreparation.events().getLast();
        if (previousEvent != null &&
            !previousComboMoveDuration.isRespected(previousEvent.time(), event.time()))
            comboPreparation = ComboPreparation.empty();
        comboPreparation.events().add(event);
        boolean mustBeEaten = false;
        boolean partOfCombo = false;
        List<Command> commandsToRun = new ArrayList<>();
        Mode currentMode = modeManager.currentMode();
        ComboMoveDuration newComboDuration = null;
        for (Map.Entry<Combo, List<Command>> entry : currentMode.comboMap()
                                                                .commandsByCombo()
                                                                .entrySet()) {
            Combo combo = entry.getKey();
            int matchingMoveCount = comboPreparation.matchingMoveCount(combo);
            if (matchingMoveCount != 0) {
                ComboMove currentMove = combo.moves().get(matchingMoveCount - 1);
                mustBeEaten |= currentMove.eventMustBeEaten();
                partOfCombo = true;
                if (newComboDuration == null) {
                    newComboDuration = currentMove.duration();
                }
                else {
                    if (newComboDuration.min().compareTo(currentMove.duration().min()) >
                        0)
                        newComboDuration =
                                new ComboMoveDuration(currentMove.duration().min(),
                                        newComboDuration.max());
                    if (newComboDuration.max().compareTo(currentMove.duration().max()) <
                        0)
                        newComboDuration = new ComboMoveDuration(newComboDuration.min(),
                                currentMove.duration().max());
                }
            }
            boolean preparationComplete = matchingMoveCount == combo.moves().size();
            if (!preparationComplete)
                continue;
            List<Command> commands = entry.getValue();
            ComboMove comboLastMove = combo.moves().getLast();
            if (!comboLastMove.duration().min().equals(Duration.ZERO)) {
                combosWaitingForLastMoveToComplete.add(new ComboWaitingForLastMoveToComplete(combo, commands, comboLastMove.duration().min().toNanos() / 1e9d));
            }
            else {
                commandsToRun.addAll(commands);
            }
        }
        if (newComboDuration != null)
            previousComboMoveDuration = newComboDuration;
        logger.debug(
                "currentMode = " + currentMode.name() + ", comboPreparationActions = " +
                comboPreparation.events().stream().map(KeyEvent::action).toList() +
                ", partOfCombo = " + partOfCombo + ", mustBeEaten = " + mustBeEaten +
                ", commandsToRun = " + commandsToRun);
        if (!partOfCombo)
            comboPreparation = ComboPreparation.empty();
        commandsToRun.forEach(commandRunner::run);
        return new KeyEventProcessing(partOfCombo, mustBeEaten);
    }

    public void interrupt() {
        comboPreparation = ComboPreparation.empty();
        combosWaitingForLastMoveToComplete.clear();
    }

    private static final class ComboWaitingForLastMoveToComplete {
        private final Combo combo;
        private final List<Command> commands;
        private double remainingWait;

        private ComboWaitingForLastMoveToComplete(Combo combo, List<Command> commands, double remainingWait) {
            this.combo = combo;
            this.commands = commands;
            this.remainingWait = remainingWait;
        }

     }

}
