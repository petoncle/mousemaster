package mousemaster;

import mousemaster.platform.Overlay;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public class IndicatorManager implements ModeListener {

    private final Overlay overlay;
    private final AnimationPlayer animationPlayer;
    private final HintManager hintManager;
    private final Supplier<App> activeApp;
    private Mode currentMode;
    private boolean entryCombosPending;
    private IndicatorConfiguration resolvedIndicator;
    private final Map<ModePropertyPath, Timeline> timelineByPropertyPath = new HashMap<>();
    private final Map<ModePropertyPath, Double> elapsedByPropertyPath = new HashMap<>();
    private final Map<ModePropertyPath, Object> currentByPropertyPath = new HashMap<>();
    private final Set<String> anchoredLayerNames = new HashSet<>();
    private final Set<String> layerNamesToAnchor = new HashSet<>();
    private Map<IndicatorPosition, List<Point>> pointsByPosition = Map.of();
    private Map<HintGridArea, Rectangle> rectangleByArea = Map.of();
    private Map<Point, HintRectangles> hintRectanglesByPoint = Map.of();

    public IndicatorManager(Overlay overlay, AnimationPlayer animationPlayer,
                            HintManager hintManager, Supplier<App> activeApp) {
        this.overlay = overlay;
        this.animationPlayer = animationPlayer;
        this.hintManager = hintManager;
        this.activeApp = activeApp;
    }

    public boolean animating() {
        return !timelineByPropertyPath.isEmpty();
    }

    public void update() {
        IndicatorConfiguration indicator = resolve();
        if (!indicator.enabled()) {
            overlay.hideIndicator();
            layerNamesToAnchor.clear();
            return;
        }
        overlay.setIndicator(indicator, Set.copyOf(layerNamesToAnchor), pointsByPosition,
                rectangleByArea, hintRectanglesByPoint, animating(),
                !animationPlayer.animate(currentMode, "hideCursor").hideCursor().enabled());
        layerNamesToAnchor.clear();
    }

    /** The mode being left is resolved too: a state that lasts less than a frame is what
     *  current is taken from when a branch takes over from it. A hidden indicator keeps the
     *  last state it was shown in. The indicator is drawn before the overlay is painted, so
     *  that the two change in the same frame. After a switch, it is drawn once the entry combos
     *  have run, so that what they start is in its first frame. */
    @Override
    public void modeChanged(Mode newMode) {
        if (currentMode != null && !newMode.name().equals(currentMode.name()))
            entryCombosPending = true;
        if (currentMode != null)
            resolve();
        currentMode = newMode;
        if (!entryCombosPending && !hintManager.waitingForUiElements())
            update();
    }

    @Override
    public void entryCombosRan() {
        entryCombosPending = false;
        if (!hintManager.waitingForUiElements())
            update();
    }

    private IndicatorConfiguration resolve() {
        updateTimelines();
        IndicatorConfiguration indicator = withTimelines(currentMode.indicator());
        if (indicator.enabled())
            resolvedIndicator = indicator;
        Set<String> previousAnchoredLayerNames = Set.copyOf(anchoredLayerNames);
        anchoredLayerNames.clear();
        for (Map.Entry<String, IndicatorLayerConfiguration> entry : indicator.layerByName()
                                                                             .entrySet()) {
            if (!entry.getValue().enabled() || entry.getValue().followMouse())
                continue;
            anchoredLayerNames.add(entry.getKey());
            if (!previousAnchoredLayerNames.contains(entry.getKey()))
                layerNamesToAnchor.add(entry.getKey());
        }
        Map<IndicatorPosition, List<Point>> newPointsByPosition = new HashMap<>();
        newPointsByPosition.put(IndicatorPosition.PointIndicatorPosition.TARGET,
                List.of(hintManager.targetPoint()));
        if (hintManager.lastSelectedHintPoint() != null)
            newPointsByPosition.put(IndicatorPosition.PointIndicatorPosition.LAST_SELECTED_HINT,
                    List.of(hintManager.lastSelectedHintPoint()));
        for (IndicatorLayerConfiguration layer : indicator.layerByName().values())
            for (IndicatorPosition position : layer.positions())
                if (layer.enabled() && position instanceof
                        IndicatorPosition.PositionHistoryIndicatorPosition(
                                String positionHistoryName))
                    newPointsByPosition.put(position, List.copyOf(
                            hintManager.positionHistory(positionHistoryName, activeApp)
                                       .positions()));
        Map<HintGridArea, Rectangle> newRectangleByArea = new HashMap<>();
        for (IndicatorLayerConfiguration layer : indicator.layerByName().values())
            if (layer.enabled() && layer.area() != null)
                newRectangleByArea.put(layer.area(), hintManager.indicatorArea(layer.area()));
        Map<Point, HintRectangles> newHintRectanglesByPoint = new HashMap<>();
        for (IndicatorLayerConfiguration layer : indicator.layerByName().values()) {
            if (!layer.enabled() || layer.positionAnchor() == PositionAnchor.POINT)
                continue;
            for (IndicatorPosition position : layer.positions()) {
                if (position == IndicatorPosition.PointIndicatorPosition.LAST_SELECTED_HINT &&
                    hintManager.lastSelectedHintPoint() != null)
                    newHintRectanglesByPoint.put(hintManager.lastSelectedHintPoint(),
                            hintManager.lastSelectedHintRectangles());
                else if (position instanceof IndicatorPosition.PositionHistoryIndicatorPosition(
                        String positionHistoryName)) {
                    PositionHistory positionHistory =
                            hintManager.positionHistory(positionHistoryName, activeApp);
                    for (Point point : positionHistory.positions()) {
                        Rectangle hintCell = positionHistory.hintCell(point);
                        if (hintCell != null)
                            newHintRectanglesByPoint.put(point, hintManager.hintRectangles(hintCell));
                    }
                }
            }
        }
        for (Map.Entry<String, IndicatorLayerConfiguration> entry : indicator.layerByName()
                                                                             .entrySet()) {
            IndicatorLayerConfiguration layer = entry.getValue();
            if (layer.enabled() && (layer.positions().stream().anyMatch(
                    position -> !Objects.equals(newPointsByPosition.get(position),
                            pointsByPosition.get(position))) ||
                                    layer.area() != null &&
                                    !Objects.equals(newRectangleByArea.get(layer.area()),
                                            rectangleByArea.get(layer.area())) ||
                                    layer.positionAnchor() != PositionAnchor.POINT &&
                                    !newHintRectanglesByPoint.equals(hintRectanglesByPoint)))
                layerNamesToAnchor.add(entry.getKey());
        }
        pointsByPosition = newPointsByPosition;
        rectangleByArea = newRectangleByArea;
        hintRectanglesByPoint = newHintRectanglesByPoint;
        Map<String, IndicatorLayerConfiguration> layerByName =
                new LinkedHashMap<>(indicator.layerByName());
        layerByName.replaceAll((layerName, layer) ->
                layer.enabled() && IndicatorPosition.runThrough(layer.positions()) &&
                pointCount(layer) < 2 ? layer.builder().enabled(false).build() : layer);
        return new IndicatorConfiguration(indicator.renderAsCursor(),
                indicator.excludeFromCapture(), layerByName);
    }

    /** A position the manager has no points for is the mouse, one point. */
    private int pointCount(IndicatorLayerConfiguration layer) {
        int pointCount = 0;
        for (IndicatorPosition position : layer.positions()) {
            List<Point> points = pointsByPosition.get(position);
            pointCount += points == null ? 1 : points.size();
        }
        return pointCount;
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
            if (propertyPath.fieldNames().get(1).equals("layerByName"))
                layerNamesToAnchor.add(propertyPath.fieldNames().get(2));
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
        return new IndicatorConfiguration(indicator.renderAsCursor(),
                indicator.excludeFromCapture(), layerByName);
    }

    private static List<String> indicatorFieldNames(ModePropertyPath propertyPath) {
        return propertyPath.fieldNames().subList(1, propertyPath.fieldNames().size());
    }

    @Override
    public void modeTimedOut() {
        // Ignored.
    }
}
