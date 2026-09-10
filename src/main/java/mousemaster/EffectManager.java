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
 * one-shot effect removes itself once every layer has played its cycles; a
 * looping effect wraps around until it is stopped. Switching modes stops looping
 * effects (their stop-effect combo may not exist in the new mode) but lets
 * one-shots finish.
 *
 * <p>All the animation arithmetic lives here, driven by the
 * {@link EffectProperty} table: the renderer only draws resolved numbers.
 */
public class EffectManager implements ModeListener, MousePositionListener {

    private static final Logger logger = LoggerFactory.getLogger(EffectManager.class);

    private final Overlay overlay;
    private Mode currentMode;
    private final Map<String, EffectPlayer> players = new LinkedHashMap<>();
    private boolean showing;
    private Point mousePosition;

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
        // Re-starting an already running effect restarts its cycle (and re-anchors it).
        players.put(effectName,
                new EffectPlayer(effect, effect.followMouse() ? null : mousePosition));
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
    public void mouseMoved(int x, int y) {
        mousePosition = new Point(x, y);
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
        private final Point anchor;
        private double elapsed;

        EffectPlayer(EffectConfiguration effect, Point anchor) {
            this.effect = effect;
            this.anchor = anchor;
        }

        void advance(double delta) {
            elapsed += delta;
        }

        /** A one-shot is done once its last layer (delays included) has played its cycles. */
        boolean done() {
            if (effect.loop())
                return false;
            for (EffectLayer layer : effect.layers())
                if (layerElapsed(layer) < effect.repeatCount() * cycleSeconds())
                    return false;
            return true;
        }

        private double cycleSeconds() {
            return Math.max(0.001, effect.duration().toMillis() / 1000d);
        }

        private double layerElapsed(EffectLayer layer) {
            return elapsed - layer.delay().toMillis() / 1000d;
        }

        EffectFrame frame() {
            List<EffectFrame.ResolvedEffectLayer> resolvedLayers = new ArrayList<>();
            for (EffectLayer layer : effect.layers()) {
                EffectFrame.ResolvedEffectLayer resolved = resolveLayer(layer);
                if (resolved != null)
                    resolvedLayers.add(resolved);
            }
            return new EffectFrame(effect.areaWidth(), effect.areaHeight(), anchor,
                    resolvedLayers);
        }

        /**
         * Where the layer is in its timeline, in percent (0-100), or -1 while its
         * delay has not elapsed or after its last cycle ended. The effect's easing
         * shapes each cycle; alternate runs every other cycle backwards; the layer's
         * speed then runs its timeline faster or slower than the cycle.
         */
        private double layerPercent(EffectLayer layer) {
            double layerElapsed = layerElapsed(layer);
            if (layerElapsed < 0)
                return -1;
            double cycle = cycleSeconds();
            double cycles = layerElapsed / cycle;
            if (!effect.loop() && cycles >= effect.repeatCount())
                return -1;
            double t = cycles - Math.floor(cycles);
            if (effect.alternate() && ((long) Math.floor(cycles)) % 2 == 1)
                t = 1 - t;
            double percent = 100 * effect.easing().apply(t) * layer.speed();
            if (percent <= 100)
                return percent;
            double wrapped = percent % 100;
            return wrapped == 0 ? 100 : wrapped;
        }

        private EffectFrame.ResolvedEffectLayer resolveLayer(EffectLayer layer) {
            double percent = layerPercent(layer);
            if (percent < 0)
                return null;
            Map<EffectProperty, Object> values = resolveValues(layer, percent);
            if (!(Boolean) values.get(EffectProperty.VISIBLE))
                return null;
            double scale = number(values, EffectProperty.SCALE);
            double width = scale * (sizeIsArea(layer, percent) ? effect.areaWidth() :
                    number(values, EffectProperty.WIDTH));
            double height = scale * (sizeIsArea(layer, percent) ? effect.areaHeight() :
                    number(values, EffectProperty.HEIGHT));
            double x = number(values, EffectProperty.X);
            double y = number(values, EffectProperty.Y);
            return new EffectFrame.ResolvedEffectLayer(layer.shape(), x, y, width, height,
                    number(values, EffectProperty.ROTATION),
                    number(values, EffectProperty.ROTATION_X),
                    number(values, EffectProperty.ROTATION_Y),
                    EffectProperty.number(values, EffectProperty.PIVOT_X, x),
                    EffectProperty.number(values, EffectProperty.PIVOT_Y, y),
                    (String) values.get(EffectProperty.COLOR),
                    number(values, EffectProperty.OPACITY), layer.filled(),
                    number(values, EffectProperty.THICKNESS),
                    number(values, EffectProperty.CORNER_RADIUS),
                    (int) Math.round(number(values, EffectProperty.EDGE_COUNT)),
                    number(values, EffectProperty.ARC_START),
                    number(values, EffectProperty.ARC_SWEEP),
                    number(values, EffectProperty.DASH_LENGTH),
                    number(values, EffectProperty.DASH_GAP),
                    number(values, EffectProperty.DASH_OFFSET), layer.text(),
                    number(values, EffectProperty.FONT_SIZE),
                    (String) values.get(EffectProperty.BACKGROUND_COLOR),
                    (String) values.get(EffectProperty.OUTLINE_COLOR),
                    number(values, EffectProperty.PADDING), scale);
        }

        private static double number(Map<EffectProperty, Object> values,
                                     EffectProperty property) {
            return (Double) values.get(property);
        }

        /** size=area holds from the latest keyframe that mentions it (the base value first). */
        private static boolean sizeIsArea(EffectLayer layer, double percent) {
            boolean value = layer.sizeIsArea();
            for (EffectKeyframe keyframe : layer.keyframes()) {
                if (keyframe.percent() > percent)
                    break;
                if (keyframe.sizeIsArea() != null)
                    value = keyframe.sizeIsArea();
            }
            return value;
        }

        /**
         * Every property's value at the given timeline position: the layer's base
         * value acts as an implicit keyframe at 0%, keyframes not mentioning the
         * property are skipped, and the value holds after its last mention. Numbers
         * interpolate linearly (shaped by the ending keyframe's easing), colors in
         * OkLab, switches hold.
         */
        static Map<EffectProperty, Object> resolveValues(EffectLayer layer,
                                                          double percent) {
            Map<EffectProperty, Object> resolved =
                    new java.util.EnumMap<>(EffectProperty.class);
            for (EffectProperty property : EffectProperty.values()) {
                Object base = layer.base().get(property);
                Object value = resolve(layer.keyframes(), percent, property, base);
                if (value != null)
                    resolved.put(property, value);
            }
            return resolved;
        }

        private static Object resolve(List<EffectKeyframe> keyframes, double percent,
                                      EffectProperty property, Object baseValue) {
            double previousPercent = 0;
            Object previousValue = baseValue;
            for (EffectKeyframe keyframe : keyframes) {
                Object value = keyframe.values().get(property);
                if (value == null)
                    continue;
                if (property.kind == EffectProperty.Kind.SWITCH) {
                    if (keyframe.percent() > percent)
                        break;
                    previousValue = value;
                    continue;
                }
                if (keyframe.percent() >= percent) {
                    if (previousValue == null)
                        return value;
                    double span = keyframe.percent() - previousPercent;
                    if (span <= 0)
                        return value;
                    double t = (percent - previousPercent) / span;
                    // The keyframe's easing shapes the segment that ends at it.
                    if (keyframe.easing() != null)
                        t = keyframe.easing().apply(t);
                    return mix(property, previousValue, value, t);
                }
                previousPercent = keyframe.percent();
                previousValue = value;
            }
            return previousValue;
        }

        private static Object mix(EffectProperty property, Object from, Object to,
                                  double t) {
            return switch (property.kind) {
                case NUMBER -> (Double) from + ((Double) to - (Double) from) * t;
                case COLOR -> HintGradientColor.mix((String) from, (String) to, t);
                case SWITCH -> to;
            };
        }

    }

}
