package jmouseable.jmouseable;

public class Ticker {

    private final ModeManager modeManager;
    private final MouseMover mouseMover;

    public Ticker(ModeManager modeManager, MouseMover mouseMover) {
        this.modeManager = modeManager;
        this.mouseMover = mouseMover;
    }

    public void update(double delta) {
        modeManager.update(delta);
        mouseMover.update(delta);
    }

}
