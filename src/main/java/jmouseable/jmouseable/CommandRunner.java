package jmouseable.jmouseable;

import static jmouseable.jmouseable.Command.*;

public class CommandRunner {

    private ModeController modeController;
    private final MouseController mouseController;
    private final GridManager gridManager;
    private final HintManager hintManager;

    public CommandRunner(MouseController mouseController, GridManager gridManager,
                         HintManager hintManager) {
        this.mouseController = mouseController;
        this.gridManager = gridManager;
        this.hintManager = hintManager;
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

            case ShrinkGridTop shrinkGridTop -> gridManager.shrinkGridTop();
            case ShrinkGridBottom shrinkGridBottom -> gridManager.shrinkGridBottom();
            case ShrinkGridLeft shrinkGridLeft -> gridManager.shrinkGridLeft();
            case ShrinkGridRight shrinkGridRight -> gridManager.shrinkGridRight();

            case MoveGridTop moveGridTop -> gridManager.moveGridTop();
            case MoveGridBottom moveGridBottom -> gridManager.moveGridBottom();
            case MoveGridLeft moveGridLeft -> gridManager.moveGridLeft();
            case MoveGridRight moveGridRight -> gridManager.moveGridRight();

            case MoveToGridCenter moveToGridCenter -> gridManager.moveToGridCenter();

            case SaveMousePosition saveMousePosition -> hintManager.saveMousePosition();
            case ClearMousePositionHistory clearMousePositionHistory -> hintManager.clearMousePositionHistory();
            // @formatter:on
        }
    }
}
