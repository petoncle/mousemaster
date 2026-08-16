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
            // A swell rises over the duration of the indicator it enters, then falls over
            // the duration of the one it left.
            if (newIndicator.transitionAnimationOvershoot() != 1)
                transitionDuration +=
                        transitionFromIndicator.transitionAnimationDuration().toMillis() / 1000d;
        }
        currentIndicator = newIndicator;
        overlay.setIndicator(transitioned(newIndicator), allowFade,
                !currentMode.hideCursor().enabled());
    }

    private IndicatorConfiguration transitioned(IndicatorConfiguration indicator) {
        if (transitionElapsed >= transitionDuration)
            return indicator;
        return swollen(IndicatorConfiguration.lerp(transitionFromIndicator, indicator,
                indicator.transitionAnimationEasing()
                         .apply(transitionElapsed / transitionDuration)), indicator);
    }

    /** Sizes the indicator past the size it is easing to, up to the overshoot, so that two
     *  indicators of the same size still pulse. Clamped, so that clicking repeatedly does
     *  not pile one swell onto the previous one. */
    private IndicatorConfiguration swollen(IndicatorConfiguration eased,
                                           IndicatorConfiguration indicator) {
        double overshoot = indicator.transitionAnimationOvershoot();
        if (overshoot == 1)
            return eased;
        double t = transitionElapsed / transitionDuration;
        double peak = indicator.transitionAnimationDuration().toMillis() / 1000d /
                      transitionDuration;
        boolean rising = t < peak;
        Easing easing = rising ? indicator.transitionAnimationEasing()
                               : transitionFromIndicator.transitionAnimationEasing();
        double swell = 1 + (overshoot - 1) *
                           easing.apply(rising ? t / peak : (1 - t) / (1 - peak));
        return eased.builder()
                    .size((int) Math.min(Math.round(eased.size() * swell),
                            Math.round(indicator.size() * overshoot)))
                    .build();
    }

    @Override
    public void modeTimedOut() {
        // Ignored.
    }
}
