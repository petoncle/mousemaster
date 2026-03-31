package mousemaster;

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
            Indicator indicator = activeIndicator();
            if (indicator.hexColor() == null)
                WindowsOverlay.hideIndicator();
            else
                WindowsOverlay.setIndicator(indicator);
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

    private Indicator activeIndicator() {
        IndicatorConfiguration config = currentMode.indicator();
        if (keyboardState.pressingUnhandledKeyInCurrentMode() &&
            config.unhandledKeyPressIndicator().hexColor() != null)
            return config.unhandledKeyPressIndicator();
        if (mouseState.leftPressing() && config.leftMousePressIndicator().hexColor() != null)
            return config.leftMousePressIndicator();
        if (mouseState.middlePressing() && config.middleMousePressIndicator().hexColor() != null)
            return config.middleMousePressIndicator();
        if (mouseState.rightPressing() && config.rightMousePressIndicator().hexColor() != null)
            return config.rightMousePressIndicator();
        if (mouseState.wheeling() && config.wheelIndicator().hexColor() != null)
            return config.wheelIndicator();
        if (mouseState.moving() && config.moveIndicator().hexColor() != null)
            return config.moveIndicator();
        return config.idleIndicator();
    }
}
