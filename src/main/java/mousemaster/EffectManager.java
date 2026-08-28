package mousemaster;

import mousemaster.platform.Overlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the mode's effects: {@code start-effect} snapshots the effect's
 * configuration into a player, {@code update} advances the players and hands the
 * overlay fully-resolved frames, {@code stop-effect} removes a player. A
 * one-shot effect removes itself at the end of its cycle; a looping effect wraps
 * around until it is stopped. Switching modes stops looping effects (their
 * stop-effect combo may not exist in the new mode) but lets one-shots finish.
 */
public class EffectManager implements ModeListener {

    private static final Logger logger = LoggerFactory.getLogger(EffectManager.class);

    private final Overlay overlay;
    private Mode currentMode;
    private final Map<String, EffectPlayer> players = new LinkedHashMap<>();
    private boolean showing;

    public EffectManager(Overlay overlay) {
        this.overlay = overlay;
    }

    public void startEffect(String effectName) {
        EffectConfiguration effect = currentMode == null ? null :
                currentMode.effects().get(effectName);
        if (effect == null) {
            logger.warn("Effect " + effectName + " is not defined in mode " +
                        (currentMode == null ? "(no mode)" : currentMode.name()));
            return;
        }
        logger.debug("Starting effect " + effectName);
        // Re-starting an already running effect restarts its cycle.
        players.put(effectName, new EffectPlayer(effect));
    }

    public void stopEffect(String effectName) {
        players.remove(effectName);
    }

    public void update(double delta) {
        List<EffectFrame> frames = new ArrayList<>();
        for (Iterator<EffectPlayer> iterator =
             players.values().iterator(); iterator.hasNext(); ) {
            EffectPlayer player = iterator.next();
            player.advance(delta);
            if (player.done()) {
                iterator.remove();
                continue;
            }
            frames.add(player.frame());
        }
        if (frames.isEmpty()) {
            if (showing) {
                showing = false;
                overlay.hideEffects();
            }
            return;
        }
        showing = true;
        overlay.setEffects(frames);
    }

    @Override
    public void modeChanged(Mode newMode) {
        currentMode = newMode;
        // A looping effect is stopped by its mode's stop-effect combo, which the new
        // mode may not have: stop the loops rather than leaving them running forever.
        players.values().removeIf(player -> player.effect.loop());
    }

    @Override
    public void modeTimedOut() {
        // Ignored.
    }

    static final class EffectPlayer {

        private final EffectConfiguration effect;
        private double elapsed;

        EffectPlayer(EffectConfiguration effect) {
            this.effect = effect;
        }

        void advance(double delta) {
            elapsed += delta;
        }

        boolean done() {
            return !effect.loop() && elapsed >= cycleSeconds();
        }

        private double cycleSeconds() {
            return Math.max(0.001, effect.duration().toMillis() / 1000d);
        }

        EffectFrame frame() {
            double cycle = cycleSeconds();
            double t = effect.loop() ? (elapsed % cycle) / cycle :
                    Math.min(1, elapsed / cycle);
            double percent = 100 * effect.easing().apply(t);
            List<EffectFrame.ResolvedEffectLayer> resolvedLayers = new ArrayList<>();
            for (EffectLayer layer : effect.layers()) {
                EffectFrame.ResolvedEffectLayer resolved = resolveLayer(layer, percent);
                if (resolved != null)
                    resolvedLayers.add(resolved);
            }
            return new EffectFrame(effect.areaWidth(), effect.areaHeight(),
                    resolvedLayers);
        }

