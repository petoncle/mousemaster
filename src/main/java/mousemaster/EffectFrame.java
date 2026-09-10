package mousemaster;

import java.util.List;

/**
 * One fully-resolved frame of a running effect: what to draw right now, with all
 * keyframe interpolation already applied. The renderer clips the layers to the
 * area, which is centered on {@code anchor} when the effect does not follow the
 * mouse, and on the mouse position otherwise ({@code anchor} null). Keeping the
 * resolution here (in {@link EffectManager}) leaves the renderer a dumb draw loop
 * and makes the animation logic testable without a UI.
 */
public record EffectFrame(int areaWidth, int areaHeight, Point anchor,
                          List<ResolvedEffectLayer> layers) {

    /**
     * A layer with its animated values resolved for the current frame time. The
     * scale is already applied to the width and height; the pivot is absolute (in
     * effect coordinates) and defaults to the layer's own position.
     */
    public record ResolvedEffectLayer(EffectShape shape, double x, double y,
                                      double width, double height,
                                      double rotation, double rotationX,
                                      double rotationY, double pivotX, double pivotY,
                                      String hexColor, double opacity, boolean filled,
                                      double thickness, double cornerRadius,
                                      int edgeCount, double arcStart, double arcSweep,
                                      double dashLength, double dashGap,
                                      double dashOffset) {

    }

}
