package mousemaster;

import mousemaster.platform.Overlay;

public class IndicatorManager implements ModeListener {

    private final Overlay overlay;
    private Mode currentMode;
    private IndicatorConfiguration currentIndicator;
    private IndicatorConfiguration transitionFromIndicator;
    private double transitionElapsed;
    private double transitionDuration;

    public IndicatorManager(Overlay overlay) {
        this.overlay = overlay;
    }

    public void update(double delta) {
        if (transitionElapsed < transitionDuration)
            transitionElapsed += delta;
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
        IndicatorConfiguration newIndicator = currentMode.indicator();
        if (!newIndicator.enabled()) {
            currentIndicator = null;
            overlay.hideIndicator(allowFade);
            return;
        }
        if (currentIndicator != null && !currentIndicator.equals(newIndicator)) {
            transitionFromIndicator = transitioned(currentIndicator);
            transitionElapsed = 0;
            transitionDuration =
                    newIndicator.transitionAnimationDuration().toMillis() / 1000d;
        }
        currentIndicator = newIndicator;
        overlay.setIndicator(transitioned(newIndicator), allowFade,
                !currentMode.hideCursor().enabled());
    }

    private IndicatorConfiguration transitioned(IndicatorConfiguration indicator) {
        if (transitionElapsed >= transitionDuration)
            return indicator;
        return IndicatorConfiguration.lerp(transitionFromIndicator, indicator,
                indicator.transitionAnimationEasing()
                         .apply(transitionElapsed / transitionDuration));
    }

    @Override
    public void modeTimedOut() {
        // Ignored.
    }
}
