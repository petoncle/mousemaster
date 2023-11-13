package jmouseable.jmouseable;

public class Ticker {

    private final ModeManager modeManager;
    private final MouseMover mouseMover;
    private final ComboWatcher comboWatcher;

    public Ticker(ModeManager modeManager, MouseMover mouseMover, ComboWatcher comboWatcher) {
        this.modeManager = modeManager;
        this.mouseMover = mouseMover;
        this.comboWatcher = comboWatcher;
    }

    public void update(double delta) {
        modeManager.update(delta);
        mouseMover.update(delta);
        comboWatcher.update(delta);
    }

}
