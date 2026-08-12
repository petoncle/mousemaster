package mousemaster;

import mousemaster.platform.Overlay;

public class IndicatorManager implements ModeListener {

    private final Overlay overlay;
    private Mode currentMode;

    public IndicatorManager(Overlay overlay) {
        this.overlay = overlay;
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
        if (currentMode.indicator().enabled())
            overlay.setIndicator(currentMode.indicator(), allowFade,
                    !currentMode.hideCursor().enabled());
        else
            overlay.hideIndicator(allowFade);
    }

    @Override
    public void modeTimedOut() {
        // Ignored.
    }
}
