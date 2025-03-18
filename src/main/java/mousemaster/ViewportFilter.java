package mousemaster;

public sealed interface ViewportFilter {

    static ViewportFilter of(Screen screen) {
        return new FixedViewportFilter(
                new Viewport(screen.rectangle().width(),
                        screen.rectangle().height(), screen.scale()));
    }

    enum AnyViewportFilter implements ViewportFilter {
        ANY_VIEWPORT_FILTER;
    }

    record FixedViewportFilter(Viewport viewport) implements ViewportFilter {

    }
}
