package jmouseable.jmouseable;

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
            case Command.ChangeMode changeMode -> modeManager.changeMode(changeMode.newModeName());

            case Command.StartMoveUp startMoveUp -> mouseMover.startMoveUp();
            case Command.StartMoveDown startMoveDown -> mouseMover.startMoveDown();
            case Command.StartMoveLeft startMoveLeft -> mouseMover.startMoveLeft();
            case Command.StartMoveRight startMoveRight -> mouseMover.startMoveRight();

            case Command.StopMoveUp stopMoveUp -> mouseMover.stopMoveUp();
            case Command.StopMoveDown stopMoveDown -> mouseMover.stopMoveDown();
            case Command.StopMoveLeft stopMoveLeft -> mouseMover.stopMoveLeft();
            case Command.StopMoveRight stopMoveRight -> mouseMover.stopMoveRight();

            case Command.PressLeft pressLeft -> {}
            case Command.PressMiddle pressMiddle -> {}
            case Command.PressRight pressRight -> {}

            case Command.ReleaseLeft releaseLeft -> {}
            case Command.ReleaseMiddle releaseMiddle -> {}
            case Command.ReleaseRight releaseRight -> {}
            // @formatter:on
        }
    }

}
