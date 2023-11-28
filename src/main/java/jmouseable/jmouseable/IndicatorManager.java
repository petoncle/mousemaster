package jmouseable.jmouseable;

public class IndicatorManager implements ModeListener {

    private final MouseState mouseState;
    private final KeyboardState keyboardState;
    private Mode currentMode;

    public IndicatorManager(MouseState mouseState, KeyboardState keyboardState) {
        this.mouseState = mouseState;
        this.keyboardState = keyboardState;
    }

    public void update(double delta) {
        if (currentMode.indicator().enabled()) {
            String indicatorHexColor = indicatorHexColor();
            if (indicatorHexColor == null)
                WindowsOverlay.hideIndicator();
            else
                WindowsOverlay.setIndicatorColor(indicatorHexColor);
        }
        else
            WindowsOverlay.hideIndicator();
    }

    @Override
    public void modeChanged(Mode newMode) {
        currentMode = newMode;
    }

    @Override
    public void modeTimedOut() {
        // Ignored.
    }

    private String indicatorHexColor() {
        Indicator indicator = currentMode.indicator();
        if (keyboardState.pressingNonComboKey() &&
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
}
