package mousemaster;

import mousemaster.platform.Overlay;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IndicatorManager implements ModeListener {

    private final Overlay overlay;
    private final AnimationPlayer animationPlayer;
    private Mode currentMode;
    private IndicatorConfiguration resolvedIndicator;
    private final Map<ModePropertyPath, Timeline> timelineByPropertyPath = new HashMap<>();
    private final Map<ModePropertyPath, Double> elapsedByPropertyPath = new HashMap<>();
    private final Map<ModePropertyPath, Object> currentByPropertyPath = new HashMap<>();

    public IndicatorManager(Overlay overlay, AnimationPlayer animationPlayer) {
        this.overlay = overlay;
        this.animationPlayer = animationPlayer;
    }

    public boolean animating() {
        return !timelineByPropertyPath.isEmpty();
    }

    public void update() {
        IndicatorConfiguration indicator = resolve();
        if (!indicator.enabled()) {
            overlay.hideIndicator();
            return;
        }
        overlay.setIndicator(indicator, animating(),
                !animationPlayer.animate(currentMode, "hideCursor").hideCursor().enabled());
    }

    /** The mode being left is resolved too: a state that lasts less than a frame is what
     *  current is taken from when a branch takes over from it. */
    @Override
    public void modeChanged(Mode newMode) {
        if (currentMode != null)
            resolve();
        currentMode = newMode;
    }

    private IndicatorConfiguration resolve() {
        updateTimelines();
        IndicatorConfiguration indicator = withTimelines(currentMode.indicator());
        resolvedIndicator = indicator.enabled() ? indicator : null;
        return indicator;
    }

    private void updateTimelines() {
        Map<ModePropertyPath, Timeline> newTimelineByPropertyPath = new HashMap<>();
        for (Map.Entry<ModePropertyPath, Timeline> entry : currentMode.timelineByPropertyPath()
                                                                     .entrySet()) {
            if (entry.getKey().fieldNames().getFirst().equals("indicator"))
                newTimelineByPropertyPath.put(entry.getKey(), entry.getValue());
        }
        timelineByPropertyPath.keySet().retainAll(newTimelineByPropertyPath.keySet());
        elapsedByPropertyPath.keySet().retainAll(newTimelineByPropertyPath.keySet());
        currentByPropertyPath.keySet().retainAll(newTimelineByPropertyPath.keySet());
        for (Map.Entry<ModePropertyPath, Timeline> entry : newTimelineByPropertyPath.entrySet()) {
            ModePropertyPath propertyPath = entry.getKey();
            Timeline timeline = entry.getValue();
            double elapsed = animationPlayer.elapsed(timeline.animationName());
            Double previousElapsed = elapsedByPropertyPath.put(propertyPath, elapsed);
            if (timeline.equals(timelineByPropertyPath.put(propertyPath, timeline)) &&
                elapsed >= previousElapsed)
                continue;
            IndicatorConfiguration indicator =
                    resolvedIndicator == null ? currentMode.indicator() : resolvedIndicator;
            currentByPropertyPath.put(propertyPath, ModePropertyMutator.getModeProperty(
                    indicator, indicatorFieldNames(propertyPath)));
        }
    }

    private IndicatorConfiguration withTimelines(IndicatorConfiguration indicator) {
        for (Map.Entry<ModePropertyPath, Timeline> entry : timelineByPropertyPath.entrySet()) {
            ModePropertyPath propertyPath = entry.getKey();
            Timeline timeline = entry.getValue();
            indicator = (IndicatorConfiguration) ModePropertyMutator.mutateModeProperty(
                    indicator, indicatorFieldNames(propertyPath),
                    timeline.valueAt(animationPlayer.progress(timeline.animationName()),
                            animationPlayer.duration(timeline.animationName()),
                            currentByPropertyPath.get(propertyPath)), null);
        }
        return indicator;
    }

    private static List<String> indicatorFieldNames(ModePropertyPath propertyPath) {
        return propertyPath.fieldNames().subList(1, propertyPath.fieldNames().size());
    }

    @Override
    public void modeTimedOut() {
        // Ignored.
    }
}
