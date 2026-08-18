package mousemaster;

import mousemaster.platform.Overlay;

public class IndicatorManager implements ModeListener {

    private final Overlay overlay;
    private Mode currentMode;
    private IndicatorConfiguration currentIndicator;
    private IndicatorConfiguration transitionFromIndicator;
    private double transitionElapsed;
    private double transitionDuration;
    private boolean allowFade = true;

    public IndicatorManager(Overlay overlay) {
        this.overlay = overlay;
    }

    public boolean animating() {
        return transitionElapsed < transitionDuration;
    }

    public void update(double delta) {
        if (transitionElapsed < transitionDuration)
            transitionElapsed += delta;
        updateIndicator(allowFade);
        allowFade = true;
    }

    /** Only the transition starts here: the frames are painted by the tick, so the mode
     *  changes of an iteration and its animation frame cost one render between them instead
     *  of one each. */
    @Override
    public void modeChanged(Mode newMode) {
        // Skip the fade animation when the zoom is about to change.
        allowFade &= currentMode == null || currentMode.zoom().equals(newMode.zoom());
        currentMode = newMode;
        IndicatorConfiguration newIndicator = newMode.indicator();
        if (newIndicator.equals(currentIndicator))
            return;
        if (currentIndicator != null) {
            // Start from what is on screen, with the colors currentIndicator was switching to.
            // Without them, a mode switching faster than a transition lasts keeps the first.
            transitionFromIndicator = eased(currentIndicator).switching(currentIndicator);
            transitionElapsed = 0;
            transitionDuration =
                    newIndicator.transitionAnimationDuration().toMillis() / 1000d;
        }
        currentIndicator = newIndicator;
    }

    private void updateIndicator(boolean allowFade) {
        if (!currentMode.indicator().enabled()) {
            currentIndicator = null;
            overlay.hideIndicator(allowFade);
            return;
        }
        overlay.setIndicator(eased(currentIndicator), currentIndicator, allowFade,
                !currentMode.hideCursor().enabled());
    }

    private IndicatorConfiguration eased(IndicatorConfiguration indicator) {
        if (transitionElapsed >= transitionDuration)
            return indicator;
        double t = indicator.transitionAnimationEasing()
                            .apply(transitionElapsed / transitionDuration);
        return IndicatorConfiguration.lerp(transitionFromIndicator, indicator, t);
    }

    @Override
    public void modeTimedOut() {
        // Ignored.
    }
}
