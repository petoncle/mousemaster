package mousemaster;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One layer of an effect: a shape, a few settings that hold for the layer's whole
 * life ({@code shape}, {@code filled}, {@code speed}, {@code delay}), and a table of
 * {@link EffectProperty base values} that keyframes can animate. Layers are drawn in
 * declaration order: layer1 first (bottom), then layer2 on top of it, and so on.
 *
 * <p>{@code sizeIsArea} marks {@code size=area}: the layer takes the size of the
 * whole effect area (a filled square layer sized to the area is the effect's
 * background). {@code delay} shifts the layer's whole timeline, so layers can be
 * released one after another. {@code speed} runs the layer's timeline faster or
 * slower than the effect's cycle.
 */
public record EffectLayer(EffectShape shape, boolean filled, double speed,
                          Duration delay, boolean sizeIsArea,
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
            for (Map.Entry<EffectProperty, Object> entry : parent.base.entrySet())
                base.putIfAbsent(entry.getKey(), entry.getValue());
            if (keyframes == null) keyframes = parent.keyframes;
        }

        public EffectLayer build(String effectName, int layerNumber) {
            if (shape == null)
                throw new IllegalArgumentException(
                        "Effect " + effectName + " layer" + layerNumber +
                        " has no shape: expected effect." + effectName + ".layer" +
                        layerNumber + "-shape=<" + EffectShape.names() + ">");
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
            return new EffectLayer(shape,
                    filled == null ? shape == EffectShape.DOT : filled,
                    speed == null ? 1 : speed,
                    delay == null ? Duration.ZERO : delay,
                    sizeIsArea != null && sizeIsArea,
                    values,
                    keyframes == null ? List.of() : keyframes);
        }

    }

}
