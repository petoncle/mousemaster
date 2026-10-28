package mousemaster;

import static mousemaster.Command.*;

public class CommandRunner {

    private ModeController modeController;
    private final MouseManager mouseManager;
    private final HintManager hintManager;
    private final Platform platform;
    private MacroPlayer macroPlayer;
    private AnimationPlayer animationPlayer;

    public CommandRunner(MouseManager mouseManager, HintManager hintManager,
                         Platform platform) {
        this.mouseManager = mouseManager;
        this.hintManager = hintManager;
        this.platform = platform;
    }

    public void setModeController(ModeController modeController) {
        this.modeController = modeController;
    }

    public void setMacroPlayer(MacroPlayer macroPlayer) {
        this.macroPlayer = macroPlayer;
    }

    public void setAnimationPlayer(AnimationPlayer animationPlayer) {
        this.animationPlayer = animationPlayer;
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

            case SnapUp snapUp -> hintManager.snapUp();
            case SnapDown snapDown -> hintManager.snapDown();
            case SnapLeft snapLeft -> hintManager.snapLeft();
            case SnapRight snapRight -> hintManager.snapRight();

            case ShrinkGridUp shrinkGridUp -> hintManager.shrinkGridUp();
            case ShrinkGridDown shrinkGridDown -> hintManager.shrinkGridDown();
            case ShrinkGridLeft shrinkGridLeft -> hintManager.shrinkGridLeft();
            case ShrinkGridRight shrinkGridRight -> hintManager.shrinkGridRight();

            case MoveGridUp moveGridUp -> hintManager.moveGridUp();
            case MoveGridDown moveGridDown -> hintManager.moveGridDown();
            case MoveGridLeft moveGridLeft -> hintManager.moveGridLeft();
            case MoveGridRight moveGridRight -> hintManager.moveGridRight();

            case MoveToGridCenter moveToGridCenter -> hintManager.moveToGridCenter();

            case MoveToLastSelectedHint moveToLastSelectedHint -> hintManager.moveToLastSelectedHint();

            case MoveToSelection moveToSelection -> hintManager.moveToSelection();

            case SavePosition(String positionHistoryName) -> hintManager.saveCurrentPosition(positionHistoryName);
            case UnsavePosition(String positionHistoryName) -> hintManager.unsaveCurrentPosition(positionHistoryName);
            case ClearPositionHistory(String positionHistoryName) -> hintManager.clearPositionHistory(positionHistoryName);
            case CycleNextPosition(String positionHistoryName) -> hintManager.cyclePosition(positionHistoryName, 1);
            case CyclePreviousPosition(String positionHistoryName) -> hintManager.cyclePosition(positionHistoryName, -1);

            case StartAnimation(String animationName) -> animationPlayer.start(animationName);
            case StopAnimation(String animationName) -> animationPlayer.stop(animationName);

            case MacroCommand(Macro macro, AliasResolution aliasResolution) ->
                    macroPlayer.submit(macro.resolve(aliasResolution));

            // Complex command that is manually handled by ComboWatcher and KeyManager.
            case BreakComboPreparation breakComboPreparation -> {}

            // Handled by ComboWatcher directly (it holds the mode state for mutations).
            case MutateMode mutateMode -> {}

            // Handled by ComboWatcher directly (it holds the variable state).
            case SetVariable setVariable -> {}
            case UnsetVariable unsetVariable -> {}
            case ResetVariables resetVariables -> {}

            case BreakMacro breakMacro -> macroPlayer.breakMacro();

            case Exit exit -> platform.requestKillProcess();
            case Restart restart -> platform.requestRestartProcess();

            case Noop noop -> {}

            case SelectHintKey selectHintKey -> hintManager.selectHintKey(eventKey);
            case DeselectHintKey deselectHintKey -> hintManager.deselectHintKey();
            // @formatter:on
        }
    }
}
