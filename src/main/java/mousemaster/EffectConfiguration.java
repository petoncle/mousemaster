package mousemaster;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A named, mode-owned visual effect: a stack of shape layers animated over one
 * cycle, drawn in an area centered on the mouse position. Effects are started and
 * stopped by combos ({@code start-effect.<name>} / {@code stop-effect.<name>}),
 * so a looping effect can run exactly while a key is held.
 */
public record EffectConfiguration(Duration duration, boolean loop, Easing easing,
                                  int areaWidth, int areaHeight,
                                  List<EffectLayer> layers) {

    public static class EffectConfigurationBuilder {

        private Duration duration;
        private Boolean loop;
        private Easing easing;
        private Integer areaWidth;
        private Integer areaHeight;
        private final Map<Integer, EffectLayer.EffectLayerBuilder> layerByNumber =
                new LinkedHashMap<>();

        public EffectConfigurationBuilder() {
        }

        public EffectConfigurationBuilder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public EffectConfigurationBuilder loop(Boolean loop) {
            this.loop = loop;
            return this;
        }

        public EffectConfigurationBuilder easing(Easing easing) {
            this.easing = easing;
            return this;
        }

        public EffectConfigurationBuilder area(Integer areaWidth, Integer areaHeight) {
            this.areaWidth = areaWidth;
            this.areaHeight = areaHeight;
            return this;
        }

        public EffectLayer.EffectLayerBuilder layer(int layerNumber) {
            return layerByNumber.computeIfAbsent(layerNumber,
                    number -> new EffectLayer.EffectLayerBuilder());
        }

        public void extend(EffectConfigurationBuilder parent) {
            if (duration == null) duration = parent.duration;
            if (loop == null) loop = parent.loop;
            if (easing == null) easing = parent.easing;
            if (areaWidth == null) areaWidth = parent.areaWidth;
            if (areaHeight == null) areaHeight = parent.areaHeight;
            for (Map.Entry<Integer, EffectLayer.EffectLayerBuilder> parentEntry :
                    parent.layerByNumber.entrySet())
                layer(parentEntry.getKey()).extend(parentEntry.getValue());
        }

        public EffectConfiguration build(String effectName) {
            if (layerByNumber.isEmpty())
                throw new IllegalArgumentException(
                        "Effect " + effectName + " has no layers: expected at least " +
                        "effect." + effectName + ".layer1-shape=<shape>");
            List<EffectLayer> layers = new ArrayList<>();
            List<Integer> layerNumbers =
                    layerByNumber.keySet().stream().sorted().toList();
            for (int i = 0; i < layerNumbers.size(); i++) {
                int layerNumber = layerNumbers.get(i);
                if (layerNumber != i + 1)
                    throw new IllegalArgumentException(
                            "Effect " + effectName + " layer numbers must be " +
                            "consecutive starting at 1, but found layer" + layerNumber);
                layers.add(layerByNumber.get(layerNumber).build(effectName, layerNumber));
            }
            return new EffectConfiguration(
                    duration == null ? Duration.ofMillis(250) : duration,
                    loop != null && loop,
                    easing == null ? new Easing.Polynomial(1) : easing,
                    areaWidth == null ? 100 : areaWidth,
                    areaHeight == null ? (areaWidth == null ? 100 : areaWidth) : areaHeight,
                    List.copyOf(layers));
        }

    }

}
