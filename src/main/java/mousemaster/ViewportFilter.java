package mousemaster;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public sealed interface ViewportFilter {

    // 1920x1080, 1920x1080-100%, or 100%
    Pattern filterPattern = Pattern.compile("(\\d+)x(\\d+)(?:-(\\d+)%)?|(\\d+)%");

    static ViewportFilter of(Screen screen) {
        return new FixedViewportFilter(screen.viewport());
    }

    /** Null if the string is not a viewport filter. */
    static ViewportFilter of(String string) {
        Matcher matcher = filterPattern.matcher(string);
        if (!matcher.matches())
            return null;
        if (matcher.group(4) != null)
            return new FixedViewportFilter(
                    new Viewport(-1, -1, scale(matcher.group(4))));
        return new FixedViewportFilter(new Viewport(
                Integer.parseUnsignedInt(matcher.group(1)),
                Integer.parseUnsignedInt(matcher.group(2)),
                matcher.group(3) == null ? -1 : scale(matcher.group(3))));
    }

    private static double scale(String percent) {
        return Integer.parseUnsignedInt(percent) / 100d;
    }

    enum AnyViewportFilter implements ViewportFilter {
        ANY_VIEWPORT_FILTER;
    }

    /** An unspecified width, height or scale is -1. */
    record FixedViewportFilter(Viewport viewport) implements ViewportFilter {

    }

    /** Not a {@link ViewportFilterMap} key: a mutation of every entry but these. */
    record NegatedViewportFilter(Set<ViewportFilter> viewportFilters)
            implements ViewportFilter {

    }
}
