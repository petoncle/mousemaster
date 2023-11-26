package jmouseable.jmouseable;

import static jmouseable.jmouseable.Command.*;

public class CommandRunner {

    private final ModeManager modeManager;
    private final MouseManager mouseManager;

    public CommandRunner(ModeManager modeManager, MouseManager mouseManager) {
        this.modeManager = modeManager;
        this.mouseManager = mouseManager;
    }

    public void run(Command command) {
        switch (command) {
            // @formatter:off
            case SwitchMode switchMode -> modeManager.switchMode(switchMode.modeName());

            case StartMoveUp startMoveUp -> mouseManager.startMoveUp();
            case StartMoveDown startMoveDown -> mouseManager.startMoveDown();
            case StartMoveLeft startMoveLeft -> mouseManager.startMoveLeft();
            case StartMoveRight startMoveRight -> mouseManager.startMoveRight();

            case StopMoveUp stopMoveUp -> mouseManager.stopMoveUp();
            case StopMoveDown stopMoveDown -> mouseManager.stopMoveDown();
            case StopMoveLeft stopMoveLeft -> mouseManager.stopMoveLeft();
            case StopMoveRight stopMoveRight -> mouseManager.stopMoveRight();

            case PressLeft pressLeft -> mouseManager.pressLeft();
            case PressMiddle pressMiddle -> mouseManager.pressMiddle();
            case PressRight pressRight -> mouseManager.pressRight();

            case ReleaseLeft releaseLeft -> mouseManager.releaseLeft();
            case ReleaseMiddle releaseMiddle -> mouseManager.releaseMiddle();
            case ReleaseRight releaseRight -> mouseManager.releaseRight();

            case StartWheelUp startWheelUp -> mouseManager.startWheelUp();
            case StartWheelDown startWheelDown -> mouseManager.startWheelDown();
            case StartWheelLeft startWheelLeft -> mouseManager.startWheelLeft();
            case StartWheelRight startWheelRight -> mouseManager.startWheelRight();

            case StopWheelUp stopWheelUp -> mouseManager.stopWheelUp();
            case StopWheelDown stopWheelDown -> mouseManager.stopWheelDown();
            case StopWheelLeft stopWheelLeft -> mouseManager.stopWheelLeft();
            case StopWheelRight stopWheelRight -> mouseManager.stopWheelRight();

            case SnapUp snapUp -> mouseManager.snapUp();
            case SnapDown snapDown -> mouseManager.snapDown();
            case SnapLeft snapLeft -> mouseManager.snapLeft();
            case SnapRight snapRight -> mouseManager.snapRight();
            // @formatter:on
        }
    }

}
