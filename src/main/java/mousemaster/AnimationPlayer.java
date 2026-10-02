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

    public AnimationPlayer(Map<String, AnimationConfiguration> animationConfigurationByName) {
        this.animationConfigurationByName = animationConfigurationByName;
    }

    public void start(String animationName) {
        elapsedByAnimationName.put(animationName, 0d);
        runningAnimationNames.add(animationName);
    }

    public void stop(String animationName) {
        runningAnimationNames.remove(animationName);
    }

    public void update(double delta) {
        for (String animationName : List.copyOf(runningAnimationNames)) {
            double elapsed = elapsedByAnimationName.get(animationName) + delta;
            elapsedByAnimationName.put(animationName, elapsed);
            Integer repeatCount =
                    animationConfigurationByName.get(animationName).repeatCount();
            if (repeatCount != null && elapsed >= repeatCount * cycleSeconds(animationName))
                runningAnimationNames.remove(animationName);
        }
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

    public double progress(String animationName) {
        AnimationConfiguration animation = animationConfigurationByName.get(animationName);
        double cycles = elapsed(animationName) / cycleSeconds(animationName);
        boolean ended = animation.repeatCount() != null && cycles >= animation.repeatCount();
        long cycle = ended ? animation.repeatCount() - 1 : (long) cycles;
        double progress = ended ? 1 : cycles - cycle;
        boolean backward =
                animation.direction() == AnimationDirection.ALTERNATE && cycle % 2 == 1;
        return backward ? 1 - progress : progress;
    }

    public Mode animate(Mode mode, String fieldName) {
        for (Map.Entry<ModePropertyPath, Timeline> entry : mode.timelineByPropertyPath()
                                                               .entrySet()) {
            ModePropertyPath propertyPath = entry.getKey();
            if (!propertyPath.fieldNames().getFirst().equals(fieldName))
                continue;
            String animationName = entry.getValue().animationName();
            mode = mode.mutate(propertyPath, entry.getValue().valueAt(progress(animationName),
                    duration(animationName),
                    ModePropertyMutator.getModeProperty(mode, propertyPath.fieldNames())));
        }
        return mode;
    }

    public Duration duration(String animationName) {
        return animationConfigurationByName.get(animationName).duration();
    }

    private double cycleSeconds(String animationName) {
        return duration(animationName).toNanos() / 1e9;
    }

}
