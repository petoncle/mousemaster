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
    private Duration previousComboMoveMaxDuration;

    public ComboWatcher(ModeManager modeManager, CommandRunner commandRunner) {
        this.modeManager = modeManager;
        this.commandRunner = commandRunner;
        this.comboPreparation = ComboPreparation.empty();
    }

    public KeyEventProcessing keyEvent(KeyEvent event) {
        KeyEvent previousEvent = comboPreparation.events().isEmpty() ? null :
                comboPreparation.events().getLast();
        if (previousEvent != null &&
            previousEvent.time().plus(previousComboMoveMaxDuration).isBefore(event.time()))
            comboPreparation = ComboPreparation.empty();
        comboPreparation.events().add(event);
        boolean mustBeEaten = false;
        boolean partOfCombo = false;
        List<Command> commandsToRun = new ArrayList<>();
        Mode currentMode = modeManager.currentMode();
        Duration newComboMaxDuration = null;
        for (Map.Entry<Combo, List<Command>> entry : currentMode.comboMap()
                                                                .commandsByCombo()
                                                                .entrySet()) {
            Combo combo = entry.getKey();
            int matchingMoveCount = comboPreparation.matchingMoveCount(combo);
            if (matchingMoveCount != 0) {
                ComboMove currentMove = combo.moves().get(matchingMoveCount - 1);
                mustBeEaten |= currentMove.eventMustBeEaten();
                partOfCombo = true;
                if (newComboMaxDuration == null ||
                    newComboMaxDuration.compareTo(currentMove.duration().max()) < 0)
                    newComboMaxDuration = currentMove.duration().max();
            }
            boolean preparationComplete = matchingMoveCount == combo.moves().size();
            if (!preparationComplete)
                continue;
            commandsToRun.addAll(entry.getValue());
        }
        if (newComboMaxDuration != null)
            previousComboMoveMaxDuration = newComboMaxDuration;
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

}
