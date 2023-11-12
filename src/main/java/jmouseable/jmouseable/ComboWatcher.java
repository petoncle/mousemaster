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

    public ComboWatcher(ModeManager modeManager, CommandRunner commandRunner) {
        this.modeManager = modeManager;
        this.commandRunner = commandRunner;
        this.comboPreparation = ComboPreparation.empty();
    }

    public KeyEventProcessing keyEvent(KeyEvent event) {
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
            commandsToRun.addAll(entry.getValue());
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

}
