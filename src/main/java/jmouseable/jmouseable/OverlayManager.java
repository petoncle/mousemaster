package jmouseable.jmouseable;

public class OverlayManager implements ModeListener {

    private final MouseState mouseState;
    private final KeyboardManager keyboardManager;
    private Mode currentMode;
    private double enforceTopmostTimer;

    public OverlayManager(MouseState mouseState,
                          KeyboardManager keyboardManager) {
        this.mouseState = mouseState;
        this.keyboardManager = keyboardManager;
    }

    public void update(double delta) {
        if (currentMode.indicator().enabled())
            WindowsOverlay.setIndicatorColor(indicatorHexColor());
        else
            WindowsOverlay.hideIndicator();
        if (currentMode.gridConfiguration().visible())
            WindowsOverlay.setGrid(currentMode.gridConfiguration());
        else
            WindowsOverlay.hideGrid();
        enforceTopmostTimer -= delta;
        if (enforceTopmostTimer < 0) {
            // Every 200ms.
            enforceTopmostTimer = 0.2;
            WindowsOverlay.setTopmost();
        }
    }

    private String indicatorHexColor() {
        Indicator indicator = currentMode.indicator();
        if (keyboardManager.pressingNonHandledKey() &&
            indicator.nonComboKeyPressHexColor() != null)
            return indicator.nonComboKeyPressHexColor();
        if (mouseState.pressing() && indicator.mousePressHexColor() != null)
            return indicator.mousePressHexColor();
        if (mouseState.wheeling() && indicator.wheelHexColor() != null)
            return indicator.wheelHexColor();
        if (mouseState.moving() && indicator.moveHexColor() != null)
            return indicator.moveHexColor();
        return indicator.idleHexColor();
    }

    @Override
    public void modeChanged(Mode newMode) {
        currentMode = newMode;
    }

    @Override
    public void modeTimedOut() {
        // Ignored.
    }
}
