package mousemaster;

import static mousemaster.Command.*;

public class CommandRunner {

    private ModeController modeController;
    private final MouseManager mouseManager;
    private final GridManager gridManager;
    private final HintManager hintManager;
    private MacroPlayer macroPlayer;

    public CommandRunner(MouseManager mouseManager, GridManager gridManager,
                         HintManager hintManager) {
        this.mouseManager = mouseManager;
        this.gridManager = gridManager;
        this.hintManager = hintManager;
    }

    public void setModeController(ModeController modeController) {
        this.modeController = modeController;
    }

    public void setMacroPlayer(MacroPlayer macroPlayer) {
        this.macroPlayer = macroPlayer;
    }

    public boolean runningAtomicCommand() {
        return mouseManager.jumping();
    }

    public void run(Command command, Key eventKey) {
        switch (command) {
            // @formatter:off
            case SwitchMode switchMode -> modeController.switchMode(switchMode.modeName());

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

            case ToggleLeft toggleLeft -> mouseManager.toggleLeft();
            case ToggleMiddle toggleMiddle -> mouseManager.toggleMiddle();
            case ToggleRight toggleRight -> mouseManager.toggleRight();

            case StartWheelUp startWheelUp -> mouseManager.startWheelUp();
            case StartWheelDown startWheelDown -> mouseManager.startWheelDown();
            case StartWheelLeft startWheelLeft -> mouseManager.startWheelLeft();
            case StartWheelRight startWheelRight -> mouseManager.startWheelRight();

            case StopWheelUp stopWheelUp -> mouseManager.stopWheelUp();
            case StopWheelDown stopWheelDown -> mouseManager.stopWheelDown();
            case StopWheelLeft stopWheelLeft -> mouseManager.stopWheelLeft();
            case StopWheelRight stopWheelRight -> mouseManager.stopWheelRight();

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

            case MoveToLastSelectedHint moveToLastSelectedHint -> hintManager.moveToLastSelectedHint();

            case SavePosition savePosition -> hintManager.saveCurrentPosition();
            case UnsavePosition unsavePosition -> hintManager.unsaveCurrentPosition();
            case ClearPositionHistory clearPositionHistory -> hintManager.clearPositionHistory();
            case CycleNextPosition cycleNextPosition -> hintManager.cycleNextPosition();
            case CyclePreviousPosition cyclePreviousPosition -> hintManager.cyclePreviousPosition();

            case MacroCommand(Macro macro, AliasResolution aliasResolution) ->
                    macroPlayer.submit(macro.resolve(aliasResolution));

            // Complex command that is manually handled by ComboWatcher and KeyManager.
            case BreakComboPreparation breakComboPreparation -> {}

            // Handled by ComboWatcher directly (it holds the mode state for mutations).
            case MutateMode mutateMode -> {}

            // Handled by ComboWatcher directly (it holds the variable state).
            case SetVariable setVariable -> {}
            case UnsetVariable unsetVariable -> {}
            case ClearVariables clearVariables -> {}

            case BreakMacro breakMacro -> macroPlayer.breakMacro();

            case Noop noop -> {}

            case SelectHintKey selectHintKey -> hintManager.selectHintKey(eventKey);
            case UnselectHintKey unselectHintKey -> hintManager.unselectHintKey();
            // @formatter:on
        }
    }
}
