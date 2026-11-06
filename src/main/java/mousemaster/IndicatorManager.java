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
    private IndicatorConfiguration drawnIndicator;
    private Map<ModePropertyPath, RunningTimeline> runningTimelineByPropertyPath = Map.of();
    private final Map<IndicatorLayerCopyKey, IndicatorLayerCopy> copyByKey =
            new LinkedHashMap<>();
    private final Map<String, IndicatorLayerConfiguration> anchoredLayerByName = new HashMap<>();
    private final Set<String> layerNamesToAnchor = new HashSet<>();
    private Map<IndicatorPosition, List<Point>> pointsByPosition = Map.of();
    private Map<Area, Rectangle> rectangleByArea = Map.of();
    private Map<Point, HintRectangles> hintRectanglesByPoint = Map.of();
    private double frameWait;

    public IndicatorManager(Overlay overlay, AnimationPlayer animationPlayer,
                            HintManager hintManager, Supplier<App> activeApp) {
        this.overlay = overlay;
        this.animationPlayer = animationPlayer;
        this.hintManager = hintManager;
        this.activeApp = activeApp;
    }

    public boolean animating() {
        return !runningTimelineByPropertyPath.isEmpty() || !copyByKey.isEmpty();
    }

    public void update(double delta) {
        IndicatorConfiguration indicator = resolve(delta);
        frameWait -= delta;
        if (animating() && frameWait > 0)
            return;
        draw(indicator);
    }

    private void draw(IndicatorConfiguration indicator) {
        drawnIndicator = indicator;
        frameWait = Mousemaster.FRAME_INTERVAL;
        if (!drawnIndicator.enabled() && copyByKey.isEmpty()) {
            overlay.hideIndicator();
            layerNamesToAnchor.clear();
            return;
        }
        Map<IndicatorLayerCopyKey, IndicatorConfiguration> indicatorByCopyKey =
                new LinkedHashMap<>();
        for (Map.Entry<IndicatorLayerCopyKey, IndicatorLayerCopy> entry : copyByKey.entrySet())
            indicatorByCopyKey.put(entry.getKey(), withTimelines(entry.getValue().indicator(),
                    entry.getValue().runningTimelineByPropertyPath()));
        overlay.setIndicator(drawnIndicator, indicatorByCopyKey, Set.copyOf(layerNamesToAnchor),
                pointsByPosition, rectangleByArea, hintRectanglesByPoint,
                currentMode.hintMesh().z(), animating(),
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
            resolve(0);
        currentMode = newMode;
        if (!entryCombosPending && !hintManager.waitingForUiElements())
            draw(resolve(0));
    }

    @Override
    public void entryCombosRan() {
        entryCombosPending = false;
        if (!hintManager.waitingForUiElements())
            draw(resolve(0));
    }

    private IndicatorConfiguration resolve(double delta) {
        updateCopies(delta);
        updateTimelines();
        IndicatorConfiguration indicator = withTimelines(currentMode.indicator(),
                runningTimelineByPropertyPath);
        if (indicator.enabled())
            resolvedIndicator = indicator;
        Map<String, IndicatorLayerConfiguration> previousAnchoredLayerByName =
                Map.copyOf(anchoredLayerByName);
        anchoredLayerByName.clear();
        for (Map.Entry<String, IndicatorLayerConfiguration> entry : indicator.layerByName()
                                                                             .entrySet()) {
            IndicatorLayerConfiguration layer = entry.getValue();
            if (!layer.enabled() || layer.followMouse())
                continue;
            anchoredLayerByName.put(entry.getKey(), layer);
            IndicatorLayerConfiguration previousLayer =
                    previousAnchoredLayerByName.get(entry.getKey());
            if (previousLayer == null || !previousLayer.positions().equals(layer.positions()) ||
                previousLayer.positionAnchor() != layer.positionAnchor() ||
                previousLayer.endPositionAnchor() != layer.endPositionAnchor() ||
                !Objects.equals(previousLayer.area(), layer.area()))
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
                else if (layer.enabled() &&
                         position == IndicatorPosition.PointIndicatorPosition.ACTIVE_WINDOW_CENTER)
                    newPointsByPosition.put(position, List.of(
                            hintManager.areaCenterPoint(AreaCenter.ACTIVE_WINDOW_CENTER)));
                else if (layer.enabled() &&
                         position == IndicatorPosition.PointIndicatorPosition.LAST_OVERLAY_AREA_CENTER)
                    newPointsByPosition.put(position, List.of(
                            hintManager.areaCenterPoint(AreaCenter.LAST_OVERLAY_AREA_CENTER)));
        Map<Area, Rectangle> newRectangleByArea = new HashMap<>();
        for (IndicatorLayerConfiguration layer : indicator.layerByName().values())
            if (layer.enabled() && layer.area().size().source() != null)
                newRectangleByArea.put(layer.area(), hintManager.indicatorArea(layer.area()));
        Map<Point, HintRectangles> newHintRectanglesByPoint = new HashMap<>();
        for (IndicatorLayerConfiguration layer : indicator.layerByName().values()) {
            if (!layer.enabled() || layer.positionAnchor() == PositionAnchor.POINT &&
                                    layer.endPositionAnchor() == PositionAnchor.POINT)
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
            if (layer.enabled() && layer.follow() && !layer.followMouse() &&
                (layer.area().size().source() == null && layer.positions().stream().anyMatch(
                        position -> !newPointsByPosition.containsKey(position)) ||
                 layer.positions().stream().anyMatch(
                         position -> !Objects.equals(newPointsByPosition.get(position),
                                 pointsByPosition.get(position))) ||
                 layer.area().size().source() != null &&
                 !Objects.equals(newRectangleByArea.get(layer.area()),
                         rectangleByArea.get(layer.area())) ||
                 (layer.positionAnchor() != PositionAnchor.POINT ||
                  layer.endPositionAnchor() != PositionAnchor.POINT) &&
                 !newHintRectanglesByPoint.equals(hintRectanglesByPoint)))
                layerNamesToAnchor.add(entry.getKey());
        }
        pointsByPosition = newPointsByPosition;
        rectangleByArea = newRectangleByArea;
        hintRectanglesByPoint = newHintRectanglesByPoint;
        Map<String, IndicatorLayerConfiguration> layerByName =
                new LinkedHashMap<>(indicator.layerByName());
        layerByName.replaceAll((layerName, layer) ->
                layer.enabled() && shapeCount(layer) == 0 ?
                        layer.builder().enabled(false).build() : layer);
        return new IndicatorConfiguration(indicator.renderAsCursor(),
                indicator.excludeFromCapture(), layerByName);
    }

    private int shapeCount(IndicatorLayerConfiguration layer) {
        int pointCount = pointCount(layer.position());
        if (layer.endPosition() == null)
            return pointCount;
        if (layer.endPosition().equals(layer.position()))
            return Math.max(pointCount - 1, 0);
        return pointCount * pointCount(layer.endPosition());
    }

    private int pointCount(IndicatorPosition position) {
        List<Point> points = pointsByPosition.get(position);
        return points == null ? 1 : points.size();
    }

    private record RunningTimeline(Timeline timeline, double elapsed, Object current) {
    }

    private record IndicatorLayerCopy(
            IndicatorConfiguration indicator,
            Map<ModePropertyPath, RunningTimeline> runningTimelineByPropertyPath) {
    }

    private void updateCopies(double delta) {
        Map<String, IndicatorLayerCopy> copyByLayerName = new LinkedHashMap<>();
        for (Map.Entry<ModePropertyPath, RunningTimeline> entry :
                runningTimelineByPropertyPath.entrySet()) {
            ModePropertyPath propertyPath = entry.getKey();
            String animationName = entry.getValue().timeline().animationName();
            if (!propertyPath.fieldNames().get(1).equals("layerByName") ||
                !animationPlayer.overlap(animationName) ||
                animationPlayer.elapsed(animationName) >= entry.getValue().elapsed())
                continue;
            String layerName = propertyPath.fieldNames().get(2);
            IndicatorLayerCopy copy = copyByLayerName.computeIfAbsent(layerName,
                    layerName_ -> new IndicatorLayerCopy(new IndicatorConfiguration(
                            drawnIndicator.renderAsCursor(), drawnIndicator.excludeFromCapture(),
                            Map.of(layerName, drawnIndicator.layerByName().get(layerName))),
                            new HashMap<>()));
            copy.runningTimelineByPropertyPath().put(propertyPath, entry.getValue());
        }
        for (Map.Entry<String, IndicatorLayerCopy> entry : copyByLayerName.entrySet()) {
            int index = 0;
            while (copyByKey.containsKey(new IndicatorLayerCopyKey(entry.getKey(), index)))
                index++;
            copyByKey.put(new IndicatorLayerCopyKey(entry.getKey(), index), entry.getValue());
            layerNamesToAnchor.add(entry.getKey());
        }
        copyByKey.values().removeIf(copy -> copy.runningTimelineByPropertyPath().values().stream()
                .allMatch(runningTimeline -> animationPlayer.ended(
                        runningTimeline.timeline().animationName(), runningTimeline.elapsed())));
        for (IndicatorLayerCopy copy : copyByKey.values())
            copy.runningTimelineByPropertyPath().replaceAll((propertyPath, runningTimeline) ->
                    new RunningTimeline(runningTimeline.timeline(),
                            runningTimeline.elapsed() + delta, runningTimeline.current()));
    }

    private void updateTimelines() {
        Map<ModePropertyPath, RunningTimeline> newRunningTimelineByPropertyPath = new HashMap<>();
        for (Map.Entry<ModePropertyPath, Timeline> entry : currentMode.timelineByPropertyPath()
                                                                     .entrySet()) {
            ModePropertyPath propertyPath = entry.getKey();
            Timeline timeline = entry.getValue();
            if (!propertyPath.fieldNames().getFirst().equals("indicator"))
                continue;
            double elapsed = animationPlayer.elapsed(timeline.animationName());
            RunningTimeline runningTimeline = runningTimelineByPropertyPath.get(propertyPath);
            if (runningTimeline != null && timeline.equals(runningTimeline.timeline()) &&
                elapsed >= runningTimeline.elapsed()) {
                newRunningTimelineByPropertyPath.put(propertyPath,
                        new RunningTimeline(timeline, elapsed, runningTimeline.current()));
                continue;
            }
            Object current = ModePropertyMutator.getModeProperty(resolvedIndicator,
                    indicatorFieldNames(propertyPath));
            newRunningTimelineByPropertyPath.put(propertyPath, new RunningTimeline(timeline,
                    elapsed, current == null ?
                    ModePropertyMutator.getModeProperty(currentMode.indicator(),
                            indicatorFieldNames(propertyPath)) : current));
            frameWait = 0;
            if (propertyPath.fieldNames().get(1).equals("layerByName"))
                layerNamesToAnchor.add(propertyPath.fieldNames().get(2));
        }
        runningTimelineByPropertyPath = newRunningTimelineByPropertyPath;
    }

    private IndicatorConfiguration withTimelines(
            IndicatorConfiguration indicator,
            Map<ModePropertyPath, RunningTimeline> runningTimelineByPropertyPath) {
        Map<String, IndicatorLayerConfiguration> layerByName =
                new LinkedHashMap<>(indicator.layerByName());
        for (Map.Entry<ModePropertyPath, RunningTimeline> entry :
                runningTimelineByPropertyPath.entrySet()) {
            ModePropertyPath propertyPath = entry.getKey();
            Timeline timeline = entry.getValue().timeline();
            List<String> fieldNames = indicatorFieldNames(propertyPath);
            Object value = timeline.valueAt(animationPlayer.progress(timeline.animationName(),
                            entry.getValue().elapsed()),
                    animationPlayer.duration(timeline.animationName()),
                    entry.getValue().current());
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
