package mousemaster;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnimationPlayer {

    private final Clock clock;
    private final ComboWatcher comboWatcher;
    private final Map<String, AnimationConfiguration> animationConfigurationByName;
    private final Map<String, Double> elapsedByAnimationName = new HashMap<>();

    public AnimationPlayer(Clock clock, ComboWatcher comboWatcher,
                           Map<String, AnimationConfiguration> animationConfigurationByName) {
        this.clock = clock;
        this.comboWatcher = comboWatcher;
        this.animationConfigurationByName = animationConfigurationByName;
    }

    public void start(String animationName) {
        elapsedByAnimationName.put(animationName, 0d);
    }

    public void stop(String animationName) {
        elapsedByAnimationName.remove(animationName);
    }

    public void update(double delta) {
        for (String animationName : List.copyOf(elapsedByAnimationName.keySet())) {
            double elapsed = elapsedByAnimationName.get(animationName) + delta;
            Integer repeatCount =
                    animationConfigurationByName.get(animationName).repeatCount();
            if (repeatCount == null || elapsed < repeatCount * cycleSeconds(animationName)) {
                elapsedByAnimationName.put(animationName, elapsed);
                continue;
            }
            elapsedByAnimationName.remove(animationName);
            comboWatcher.keyEvent(new KeyEvent.ReleaseKeyEvent(clock.now(),
                    new Key(animationName, null, null)));
        }
    }

    public double elapsed(String animationName) {
        return elapsedByAnimationName.get(animationName);
    }

    public double progress(String animationName) {
        double cycles = elapsed(animationName) / cycleSeconds(animationName);
        double progress = cycles - Math.floor(cycles);
        boolean backward =
                animationConfigurationByName.get(animationName).direction() ==
                AnimationDirection.ALTERNATE && (long) cycles % 2 == 1;
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
