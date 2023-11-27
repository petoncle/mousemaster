package jmouseable.jmouseable;

import static jmouseable.jmouseable.Command.*;

public class CommandRunner {

    private ModeController modeController;
    private final MouseController mouseController;
    private final GridManager gridManager;

    public CommandRunner(MouseController mouseController, GridManager gridManager) {
        this.mouseController = mouseController;
        this.gridManager = gridManager;
    }

    public void setModeController(ModeController modeController) {
        this.modeController = modeController;
    }

    public void run(Command command) {
        switch (command) {
            // @formatter:off
            case SwitchMode switchMode -> modeController.switchMode(switchMode.modeName());

            case StartMoveUp startMoveUp -> mouseController.startMoveUp();
            case StartMoveDown startMoveDown -> mouseController.startMoveDown();
            case StartMoveLeft startMoveLeft -> mouseController.startMoveLeft();
            case StartMoveRight startMoveRight -> mouseController.startMoveRight();

            case StopMoveUp stopMoveUp -> mouseController.stopMoveUp();
            case StopMoveDown stopMoveDown -> mouseController.stopMoveDown();
            case StopMoveLeft stopMoveLeft -> mouseController.stopMoveLeft();
            case StopMoveRight stopMoveRight -> mouseController.stopMoveRight();

            case PressLeft pressLeft -> mouseController.pressLeft();
            case PressMiddle pressMiddle -> mouseController.pressMiddle();
            case PressRight pressRight -> mouseController.pressRight();

            case ReleaseLeft releaseLeft -> mouseController.releaseLeft();
            case ReleaseMiddle releaseMiddle -> mouseController.releaseMiddle();
            case ReleaseRight releaseRight -> mouseController.releaseRight();

            case StartWheelUp startWheelUp -> mouseController.startWheelUp();
            case StartWheelDown startWheelDown -> mouseController.startWheelDown();
            case StartWheelLeft startWheelLeft -> mouseController.startWheelLeft();
            case StartWheelRight startWheelRight -> mouseController.startWheelRight();

            case StopWheelUp stopWheelUp -> mouseController.stopWheelUp();
            case StopWheelDown stopWheelDown -> mouseController.stopWheelDown();
            case StopWheelLeft stopWheelLeft -> mouseController.stopWheelLeft();
            case StopWheelRight stopWheelRight -> mouseController.stopWheelRight();

            case SnapUp snapUp -> gridManager.snapUp();
            case SnapDown snapDown -> gridManager.snapDown();
            case SnapLeft snapLeft -> gridManager.snapLeft();
            case SnapRight snapRight -> gridManager.snapRight();

            case CutGridBottom cutGridBottom -> gridManager.cutGridBottom();
            case CutGridLeft cutGridLeft -> gridManager.cutGridLeft();
            case CutGridRight cutGridRight -> gridManager.cutGridRight();
            case CutGridTop cutGridTop -> gridManager.cutGridTop();
            // @formatter:on
        }
    }
}
