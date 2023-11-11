package jmouseable.jmouseable;

import static jmouseable.jmouseable.Command.*;

public class CommandRunner {

    private final ModeManager modeManager;
    private final MouseMover mouseMover;

    public CommandRunner(ModeManager modeManager, MouseMover mouseMover) {
        this.modeManager = modeManager;
        this.mouseMover = mouseMover;
    }

    public void run(Command command) {
        switch (command) {
            // @formatter:off
            case ChangeMode changeMode -> modeManager.changeMode(changeMode.newModeName());

            case StartMoveUp startMoveUp -> mouseMover.startMoveUp();
            case StartMoveDown startMoveDown -> mouseMover.startMoveDown();
            case StartMoveLeft startMoveLeft -> mouseMover.startMoveLeft();
            case StartMoveRight startMoveRight -> mouseMover.startMoveRight();

            case StopMoveUp stopMoveUp -> mouseMover.stopMoveUp();
            case StopMoveDown stopMoveDown -> mouseMover.stopMoveDown();
            case StopMoveLeft stopMoveLeft -> mouseMover.stopMoveLeft();
            case StopMoveRight stopMoveRight -> mouseMover.stopMoveRight();

            case PressLeft pressLeft -> mouseMover.pressLeft();
            case PressMiddle pressMiddle -> mouseMover.pressMiddle();
            case PressRight pressRight -> mouseMover.pressRight();

            case ReleaseLeft releaseLeft -> mouseMover.releaseLeft();
            case ReleaseMiddle releaseMiddle -> mouseMover.releaseMiddle();
            case ReleaseRight releaseRight -> mouseMover.releaseRight();

            case StartWheelUp startWheelUp -> mouseMover.startWheelUp();
            case StartWheelDown startWheelDown -> mouseMover.startWheelDown();
            case StartWheelLeft startWheelLeft -> mouseMover.startWheelLeft();
            case StartWheelRight startWheelRight -> mouseMover.startWheelRight();

            case StopWheelUp stopWheelUp -> mouseMover.stopWheelUp();
            case StopWheelDown stopWheelDown -> mouseMover.stopWheelDown();
            case StopWheelLeft stopWheelLeft -> mouseMover.stopWheelLeft();
            case StopWheelRight stopWheelRight -> mouseMover.stopWheelRight();
            // @formatter:on
        }
    }

}
