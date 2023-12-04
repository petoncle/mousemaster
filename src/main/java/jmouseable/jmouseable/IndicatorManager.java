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
                WindowsOverlay.setIndicator(
                        new Indicator(currentMode.indicator().size(), indicatorHexColor));
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
        IndicatorConfiguration indicatorConfiguration = currentMode.indicator();
        if (keyboardState.pressingNonComboKey() &&
            indicatorConfiguration.nonComboKeyPressHexColor() != null)
            return indicatorConfiguration.nonComboKeyPressHexColor();
        if (mouseState.pressing() && indicatorConfiguration.mousePressHexColor() != null)
            return indicatorConfiguration.mousePressHexColor();
        if (mouseState.wheeling() && indicatorConfiguration.wheelHexColor() != null)
            return indicatorConfiguration.wheelHexColor();
        if (mouseState.moving() && indicatorConfiguration.moveHexColor() != null)
            return indicatorConfiguration.moveHexColor();
        return indicatorConfiguration.idleHexColor();
    }
}
