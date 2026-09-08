package mousemaster;

import java.util.Map;

/**
 * One keyframe of an effect layer's timeline. {@code percent} is the position in the
 * layer's cycle (0-100). {@code values} holds only the properties this keyframe
 * pins (see {@link EffectProperty} for how each kind is interpolated); properties it
 * does not mention are not constrained by it. {@code sizeIsArea} is the
 * {@code size=area} keyword. The layer's base values act as an implicit keyframe at
 * 0%. {@code easing} shapes the interpolation of the segment that ends at this
 * keyframe (null = linear).
 */
public record EffectKeyframe(double percent, Map<EffectProperty, Object> values,
                             Boolean sizeIsArea, Easing easing) {

    public EffectKeyframe {
        values = Map.copyOf(values);
    }

}
