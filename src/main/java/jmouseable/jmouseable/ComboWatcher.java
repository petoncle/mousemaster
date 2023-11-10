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

    /**
     * @return true if the event should be eaten.
     */
    public boolean keyEvent(KeyEvent event) {
        KeyEvent previousEvent = comboPreparation.events().isEmpty() ? null :
                comboPreparation.events().getLast();
        if (previousEvent != null &&
            previousEvent.time().isBefore(event.time().minusMillis(150))) {
            logger.debug("Timeout: resetting combo preparation");
            comboPreparation = ComboPreparation.empty();
        }
        comboPreparation.events().add(event);
        logger.debug("comboPreparationActions = " +
                    comboPreparation.events().stream().map(KeyEvent::action).toList());
        boolean eat = false;
        for (Map.Entry<Combo, List<Command>> entry : currentMode.comboMap()
                                                                .commandsByCombo()
                                                                .entrySet()) {
            Combo combo = entry.getKey();
            int matchingMoveCount = comboPreparation.matchingMoveCount(combo);
            if (matchingMoveCount != 0)
                eat |= combo.moves().get(matchingMoveCount - 1).eventMustBeEaten();
            if (matchingMoveCount != combo.moves().size())
                continue;
            logger.debug("Preparation matches combo " + combo);
            List<Command> commands = entry.getValue();
            commands.forEach(this::run);
        }
        logger.debug("Eating: " + eat);
        return eat;
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
