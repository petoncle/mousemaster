package mousemaster;

import mousemaster.platform.Overlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    /**
     * A main-loop stall (a long iteration, a sleep) is not animation time: an effect
     * never advances more than this per tick, so a one-shot is still seen after a
     * hiccup instead of having vanished, and a loop slows down rather than jumping.
     */
    static final double maxDeltaSeconds = 0.1;

    private final Overlay overlay;
    private Mode currentMode;
    // The mode before the last switch: a combo that switches mode and starts an effect
    // runs its start-effect after the switch when it waits behind an atomic command (a
    // hint selection moving the mouse), so the effect is looked up there as a fallback.
    private Mode previousMode;
    private final Map<String, EffectPlayer> players = new LinkedHashMap<>();
    private boolean showing;
    private Point mousePosition;
    // What the overlay last drew, to skip the repaint when nothing changed (a hold
    // keyframe, a delay, a hidden layer): the frames, and the mouse position for the
    // effects that follow it.
    private List<EffectFrame> lastFrames;
    private Point lastFramesMousePosition;

    public EffectManager(Overlay overlay) {
        this.overlay = overlay;
    }

    public void startEffect(String effectName) {
        startEffect(effectName, null, true);
    }

    /**
     * Starts (or restarts) an effect; the key is the one that completed the combo,
     * pressed or released, shown by a text layer's {@code {key}} ({@code {move}} with
     * its + or -) and, as a history across restarts of a running effect,
     * {@code {keys}} ({@code {moves}}).
     */
    public void startEffect(String effectName, Key key, boolean pressed) {
        EffectConfiguration effect = currentMode == null ? null :
                currentMode.effects().get(effectName);
        if (effect == null && previousMode != null) {
            effect = previousMode.effects().get(effectName);
            if (effect != null && logger.isDebugEnabled())
                logger.debug("Effect " + effectName + " is not defined in mode " +
                             currentMode.name() + ", using the definition of " +
                             previousMode.name() + " (the mode of the combo that started it)");
        }
        if (effect == null) {
            logger.warn("Effect " + effectName + " is not defined in mode " +
                        (currentMode == null ? "(no mode)" : currentMode.name()));
            return;
        }
        if (!effect.enabled()) {
            logger.debug("Effect " + effectName + " is disabled (enabled=false)");
            return;
        }
        logger.debug("Starting effect " + effectName);
        // Re-starting an already running effect restarts its cycle (and re-anchors it),
        // but keeps its key history: a keycast shows what was typed while it showed.
        EffectPlayer running = players.get(effectName);
        EffectPlayer player =
                new EffectPlayer(effect, effect.followMouse() ? null : mousePosition,
                        running == null ? List.of() : running.keys, key, pressed);
        players.put(effectName, player);
    }

    public void stopEffect(String effectName) {
        players.remove(effectName);
    }

    public void update(double delta) {
        if (players.isEmpty()) {
            if (showing) {
                showing = false;
                lastFrames = null;
                overlay.hideEffects();
            }
            return;
        }
        delta = Math.min(Math.max(0, delta), maxDeltaSeconds);
        List<EffectFrame> frames = new ArrayList<>(players.size());
        boolean anyFollowsMouse = false;
        for (Iterator<Map.Entry<String, EffectPlayer>> iterator =
             players.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, EffectPlayer> entry = iterator.next();
            EffectPlayer player = entry.getValue();
            try {
                player.advance(delta);
                if (player.done()) {
                    iterator.remove();
                    continue;
                }
                frames.add(player.frame());
            } catch (RuntimeException e) {
                // One broken effect must not take the main loop down: drop it and say so.
                logger.error("Effect " + entry.getKey() + " failed and was stopped", e);
                iterator.remove();
                continue;
            }
            anyFollowsMouse |= player.effect.followMouse();
        }
        if (frames.isEmpty()) {
            if (showing) {
                showing = false;
                lastFrames = null;
                overlay.hideEffects();
            }
            return;
        }
        boolean unchanged = showing && frames.equals(lastFrames) &&
                            (!anyFollowsMouse || Objects.equals(mousePosition, lastFramesMousePosition));
        if (unchanged)
            return;
        showing = true;
        lastFrames = frames;
        lastFramesMousePosition = mousePosition;
        overlay.setEffects(frames);
    }

    @Override
    public void mouseMoved(int x, int y) {
        mousePosition = new Point(x, y);
    }

    @Override
    public void modeChanged(Mode newMode) {
        // ModeListeners are also told about mutations of the current mode (a property
        // branch such as _{isleftmousepressing} -> ... switching): that is the same mode
        // with the same combos, so its loops keep running. Only a switch to another mode
        // stops them: a looping effect is stopped by its mode's stop-effect combo, which
        // the new mode may not have.
        boolean otherMode = currentMode == null || !currentMode.name().equals(newMode.name());
        if (otherMode)
            previousMode = currentMode;
        currentMode = newMode;
        if (otherMode)
            players.values().removeIf(player -> player.effect.loop());
    }

    @Override
    public void modeTimedOut() {
        // Ignored.
    }

    static final class EffectPlayer {

        /** How many keys {@code {keys}} remembers: enough for a line of typing. */
        static final int maxKeys = 16;

        private final EffectConfiguration effect;
        private final Point anchor;
        // The key moves that started this effect and its running predecessors, oldest
        // first, as combo moves ("+a", "-a"), for the {key}, {keys}, {move} and {moves}
        // placeholders of text layers.
        private final List<String> keys;
        private double elapsed;

        EffectPlayer(EffectConfiguration effect, Point anchor) {
            this(effect, anchor, List.of(), null, true);
        }

        EffectPlayer(EffectConfiguration effect, Point anchor, List<String> previousKeys,
                     Key key, boolean pressed) {
            this.effect = effect;
            this.anchor = anchor;
            List<String> keys = new ArrayList<>(previousKeys);
            if (key != null)
                keys.add((pressed ? "+" : "-") + key.name());
            if (keys.size() > maxKeys)
                keys = new ArrayList<>(keys.subList(keys.size() - maxKeys, keys.size()));
            this.keys = keys;
        }

        /** The text of a text layer with its placeholders filled in. */
        private EffectText text(EffectLayer layer) {
            EffectText text = layer.text();
            if (text == null || text.text().indexOf('{') == -1)
                return text;
            String filled = text.text();
            if (filled.contains("{key}"))
                filled = filled.replace("{key}", keys.isEmpty() ? "" : keys.getLast().substring(1));
            if (filled.contains("{move}"))
                filled = filled.replace("{move}", keys.isEmpty() ? "" : keys.getLast());
            if (filled.contains("{keys}")) {
                StringBuilder names = new StringBuilder();
                for (String move : keys)
                    names.append(names.isEmpty() ? "" : " ").append(move.substring(1));
                filled = filled.replace("{keys}", names.toString());
            }
            if (filled.contains("{moves}"))
                filled = filled.replace("{moves}", String.join(" ", keys));
            return filled.equals(text.text()) ? text : text.withText(filled);
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
                    number(values, EffectProperty.ARC_LENGTH),
                    number(values, EffectProperty.DASH_LENGTH),
                    number(values, EffectProperty.DASH_GAP),
                    number(values, EffectProperty.DASH_OFFSET), text(layer),
                    number(values, EffectProperty.FONT_SIZE),
                    (String) values.get(EffectProperty.BACKGROUND_COLOR),
                    (String) values.get(EffectProperty.OUTLINE_COLOR),
                    number(values, EffectProperty.OUTLINE_THICKNESS),
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
                // Only the properties some keyframe mentions can differ from the base.
                Object value = layer.animated().contains(property) ?
                        resolve(layer.keyframes(), percent, property, base) : base;
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
