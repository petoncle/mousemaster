package mousemaster;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public sealed interface ScreenFilter {

    // 1920x1080, 1920x1080-100%, or 100%
    Pattern filterPattern = Pattern.compile("(\\d+)x(\\d+)(?:-(\\d+)%)?|(\\d+)%");

    boolean matches(Screen screen);

    static ScreenFilter of(Screen screen) {
        return new FixedScreenFilter(screen.rectangle().width(),
                screen.rectangle().height(), screen.scale());
    }

    /** Null if the string is not a screen filter. */
    static ScreenFilter of(String string) {
        Matcher matcher = filterPattern.matcher(string);
        if (!matcher.matches())
            return null;
        if (matcher.group(4) != null)
            return new FixedScreenFilter(-1, -1, scale(matcher.group(4)));
        return new FixedScreenFilter(
                Integer.parseUnsignedInt(matcher.group(1)),
                Integer.parseUnsignedInt(matcher.group(2)),
                matcher.group(3) == null ? -1 : scale(matcher.group(3)));
    }

    private static double scale(String percent) {
        return Integer.parseUnsignedInt(percent) / 100d;
    }

    enum AnyScreenFilter implements ScreenFilter {
        ANY_SCREEN_FILTER;

        @Override
        public boolean matches(Screen screen) {
            return true;
        }
    }

    /** An unspecified width, height or scale is -1. */
    record FixedScreenFilter(int width, int height, double scale) implements ScreenFilter {

        @Override
        public boolean matches(Screen screen) {
            return (width == -1 || width == screen.rectangle().width() &&
                                   height == screen.rectangle().height()) &&
                   (scale == -1 || scale == screen.scale());
        }

        @Override
        public String toString() {
            String resolution = width == -1 ? "" : width + "x" + height;
            String scalePercent = scale == -1 ? "" : Math.round(scale * 100) + "%";
            if (resolution.isEmpty() || scalePercent.isEmpty())
                return resolution + scalePercent;
            return resolution + "-" + scalePercent;
        }
    }
}
