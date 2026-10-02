package mousemaster;

import java.time.Duration;
import java.util.List;

public record Timeline(String animationName, List<Keyframe> keyframes) {

    public record Keyframe(Position position, Object value, Easing easing) {
    }

    public sealed interface Position {

        double percent(Duration animationDuration);

        record PercentPosition(double percent) implements Position {
            @Override
            public double percent(Duration animationDuration) {
                return percent;
            }
        }

        record DurationPosition(Duration duration) implements Position {
            @Override
            public double percent(Duration animationDuration) {
                return (double) duration.toNanos() / animationDuration.toNanos();
            }
        }

    }

    public record Current() {
    }

    public Object valueAt(double progress, Duration duration, Object current) {
        double percent = Math.clamp(progress, 0, 1);
        Object from = current;
        double fromPercent = 0;
        for (Keyframe keyframe : keyframes) {
            Object to = keyframe.value instanceof Current ? current : keyframe.value;
            double toPercent = keyframe.position.percent(duration);
            if (percent <= toPercent) {
                if (toPercent == fromPercent)
                    return to;
                return interpolate(from, to, keyframe.easing.apply(
                        (percent - fromPercent) / (toPercent - fromPercent)));
            }
            from = to;
            fromPercent = toPercent;
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
