package mousemaster;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnimationPlayer {

    private final Map<String, AnimationConfiguration> animationConfigurationByName;
    private final Map<String, Double> elapsedByAnimationName = new HashMap<>();
    private final Set<String> runningAnimationNames = new HashSet<>();
    private final Set<String> startedAnimationNames = new HashSet<>();
    private Map<ModePropertyPath, RunningTimeline> runningTimelineByPropertyPath = Map.of();
    private Mode animatedMode;

    public record RunningTimeline(Timeline timeline, double elapsed, Object current) {
    }

    public AnimationPlayer(Map<String, AnimationConfiguration> animationConfigurationByName) {
        this.animationConfigurationByName = animationConfigurationByName;
    }

    public void start(String animationName) {
        elapsedByAnimationName.put(animationName, 0d);
        runningAnimationNames.add(animationName);
        startedAnimationNames.add(animationName);
    }

    public void stop(String animationName) {
        runningAnimationNames.remove(animationName);
    }

    /** An animation started since the last update starts its clock at this one, so the work
     *  of the frame that started it takes none of its time. */
    public void update(double delta) {
        for (String animationName : List.copyOf(runningAnimationNames)) {
            if (ended(animationName, elapsed(animationName)))
                runningAnimationNames.remove(animationName);
            else if (!startedAnimationNames.contains(animationName))
                elapsedByAnimationName.merge(animationName, delta, Double::sum);
        }
        startedAnimationNames.clear();
    }

    public boolean ended(String animationName, double elapsed) {
        Integer repeatCount = animationConfigurationByName.get(animationName).repeatCount();
        return repeatCount != null && elapsed >= repeatCount * cycleSeconds(animationName);
    }

    public Set<String> animationNames() {
        return animationConfigurationByName.keySet();
    }

    public boolean running(String animationName) {
        return runningAnimationNames.contains(animationName);
    }

    public double elapsed(String animationName) {
        return elapsedByAnimationName.get(animationName);
    }

    public double progress(String animationName, double elapsed) {
        AnimationConfiguration animation = animationConfigurationByName.get(animationName);
        double cycles = elapsed / cycleSeconds(animationName);
        boolean ended = ended(animationName, elapsed);
        long cycle = ended ? animation.repeatCount() - 1 : (long) cycles;
        double progress = ended ? 1 : cycles - cycle;
        boolean backward =
                animation.direction() == AnimationDirection.ALTERNATE && cycle % 2 == 1;
        return backward ? 1 - progress : progress;
    }

    /** The running timeline with its new elapsed time, or null when the timeline starts: it is
     *  new, another one, or its animation started again. */
    public RunningTimeline runOn(RunningTimeline runningTimeline, Timeline timeline) {
        double elapsed = elapsed(timeline.animationName());
        return runningTimeline != null && timeline.equals(runningTimeline.timeline()) &&
               elapsed >= runningTimeline.elapsed() ?
                new RunningTimeline(timeline, elapsed, runningTimeline.current()) : null;
    }

    public Mode animate(Mode mode, String fieldName) {
        Map<ModePropertyPath, RunningTimeline> newRunningTimelineByPropertyPath = new HashMap<>();
        Mode newAnimatedMode = mode;
        for (Map.Entry<ModePropertyPath, Timeline> entry : mode.timelineByPropertyPath()
                                                               .entrySet()) {
            ModePropertyPath propertyPath = entry.getKey();
            if (!propertyPath.fieldNames().getFirst().equals(fieldName))
                continue;
            Timeline timeline = entry.getValue();
            String animationName = timeline.animationName();
            RunningTimeline runningTimeline =
                    runOn(runningTimelineByPropertyPath.get(propertyPath), timeline);
            if (runningTimeline == null)
                runningTimeline = new RunningTimeline(timeline, elapsed(animationName),
                        ModePropertyMutator.getModeProperty(animatedMode == null ? mode :
                                animatedMode, propertyPath.fieldNames()));
            newRunningTimelineByPropertyPath.put(propertyPath, runningTimeline);
            newAnimatedMode = newAnimatedMode.mutate(propertyPath, timeline.valueAt(
                    progress(animationName, runningTimeline.elapsed()), duration(animationName),
                    runningTimeline.current()));
        }
        runningTimelineByPropertyPath = newRunningTimelineByPropertyPath;
        animatedMode = newAnimatedMode;
        return newAnimatedMode;
    }

    public Duration duration(String animationName) {
        return animationConfigurationByName.get(animationName).duration();
    }

    public boolean overlap(String animationName) {
        return animationConfigurationByName.get(animationName).overlap();
    }

    private double cycleSeconds(String animationName) {
        return duration(animationName).toNanos() / 1e9;
    }

}
