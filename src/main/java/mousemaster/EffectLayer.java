package mousemaster;

import java.util.List;

/**
 * One layer of an effect: a single shape with a position (relative to the effect
 * area's center, which follows the mouse), a size, and an optional keyframe
 * timeline animating it over the effect's cycle. Layers are drawn in declaration
 * order: layer1 first (bottom), then layer2 on top of it, and so on.
 *
 * <p>{@code sizeIsArea} marks {@code size=area}: the layer takes the size of the
 * whole effect area (a filled square layer sized to the area is the effect's
 * background).
 */
public record EffectLayer(EffectShape shape, double x, double y,
                          double sizeWidth, double sizeHeight, boolean sizeIsArea,
                          double rotation, String hexColor, double opacity,
                          boolean filled, double thickness,
                          List<EffectKeyframe> keyframes) {

    public static class EffectLayerBuilder {

        private EffectShape shape;
        private Double x;
        private Double y;
        private Double sizeWidth;
        private Double sizeHeight;
        private Boolean sizeIsArea;
        private Double rotation;
        private String hexColor;
        private Double opacity;
        private Boolean filled;
        private Double thickness;
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

        public EffectLayerBuilder x(Double x) {
            this.x = x;
            return this;
        }

        public EffectLayerBuilder y(Double y) {
            this.y = y;
            return this;
        }

        public EffectLayerBuilder size(Double sizeWidth, Double sizeHeight,
                                       Boolean sizeIsArea) {
            this.sizeWidth = sizeWidth;
            this.sizeHeight = sizeHeight;
            this.sizeIsArea = sizeIsArea;
            return this;
        }

        public EffectLayerBuilder rotation(Double rotation) {
            this.rotation = rotation;
            return this;
        }

        public EffectLayerBuilder hexColor(String hexColor) {
            this.hexColor = hexColor;
            return this;
        }

        public EffectLayerBuilder opacity(Double opacity) {
            this.opacity = opacity;
            return this;
        }

        public EffectLayerBuilder filled(Boolean filled) {
            this.filled = filled;
            return this;
        }

        public EffectLayerBuilder thickness(Double thickness) {
            this.thickness = thickness;
            return this;
        }

        public EffectLayerBuilder keyframes(List<EffectKeyframe> keyframes) {
            this.keyframes = keyframes;
            return this;
        }

        public void extend(EffectLayerBuilder parent) {
            if (shape == null) shape = parent.shape;
            if (x == null) x = parent.x;
            if (y == null) y = parent.y;
            if (sizeWidth == null) sizeWidth = parent.sizeWidth;
            if (sizeHeight == null) sizeHeight = parent.sizeHeight;
            if (sizeIsArea == null) sizeIsArea = parent.sizeIsArea;
            if (rotation == null) rotation = parent.rotation;
            if (hexColor == null) hexColor = parent.hexColor;
            if (opacity == null) opacity = parent.opacity;
            if (filled == null) filled = parent.filled;
            if (thickness == null) thickness = parent.thickness;
            if (keyframes == null) keyframes = parent.keyframes;
        }

        public EffectLayer build(String effectName, int layerNumber) {
            if (shape == null)
                throw new IllegalArgumentException(
                        "Effect " + effectName + " layer" + layerNumber +
                        " has no shape: expected effect." + effectName + ".layer" +
                        layerNumber + "-shape=<dot|circle|square|triangle|line|cross>");
            return new EffectLayer(shape,
                    x == null ? 0 : x,
                    y == null ? 0 : y,
                    sizeWidth == null ? 16 : sizeWidth,
                    sizeHeight == null ? (sizeWidth == null ? 16 : sizeWidth) : sizeHeight,
                    sizeIsArea != null && sizeIsArea,
                    rotation == null ? 0 : rotation,
                    hexColor == null ? "#FFFFFF" : hexColor,
                    opacity == null ? 1.0 : opacity,
                    filled == null ? shape == EffectShape.DOT : filled,
                    thickness == null ? 1 : thickness,
                    keyframes == null ? List.of() : keyframes);
        }

    }

}
