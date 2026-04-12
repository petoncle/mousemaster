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
        updateIndicator(true);
    }

    @Override
    public void modeChanged(Mode newMode) {
        // Skip the fade animation when the zoom is about to change.
        boolean allowFade = currentMode == null ||
                            currentMode.zoom().equals(newMode.zoom());
        currentMode = newMode;
        updateIndicator(allowFade);
    }

    private void updateIndicator(boolean allowFade) {
        if (currentMode.indicator().enabled()) {
            Indicator indicator = activeIndicator();
            if (indicator.hexColor() == null)
                WindowsOverlay.hideIndicator(allowFade);
            else
                WindowsOverlay.setIndicator(indicator,
                        currentMode.indicator().fadeAnimationEnabled(),
                        currentMode.indicator().fadeAnimationDuration(),
                        allowFade);
        }
        else
            WindowsOverlay.hideIndicator(allowFade);
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
