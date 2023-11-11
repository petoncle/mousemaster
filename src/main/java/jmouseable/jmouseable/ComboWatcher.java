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
    private final Duration defaultComboBreakingTimeout;
    private ComboPreparation comboPreparation;
    private Duration currentComboBreakingTimeout;

    public ComboWatcher(ModeManager modeManager, CommandRunner commandRunner,
                        Duration defaultComboBreakingTimeout) {
        this.modeManager = modeManager;
        this.commandRunner = commandRunner;
        this.defaultComboBreakingTimeout = defaultComboBreakingTimeout;
        this.comboPreparation = ComboPreparation.empty();
        this.currentComboBreakingTimeout = defaultComboBreakingTimeout;
    }

    public KeyEventProcessing keyEvent(KeyEvent event) {
        KeyEvent previousEvent = comboPreparation.events().isEmpty() ? null :
                comboPreparation.events().getLast();
        if (previousEvent != null && previousEvent.time()
                                                  .isBefore(event.time()
                                                                 .minus(currentComboBreakingTimeout))) {
            comboPreparation = ComboPreparation.empty();
            currentComboBreakingTimeout = defaultComboBreakingTimeout;
        }
        comboPreparation.events().add(event);
        boolean mustBeEaten = false;
        boolean partOfCombo = false;
        List<Command> commandsToRun = new ArrayList<>();
        Mode currentMode = modeManager.currentMode();
        for (Map.Entry<Combo, List<Command>> entry : currentMode.comboMap()
                                                                .commandsByCombo()
                                                                .entrySet()) {
            Combo combo = entry.getKey();
            int matchingMoveCount = comboPreparation.matchingMoveCount(combo);
            if (matchingMoveCount != 0) {
                ComboMove move = combo.moves().get(matchingMoveCount - 1);
                mustBeEaten |= move.eventMustBeEaten();
                partOfCombo = true;
                currentComboBreakingTimeout =
                        move.breakingTimeout() != null ? move.breakingTimeout() :
                                defaultComboBreakingTimeout;
            }
            boolean preparationComplete = matchingMoveCount == combo.moves().size();
            if (!preparationComplete)
                continue;
            commandsToRun.addAll(entry.getValue());
        }
        logger.debug(
                "currentMode = " + currentMode.name() + ", comboPreparationActions = " +
                comboPreparation.events().stream().map(KeyEvent::action).toList() +
                ", partOfCombo = " + partOfCombo + ", mustBeEaten = " + mustBeEaten +
                ", commandsToRun = " + commandsToRun);
        commandsToRun.forEach(commandRunner::run);
        return new KeyEventProcessing(partOfCombo, mustBeEaten);
    }

}
