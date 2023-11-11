package jmouseable.jmouseable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static jmouseable.jmouseable.Command.*;

public class ComboWatcher {

    private static final Logger logger = LoggerFactory.getLogger(ComboWatcher.class);

    private final ModeMap modeMap;
    private final MouseMover mouseMover;
    private Mode currentMode;
    private ComboPreparation comboPreparation;

    public ComboWatcher(ModeMap modeMap, MouseMover mouseMover) {
        this.modeMap = modeMap;
        this.mouseMover = mouseMover;
        this.comboPreparation = ComboPreparation.empty();
        changeMode(modeMap.get(Mode.DEFAULT_MODE_NAME));
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
            List<Command> commandsToRun = new ArrayList<>();
            for (Map.Entry<Combo, List<Command>> entry : currentMode.comboMap()
                                                                    .commandsByCombo()
                                                                    .entrySet()) {
                Combo combo = entry.getKey();
                int matchingMoveCount = comboPreparation.matchingMoveCount(combo);
                if (matchingMoveCount != 0) {
                    mustBeEaten |=
                            combo.moves().get(matchingMoveCount - 1).eventMustBeEaten();
                    partOfCombo = true;
                }
                boolean preparationComplete = matchingMoveCount == combo.moves().size();
                if (!preparationComplete)
                    continue;
            commandsToRun.addAll(entry.getValue());
            }
        logger.debug("comboPreparationActions = " +
                     comboPreparation.events().stream().map(KeyEvent::action).toList() +
                     ", partOfCombo = " + partOfCombo + ", mustBeEaten = " + mustBeEaten +
                     ", commandsToRun = " + commandsToRun);
            commandsToRun.forEach(this::run);
        return new KeyEventProcessing(partOfCombo, mustBeEaten);
    }

    private void run(Command command) {
        switch (command) {
            // @formatter:off
            case ChangeMode changeMode -> changeMode(modeMap.get(changeMode.newModeName()));

            case StartMoveUp startMoveUp -> mouseMover.startMoveUp();
            case StartMoveDown startMoveDown -> mouseMover.startMoveDown();
            case StartMoveLeft startMoveLeft -> mouseMover.startMoveLeft();
            case StartMoveRight startMoveRight -> mouseMover.startMoveRight();

            case StopMoveUp stopMoveUp -> mouseMover.stopMoveUp();
            case StopMoveDown stopMoveDown -> mouseMover.stopMoveDown();
            case StopMoveLeft stopMoveLeft -> mouseMover.stopMoveLeft();
            case StopMoveRight stopMoveRight -> mouseMover.stopMoveRight();

            case PressLeft pressLeft -> {}
            case PressMiddle pressMiddle -> {}
            case PressRight pressRight -> {}

            case ReleaseLeft releaseLeft -> {}
            case ReleaseMiddle releaseMiddle -> {}
            case ReleaseRight releaseRight -> {}
            // @formatter:on
        }
    }

    private void changeMode(Mode newMode) {
        currentMode = newMode;
        mouseMover.changeMouse(newMode.mouse());
    }

}
