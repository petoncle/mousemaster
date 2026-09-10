package mousemaster;

import java.time.Duration;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One layer of an effect: a shape, a few settings that hold for the layer's whole
 * life ({@code shape}, {@code filled}, {@code speed}, {@code delay}), and a table of
 * {@link EffectProperty base values} that keyframes can animate. Layers are drawn in
 * declaration order: layer1 first (bottom), then layer2 on top of it, and so on.
 *
 * <p>{@code sizeIsArea} marks {@code size=area}: the layer takes the size of the
 * whole effect area (a filled rect layer sized to the area is the effect's
 * background). {@code delay} shifts the layer's whole timeline, so layers can be
 * released one after another. {@code speed} runs the layer's timeline faster or
 * slower than the effect's cycle.
 */
public record EffectLayer(EffectShape shape, boolean filled, double speed,
                          Duration delay, boolean sizeIsArea, EffectText text,
                          Map<EffectProperty, Object> base,
                          List<EffectKeyframe> keyframes) {

    public EffectLayer {
        base = Map.copyOf(base);
        keyframes = List.copyOf(keyframes);
    }

    public static class EffectLayerBuilder {

        private EffectShape shape;
        private Boolean filled;
        private Double speed;
        private Duration delay;
        private Boolean sizeIsArea;
        private final EffectText.EffectTextBuilder text = new EffectText.EffectTextBuilder();
        private final Map<EffectProperty, Object> base = new EnumMap<>(EffectProperty.class);
        private List<EffectKeyframe> keyframes;

        public EffectLayerBuilder() {
        }

        public EffectLayerBuilder shape(EffectShape shape) {
            this.shape = shape;
            return this;
        }

        public EffectShape shape() {
            return shape;
        }

        public EffectLayerBuilder filled(Boolean filled) {
            this.filled = filled;
            return this;
        }

        /** The text settings (text, font-name, font-weight, font-italic, text-align). */
        public EffectText.EffectTextBuilder text() {
            return text;
        }

        public EffectLayerBuilder speed(Double speed) {
            this.speed = speed;
            return this;
        }

        public EffectLayerBuilder delay(Duration delay) {
            this.delay = delay;
            return this;
        }

        public EffectLayerBuilder sizeIsArea(Boolean sizeIsArea) {
            this.sizeIsArea = sizeIsArea;
            return this;
        }

        /** Sets a base value; the value's type must match the property's kind. */
        public EffectLayerBuilder set(EffectProperty property, Object value) {
            base.put(property, value);
            return this;
        }

        public EffectLayerBuilder keyframes(List<EffectKeyframe> keyframes) {
            this.keyframes = keyframes;
            return this;
        }

        public void extend(EffectLayerBuilder parent) {
            if (shape == null) shape = parent.shape;
            if (filled == null) filled = parent.filled;
            if (speed == null) speed = parent.speed;
            if (delay == null) delay = parent.delay;
            if (sizeIsArea == null) sizeIsArea = parent.sizeIsArea;
            text.extend(parent.text);
            for (Map.Entry<EffectProperty, Object> entry : parent.base.entrySet())
                base.putIfAbsent(entry.getKey(), entry.getValue());
            if (keyframes == null) keyframes = parent.keyframes;
        }

        /** The cycle resolves keyframes written in milliseconds and lets the order be checked. */
        public EffectLayer build(String effectName, int layerNumber, Duration cycle) {
            if (shape == null)
                throw new IllegalArgumentException(
                        "Effect " + effectName + " layer" + layerNumber +
                        " has no shape: every layer needs one, write effect." + effectName +
                        ".layer" + layerNumber + "-shape=<shape> with one of " +
                        EffectShape.names());
            Map<EffectProperty, Object> values = new EnumMap<>(EffectProperty.class);
            for (EffectProperty property : EffectProperty.values()) {
                Object value = base.get(property);
                if (value == null)
                    value = property.defaultValue;
                if (value != null)
                    values.put(property, value);
            }
            // A uniform size sets the width only: the height follows it.
            if (base.get(EffectProperty.WIDTH) != null && base.get(EffectProperty.HEIGHT) == null)
                values.put(EffectProperty.HEIGHT, base.get(EffectProperty.WIDTH));
            if (shape == EffectShape.TEXT && text.build().text() == null)
                throw new IllegalArgumentException(
                        "Effect " + effectName + " layer" + layerNumber +
                        " is a text layer but has nothing to say: add effect." + effectName +
                        ".layer" + layerNumber + "-text=<the text to show>");
            if (shape != EffectShape.TEXT && !text.isEmpty())
                throw new IllegalArgumentException(
                        "Effect " + effectName + " layer" + layerNumber +
                        " has text settings (text, font-name, font-weight, font-italic," +
                        " text-align) but draws a " + shape.name().toLowerCase() + ": those" +
                        " settings only apply to layer" + layerNumber + "-shape=text");
            List<EffectKeyframe> resolvedKeyframes = new ArrayList<>();
            double previousPercent = -1;
            for (EffectKeyframe keyframe : keyframes == null ? List.<EffectKeyframe>of() : keyframes) {
                double percent = keyframe.percent();
                if (keyframe.inMillis()) {
                    percent = 100 * keyframe.percent() / Math.max(1, cycle.toMillis());
                    if (percent > 100)
                        throw new IllegalArgumentException(
                                "Effect " + effectName + " layer" + layerNumber + " has a keyframe at " +
                                (long) keyframe.percent() + "ms, but one cycle of the effect only" +
                                " lasts duration-millis=" + cycle.toMillis() + ": move the keyframe" +
                                " earlier or make the duration longer");
                }
                if (percent <= previousPercent)
                    throw new IllegalArgumentException(
                            "Effect " + effectName + " layer" + layerNumber +
                            " keyframes must be in time order, each later than the previous:" +
                            " found " + (keyframe.inMillis() ? (long) keyframe.percent() + "ms" :
                            keyframe.percent() + "%") + " after " + previousPercent + "%");
                previousPercent = percent;
                resolvedKeyframes.add(keyframe.atPercent(percent));
            }
            return new EffectLayer(shape,
                    filled == null ? shape == EffectShape.DOT : filled,
                    speed == null ? 1 : speed,
                    delay == null ? Duration.ZERO : delay,
                    sizeIsArea != null && sizeIsArea,
                    shape == EffectShape.TEXT ? text.build() : null,
                    values,
                    resolvedKeyframes);
        }

    }

}
