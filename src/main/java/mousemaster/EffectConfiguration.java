package mousemaster;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A named, mode-owned visual effect: a stack of shape layers animated over one
 * cycle, drawn in an area centered on the mouse position (or, with
 * {@code follow-mouse=false}, on the position the mouse had when the effect
 * started). Effects are started and stopped by combos ({@code start-effect.<name>}
 * / {@code stop-effect.<name>}), so a looping effect can run exactly while a key is
 * held.
 *
 * <p>{@code repeatCount} is the number of cycles a one-shot plays ({@code repeat=3});
 * {@link #LOOP} plays until stopped. {@code alternate} plays every other cycle
 * backwards ({@code direction=alternate}), so a loop swings instead of jumping back
 * to its start.
 */
public record EffectConfiguration(Duration duration, int repeatCount, boolean alternate,
                                  Easing easing, int areaWidth, int areaHeight,
                                  boolean followMouse, List<EffectLayer> layers) {

    public static final int LOOP = -1;

    public boolean loop() {
        return repeatCount == LOOP;
    }

    public static class EffectConfigurationBuilder {

        private Duration duration;
        private Integer repeatCount;
        private Boolean alternate;
        private Easing easing;
        private Integer areaWidth;
        private Integer areaHeight;
        private Boolean followMouse;
        private final Map<Integer, EffectLayer.EffectLayerBuilder> layerByNumber =
                new LinkedHashMap<>();

        public EffectConfigurationBuilder() {
        }

        public EffectConfigurationBuilder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public EffectConfigurationBuilder repeatCount(Integer repeatCount) {
            this.repeatCount = repeatCount;
            return this;
        }

        public EffectConfigurationBuilder alternate(Boolean alternate) {
            this.alternate = alternate;
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

        public EffectConfigurationBuilder followMouse(Boolean followMouse) {
            this.followMouse = followMouse;
            return this;
        }

        public EffectLayer.EffectLayerBuilder layer(int layerNumber) {
            return layerByNumber.computeIfAbsent(layerNumber,
                    number -> new EffectLayer.EffectLayerBuilder());
        }

        public void extend(EffectConfigurationBuilder parent) {
            if (duration == null) duration = parent.duration;
            if (repeatCount == null) repeatCount = parent.repeatCount;
            if (alternate == null) alternate = parent.alternate;
            if (easing == null) easing = parent.easing;
            if (areaWidth == null) areaWidth = parent.areaWidth;
            if (areaHeight == null) areaHeight = parent.areaHeight;
            if (followMouse == null) followMouse = parent.followMouse;
            for (Map.Entry<Integer, EffectLayer.EffectLayerBuilder> parentEntry :
                    parent.layerByNumber.entrySet())
                layer(parentEntry.getKey()).extend(parentEntry.getValue());
        }

        public EffectConfiguration build(String effectName) {
            if (layerByNumber.isEmpty())
                throw new IllegalArgumentException(
                        "Effect " + effectName + " has nothing to draw: an effect is made of" +
                        " layers, add at least effect." + effectName + ".layer1-shape=<shape>");
            Duration cycle = duration == null ? Duration.ofMillis(250) : duration;
            List<EffectLayer> layers = new ArrayList<>();
            List<Integer> layerNumbers =
                    layerByNumber.keySet().stream().sorted().toList();
            for (int i = 0; i < layerNumbers.size(); i++) {
                int layerNumber = layerNumbers.get(i);
                if (layerNumber != i + 1)
                    throw new IllegalArgumentException(
                            "Effect " + effectName + " has a layer" + layerNumber + " but no layer" +
                            (i + 1) + ": layers are numbered 1, 2, 3... without gaps");
                layers.add(layerByNumber.get(layerNumber).build(effectName, layerNumber, cycle));
            }
            return new EffectConfiguration(
                    cycle,
                    repeatCount == null ? 1 : repeatCount,
                    alternate != null && alternate,
                    easing == null ? new Easing.Polynomial(1) : easing,
                    areaWidth == null ? 100 : areaWidth,
                    areaHeight == null ? (areaWidth == null ? 100 : areaWidth) : areaHeight,
                    followMouse == null || followMouse,
                    List.copyOf(layers));
        }

    }

}
