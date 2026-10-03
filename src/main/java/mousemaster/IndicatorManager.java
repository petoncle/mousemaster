package mousemaster;

import mousemaster.platform.Overlay;

import java.util.HashMap;
import java.util.LinkedHashMap;
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
        if (!enabled(indicator)) {
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
        resolvedIndicator = enabled(indicator) ? indicator : null;
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
            Object current = ModePropertyMutator.getModeProperty(resolvedIndicator,
                    indicatorFieldNames(propertyPath));
            currentByPropertyPath.put(propertyPath, current == null ?
                    ModePropertyMutator.getModeProperty(currentMode.indicator(),
                            indicatorFieldNames(propertyPath)) : current);
        }
    }

    private IndicatorConfiguration withTimelines(IndicatorConfiguration indicator) {
        Map<String, IndicatorLayerConfiguration> layerByName =
                new LinkedHashMap<>(indicator.layerByName());
        for (Map.Entry<ModePropertyPath, Timeline> entry : timelineByPropertyPath.entrySet()) {
            ModePropertyPath propertyPath = entry.getKey();
            Timeline timeline = entry.getValue();
            List<String> fieldNames = indicatorFieldNames(propertyPath);
            Object value = timeline.valueAt(animationPlayer.progress(timeline.animationName()),
                    animationPlayer.duration(timeline.animationName()),
                    currentByPropertyPath.get(propertyPath));
            if (fieldNames.getFirst().equals("layerByName"))
                layerByName.put(fieldNames.get(1),
                        (IndicatorLayerConfiguration) ModePropertyMutator.mutateModeProperty(
                                layerByName.get(fieldNames.get(1)),
                                fieldNames.subList(2, fieldNames.size()), value, null));
            else
                indicator = (IndicatorConfiguration) ModePropertyMutator.mutateModeProperty(
                        indicator, fieldNames, value, null);
        }
        return new IndicatorConfiguration(indicator.renderAsCursor(), indicator.shadow(),
                layerByName);
    }

    private static boolean enabled(IndicatorConfiguration indicator) {
        for (IndicatorLayerConfiguration layer : indicator.layerByName().values())
            if (layer.enabled())
                return true;
        return false;
    }

    private static List<String> indicatorFieldNames(ModePropertyPath propertyPath) {
        return propertyPath.fieldNames().subList(1, propertyPath.fieldNames().size());
    }

    @Override
    public void modeTimedOut() {
        // Ignored.
    }
}
