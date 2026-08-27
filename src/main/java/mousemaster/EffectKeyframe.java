package mousemaster;

/**
 * One keyframe of an effect layer's timeline. {@code percent} is the position in the
 * effect's cycle (0-100). Fields left null are not constrained by this keyframe:
 * numeric fields are interpolated between the keyframes that do mention them, while
 * {@code visible} and {@code hexColor} switch when their keyframe is reached.
 * The layer's base values act as an implicit keyframe at 0%.
 */
public record EffectKeyframe(double percent, Double sizeWidth, Double sizeHeight,
                             Boolean sizeIsArea, Double opacity, Double rotation,
                             Double x, Double y, Boolean visible, String hexColor) {

}
