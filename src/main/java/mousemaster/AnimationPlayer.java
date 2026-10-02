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
            if (elapsed < duration(animationName).toNanos() / 1e9) {
                elapsedByAnimationName.put(animationName, elapsed);
                continue;
            }
            elapsedByAnimationName.remove(animationName);
            comboWatcher.keyEvent(new KeyEvent.ReleaseKeyEvent(clock.now(),
                    new Key(animationName, null, null)));
        }
    }

    public double progress(String animationName) {
        return elapsedByAnimationName.get(animationName) /
               (duration(animationName).toNanos() / 1e9);
    }

    public Duration duration(String animationName) {
        return animationConfigurationByName.get(animationName).duration();
    }

}
