package mousemaster;

import mousemaster.platform.Overlay;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IndicatorManager implements ModeListener {

    private final Overlay overlay;
    private final MacroPlayer macroPlayer;
    private Mode currentMode;
    private IndicatorConfiguration currentIndicator;
    private IndicatorConfiguration transitionFromIndicator;
    private double transitionElapsed;
    private double transitionDuration;
    private boolean allowFade = true;
    private IndicatorConfiguration drawnIndicator;
    private final Map<ModePropertyPath, Timeline> timelineByPropertyPath = new HashMap<>();
    private final Map<ModePropertyPath, Double> progressByPropertyPath = new HashMap<>();
    private final Map<ModePropertyPath, Object> currentByPropertyPath = new HashMap<>();

    public IndicatorManager(Overlay overlay, MacroPlayer macroPlayer) {
        this.overlay = overlay;
        this.macroPlayer = macroPlayer;
    }

    public boolean animating() {
        return transitionElapsed < transitionDuration || !timelineByPropertyPath.isEmpty();
    }

    public void update(double delta) {
        if (transitionElapsed < transitionDuration)
            transitionElapsed += delta;
        updateTimelines();
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

    private void updateTimelines() {
        Map<ModePropertyPath, Timeline> newTimelineByPropertyPath = new HashMap<>();
        for (Map.Entry<ModePropertyPath, Timeline> entry : currentMode.timelineByPropertyPath()
                                                                     .entrySet()) {
            if (entry.getKey().fieldNames().getFirst().equals("indicator"))
                newTimelineByPropertyPath.put(entry.getKey(), entry.getValue());
        }
        timelineByPropertyPath.keySet().retainAll(newTimelineByPropertyPath.keySet());
        progressByPropertyPath.keySet().retainAll(newTimelineByPropertyPath.keySet());
        currentByPropertyPath.keySet().retainAll(newTimelineByPropertyPath.keySet());
        for (Map.Entry<ModePropertyPath, Timeline> entry : newTimelineByPropertyPath.entrySet()) {
            ModePropertyPath propertyPath = entry.getKey();
            Timeline timeline = entry.getValue();
            double progress =
                    macroPlayer.virtualKeyMacroWaitProgress(timeline.animationName());
            Double previousProgress = progressByPropertyPath.put(propertyPath, progress);
            if (timeline.equals(timelineByPropertyPath.put(propertyPath, timeline)) &&
                progress >= previousProgress)
                continue;
            IndicatorConfiguration indicator =
                    drawnIndicator == null ? currentMode.indicator() : drawnIndicator;
            currentByPropertyPath.put(propertyPath, ModePropertyMutator.getModeProperty(
                    indicator, indicatorFieldNames(propertyPath)));
        }
    }

    private void updateIndicator(boolean allowFade) {
        if (!currentMode.indicator().enabled()) {
            currentIndicator = null;
            drawnIndicator = null;
            overlay.hideIndicator(allowFade);
            return;
        }
        drawnIndicator = withTimelines(eased(currentIndicator));
        overlay.setIndicator(drawnIndicator, currentIndicator, allowFade,
                !currentMode.hideCursor().enabled());
    }

    private IndicatorConfiguration withTimelines(IndicatorConfiguration indicator) {
        for (Map.Entry<ModePropertyPath, Timeline> entry : timelineByPropertyPath.entrySet()) {
            ModePropertyPath propertyPath = entry.getKey();
            Timeline timeline = entry.getValue();
            indicator = (IndicatorConfiguration) ModePropertyMutator.mutateModeProperty(
                    indicator, indicatorFieldNames(propertyPath),
                    timeline.valueAt(progressByPropertyPath.get(propertyPath),
                            macroPlayer.virtualKeyMacroWait(timeline.animationName()),
                            currentByPropertyPath.get(propertyPath)), null);
        }
        return indicator;
    }

    private static List<String> indicatorFieldNames(ModePropertyPath propertyPath) {
        return propertyPath.fieldNames().subList(1, propertyPath.fieldNames().size());
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
