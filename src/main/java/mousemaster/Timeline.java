package mousemaster;

import java.util.List;

public record Timeline(String animationName, List<Keyframe> keyframes) {

    public record Keyframe(double percent, Object value, Easing easing) {
    }

    public record Current() {
    }

    public Object valueAt(double progress, Object current) {
        double percent = Math.clamp(progress, 0, 1);
        Object from = current;
        double fromPercent = 0;
        for (Keyframe keyframe : keyframes) {
            Object to = keyframe.value instanceof Current ? current : keyframe.value;
            if (percent <= keyframe.percent) {
                if (keyframe.percent == fromPercent)
                    return to;
                return interpolate(from, to, keyframe.easing.apply(
                        (percent - fromPercent) / (keyframe.percent - fromPercent)));
            }
            from = to;
            fromPercent = keyframe.percent;
        }
        return from;
    }

    private static Object interpolate(Object from, Object to, double t) {
        return switch (from) {
            case Integer integer -> (int) Math.round(integer + ((Integer) to - integer) * t);
            case Double number -> number + ((Double) to - number) * t;
            case GradientColor color when to instanceof GradientColor toColor &&
                                          color.hexColors().size() ==
                                          toColor.hexColors().size() ->
                    color.mix(toColor, t);
            default -> t < 1 ? from : to;
        };
    }

}
