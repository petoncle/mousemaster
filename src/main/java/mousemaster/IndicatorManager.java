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
    private double swellFromScale;

    public IndicatorManager(Overlay overlay) {
        this.overlay = overlay;
    }

    public boolean animating() {
        return transitionElapsed < transitionDuration || swellElapsed < swellDuration;
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
            // Start from what is on screen, with the colors currentIndicator was switching to.
            // Without them, a mode switching faster than a transition lasts keeps the first.
            transitionFromIndicator = eased(currentIndicator).switching(currentIndicator);
            transitionElapsed = 0;
            transitionDuration =
                    newIndicator.transitionAnimationDuration().toMillis() / 1000d;
            if (newIndicator.transitionAnimationOvershoot() != 1) {
                // A click while the indicator is still swollen swells on from the scale it
                // has, instead of collapsing to its resting size for a frame.
                swellFromScale = swell();
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
        if (transitionElapsed >= transitionDuration && swellElapsed >= swellDuration)
            return indicator;
        // What switches at the end waits for the swell too: the color must not come back
        // while the indicator is still on its way down.
        double t = transitionElapsed >= transitionDuration ? 1 :
                indicator.transitionAnimationEasing()
                         .apply(transitionElapsed / transitionDuration);
        return IndicatorConfiguration.lerp(transitionFromIndicator, indicator, t);
    }

    /** Scales the indicator away from the size it is easing to, as far as the overshoot, so
     *  that two indicators of the same size still pulse. It takes the duration of the one it
     *  enters to get there and that of the one it left to come back, on its own clock: a
     *  press shorter than that still pulses. */
    private IndicatorConfiguration swollen(IndicatorConfiguration eased) {
        double swell = swell();
        return swell == 1 ? eased : eased.scaled(swell);
    }

    /** What the indicator is scaled by, on its way to the overshoot and back to 1. */
    private double swell() {
        if (swellElapsed >= swellDuration)
            return 1;
        double overshoot = swellIndicator.transitionAnimationOvershoot();
        double toOvershoot =
                swellIndicator.transitionAnimationDuration().toMillis() / 1000d;
        if (swellElapsed < toOvershoot)
            return swellFromScale + (overshoot - swellFromScale) *
                                    swellIndicator.transitionAnimationEasing()
                                                  .apply(swellElapsed / toOvershoot);
        return 1 + (overshoot - 1) *
                   swellFromIndicator.transitionAnimationEasing()
                                     .apply((swellDuration - swellElapsed) /
                                            (swellDuration - toOvershoot));
    }

    @Override
    public void modeTimedOut() {
        // Ignored.
    }
}
