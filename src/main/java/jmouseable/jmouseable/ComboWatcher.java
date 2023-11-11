package jmouseable.jmouseable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static jmouseable.jmouseable.Command.*;

public class ComboWatcher {

    private static final Logger logger = LoggerFactory.getLogger(ComboWatcher.class);

    private final ModeMap modeMap;
    private Mode currentMode;
    private ComboPreparation comboPreparation;

    public ComboWatcher(ModeMap modeMap) {
        this.modeMap = modeMap;
        this.currentMode = modeMap.get(Mode.DEFAULT_MODE_NAME);
        this.comboPreparation = ComboPreparation.empty();
    }

    public KeyEventProcessing keyEvent(KeyEvent event) {
        KeyEvent previousEvent = comboPreparation.events().isEmpty() ? null :
                comboPreparation.events().getLast();
        if (previousEvent != null &&
            previousEvent.time().isBefore(event.time().minusMillis(150))) {
            comboPreparation = ComboPreparation.empty();
        }
        comboPreparation.events().add(event);
        boolean mustBeEaten = false;
        boolean partOfCombo = false;
        for (Map.Entry<Combo, List<Command>> entry : currentMode.comboMap()
                                                                .commandsByCombo()
                                                                .entrySet()) {
            Combo combo = entry.getKey();
            int matchingMoveCount = comboPreparation.matchingMoveCount(combo);
            if (matchingMoveCount != 0) {
                mustBeEaten |= combo.moves().get(matchingMoveCount - 1).eventMustBeEaten();
                partOfCombo = true;
            }
            boolean preparationComplete = matchingMoveCount == combo.moves().size();
            if (!preparationComplete)
                continue;
            List<Command> commands = entry.getValue();
            commands.forEach(this::run);
        }
        logger.debug("comboPreparationActions = " +
                     comboPreparation.events().stream().map(KeyEvent::action).toList() +
                     ", partOfCombo = " + partOfCombo + ", mustBeEaten = " + mustBeEaten);
        return new KeyEventProcessing(partOfCombo, mustBeEaten);
    }

    private void run(Command command) {
        logger.debug(command.getClass().getSimpleName());
        switch (command) {
            case ChangeMode changeMode -> currentMode = modeMap.get(changeMode.newModeName());
            case PressLeft pressLeft -> {}
            case PressMiddle pressMiddle -> {}
            case PressRight pressRight -> {}
            case ReleaseLeft releaseLeft -> {}
            case ReleaseMiddle releaseMiddle -> {}
            case ReleaseRight releaseRight -> {}
            case StartMoveDown startMoveDown -> {}
            case StartMoveLeft startMoveLeft -> {}
            case StartMoveRight startMoveRight -> {}
            case StartMoveUp startMoveUp -> {}
            case StopMoveDown stopMoveDown -> {}
            case StopMoveLeft stopMoveLeft -> {}
            case StopMoveRight stopMoveRight -> {}
            case StopMoveUp stopMoveUp -> {}
        }
    }

}
