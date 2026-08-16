package mousemaster;

import mousemaster.platform.Overlay;

public class IndicatorManager implements ModeListener {

    private final Overlay overlay;
    private Mode currentMode;
    private IndicatorConfiguration currentIndicator;
    private IndicatorConfiguration transitionFromIndicator;
    private double transitionElapsed;
    private double transitionDuration;
    private IndicatorConfiguration swellFromIndicator;
    private IndicatorConfiguration swellIndicator;
    private double swellElapsed;
    private double swellDuration;

    public IndicatorManager(Overlay overlay) {
        this.overlay = overlay;
    }

    public void update(double delta) {
        if (transitionElapsed < transitionDuration)
            transitionElapsed += delta;
        if (swellElapsed < swellDuration)
            swellElapsed += delta;
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
            transitionFromIndicator = eased(currentIndicator);
            transitionElapsed = 0;
            transitionDuration =
                    newIndicator.transitionAnimationDuration().toMillis() / 1000d;
            if (newIndicator.transitionAnimationOvershoot() != 1) {
                swellFromIndicator = transitionFromIndicator;
                swellIndicator = newIndicator;
                swellElapsed = 0;
                swellDuration = transitionDuration + transitionFromIndicator
                        .transitionAnimationDuration().toMillis() / 1000d;
            }
        }
        currentIndicator = newIndicator;
        overlay.setIndicator(swollen(eased(newIndicator)), allowFade,
                !currentMode.hideCursor().enabled());
    }

    private IndicatorConfiguration eased(IndicatorConfiguration indicator) {
        if (transitionElapsed >= transitionDuration)
            return indicator;
        return IndicatorConfiguration.lerp(transitionFromIndicator, indicator,
                indicator.transitionAnimationEasing()
                         .apply(transitionElapsed / transitionDuration));
    }

    /** Sizes the indicator past the size it is easing to, up to the overshoot, so that two
     *  indicators of the same size still pulse. The swell rises over the duration of the
     *  indicator it enters and falls over the duration of the one it left, on its own clock:
     *  a press shorter than the rise still pulses. */
    private IndicatorConfiguration swollen(IndicatorConfiguration eased) {
        if (swellElapsed >= swellDuration)
            return eased;
        double rise = swellIndicator.transitionAnimationDuration().toMillis() / 1000d;
        boolean rising = swellElapsed < rise;
        Easing easing = rising ? swellIndicator.transitionAnimationEasing()
                               : swellFromIndicator.transitionAnimationEasing();
        double swell = 1 + (swellIndicator.transitionAnimationOvershoot() - 1) *
                           easing.apply(rising ? swellElapsed / rise :
                                   (swellDuration - swellElapsed) / (swellDuration - rise));
        return eased.builder()
                    .size((int) Math.round(eased.size() * swell))
                    .build();
    }

    @Override
    public void modeTimedOut() {
        // Ignored.
    }
}
