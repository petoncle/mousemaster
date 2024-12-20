package mousemaster;

import static mousemaster.Command.*;

public class CommandRunner {

    private ModeController modeController;
    private final MouseController mouseController;
    private final GridManager gridManager;
    private final HintManager hintManager;
    private final Remapper remapper;

    public CommandRunner(MouseController mouseController, GridManager gridManager,
                         HintManager hintManager, Remapper remapper) {
        this.mouseController = mouseController;
        this.gridManager = gridManager;
        this.hintManager = hintManager;
        this.remapper = remapper;
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

            case ToggleLeft toggleLeft -> mouseController.toggleLeft();
            case ToggleMiddle toggleMiddle -> mouseController.toggleMiddle();
            case ToggleRight toggleRight -> mouseController.toggleRight();

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

            case ShrinkGridUp shrinkGridUp -> gridManager.shrinkGridUp();
            case ShrinkGridDown shrinkGridDown -> gridManager.shrinkGridDown();
            case ShrinkGridLeft shrinkGridLeft -> gridManager.shrinkGridLeft();
            case ShrinkGridRight shrinkGridRight -> gridManager.shrinkGridRight();

            case MoveGridUp moveGridUp -> gridManager.moveGridUp();
            case MoveGridDown moveGridDown -> gridManager.moveGridDown();
            case MoveGridLeft moveGridLeft -> gridManager.moveGridLeft();
            case MoveGridRight moveGridRight -> gridManager.moveGridRight();

            case MoveToGridCenter moveToGridCenter -> gridManager.moveToGridCenter();

            case SavePosition savePosition -> hintManager.saveCurrentPosition();
            case ClearPositionHistory clearPositionHistory -> hintManager.clearPositionHistory();
            case CycleNextPosition cycleNextPosition -> hintManager.cycleNextPosition();
            case CyclePreviousPosition cyclePreviousPosition -> hintManager.cyclePreviousPosition();

            case RemapCommand remap -> remapper.submitRemapping(remap.remapping());
            // @formatter:on
        }
    }
}
