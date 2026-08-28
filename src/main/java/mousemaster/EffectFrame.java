package mousemaster;

import java.util.List;

/**
 * One fully-resolved frame of a running effect: what to draw right now, with all
 * keyframe interpolation already applied. The renderer clips the layers to the
 * area, which is centered on the mouse position. Keeping the resolution here (in
 * {@link EffectManager}) leaves the renderer a dumb draw loop and makes the
 * animation logic testable without a UI.
 */
public record EffectFrame(int areaWidth, int areaHeight,
                          List<ResolvedEffectLayer> layers) {

    /** A layer with its animated values resolved for the current frame time. */
    public record ResolvedEffectLayer(EffectShape shape, double x, double y,
                                      double width, double height, double rotation,
                                      double rotationX, double rotationY,
                                      String hexColor, double opacity,
                                      boolean filled, double thickness) {

    }

}