        private EffectFrame.ResolvedEffectLayer resolveLayer(EffectLayer layer,
                                                             double effectPercent) {
            // The layer's speed runs its timeline faster or slower than the effect's
            // cycle: at speed 2 the timeline plays twice per cycle.
            double percent = layerPercent(effectPercent, layer.speed());
            if (!latestBoolean(layer.keyframes(), percent, EffectKeyframe::visible))
                return null;
            double width = interpolate(layer.keyframes(), percent,
                    keyframe -> sizeWidth(keyframe), layerSizeWidth(layer));
            double height = interpolate(layer.keyframes(), percent,
                    keyframe -> sizeHeight(keyframe), layerSizeHeight(layer));
            double x = interpolate(layer.keyframes(), percent, EffectKeyframe::x,
                    layer.x());
            double y = interpolate(layer.keyframes(), percent, EffectKeyframe::y,
                    layer.y());
            double rotation = interpolate(layer.keyframes(), percent,
                    EffectKeyframe::rotation, layer.rotation());
            double rotationX = interpolate(layer.keyframes(), percent,
                    EffectKeyframe::rotationX, layer.rotationX());
            double rotationY = interpolate(layer.keyframes(), percent,
                    EffectKeyframe::rotationY, layer.rotationY());
            double opacity = interpolate(layer.keyframes(), percent,
                    EffectKeyframe::opacity, layer.opacity());
            String hexColor = latestString(layer.keyframes(), percent,
                    EffectKeyframe::hexColor, layer.hexColor());
            return new EffectFrame.ResolvedEffectLayer(layer.shape(), x, y, width,
                    height, rotation, rotationX, rotationY, hexColor, opacity,
                    layer.filled(), layer.thickness());
        }

        private static double layerPercent(double effectPercent, double speed) {
            double percent = effectPercent * speed;
            if (percent <= 100)
                return percent;
            double wrapped = percent % 100;
            return wrapped == 0 ? 100 : wrapped;
        }

        private double layerSizeWidth(EffectLayer layer) {
            return layer.sizeIsArea() ? effect.areaWidth() : layer.sizeWidth();
        }

        private double layerSizeHeight(EffectLayer layer) {
            return layer.sizeIsArea() ? effect.areaHeight() : layer.sizeHeight();
        }

        private Double sizeWidth(EffectKeyframe keyframe) {
            if (keyframe.sizeIsArea() != null && keyframe.sizeIsArea())
                return (double) effect.areaWidth();
            return keyframe.sizeWidth();
        }

        private Double sizeHeight(EffectKeyframe keyframe) {
            if (keyframe.sizeIsArea() != null && keyframe.sizeIsArea())
                return (double) effect.areaHeight();
            return keyframe.sizeHeight();
        }

        private interface KeyframeValue {
            Double value(EffectKeyframe keyframe);
        }

        /**
         * The value of one animated property at the given cycle position: the layer's
         * base value acts as an implicit keyframe at 0%, keyframes not mentioning the
         * property are skipped, and the value holds after its last mention.
         */
        private static double interpolate(List<EffectKeyframe> keyframes,
                                          double percent, KeyframeValue property,
                                          double baseValue) {
            double previousPercent = 0;
            double previousValue = baseValue;
            for (EffectKeyframe keyframe : keyframes) {
                Double value = property.value(keyframe);
                if (value == null)
                    continue;
                if (keyframe.percent() >= percent) {
                    double span = keyframe.percent() - previousPercent;
                    if (span <= 0)
                        return value;
                    double t = (percent - previousPercent) / span;
                    // The keyframe's easing shapes the segment that ends at it.
                    if (keyframe.easing() != null)
                        t = keyframe.easing().apply(t);
                    return previousValue + (value - previousValue) * t;
                }
                previousPercent = keyframe.percent();
                previousValue = value;
            }
            return previousValue;
        }

        private static boolean latestBoolean(List<EffectKeyframe> keyframes,
                                             double percent,
                                             java.util.function.Function<EffectKeyframe, Boolean> property) {
            boolean value = true;
            for (EffectKeyframe keyframe : keyframes) {
                if (keyframe.percent() > percent)
                    break;
                Boolean keyframeValue = property.apply(keyframe);
                if (keyframeValue != null)
                    value = keyframeValue;
            }
            return value;
        }

        private static String latestString(List<EffectKeyframe> keyframes,
                                           double percent,
                                           java.util.function.Function<EffectKeyframe, String> property,
                                           String baseValue) {
            String value = baseValue;
            for (EffectKeyframe keyframe : keyframes) {
                if (keyframe.percent() > percent)
                    break;
                String keyframeValue = property.apply(keyframe);
                if (keyframeValue != null)
                    value = keyframeValue;
            }
            return value;
        }

    }

}
