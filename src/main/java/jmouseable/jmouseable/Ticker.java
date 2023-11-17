package jmouseable.jmouseable;

public class Ticker {

    private final ModeManager modeManager;
    private final MouseManager mouseManager;
    private final KeyboardManager keyboardManager;
    private final IndicatorManager indicatorManager;

    public Ticker(ModeManager modeManager, MouseManager mouseManager,
                  KeyboardManager keyboardManager, IndicatorManager indicatorManager) {
        this.modeManager = modeManager;
        this.mouseManager = mouseManager;
        this.keyboardManager = keyboardManager;
        this.indicatorManager = indicatorManager;
    }

    public void update(double delta) {
        modeManager.update(delta);
        mouseManager.update(delta);
        keyboardManager.update(delta);
        indicatorManager.update(delta);
    }

}
