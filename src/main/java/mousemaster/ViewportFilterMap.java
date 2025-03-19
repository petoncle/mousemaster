package mousemaster;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static mousemaster.ViewportFilter.*;

public class ViewportFilterMap<V> {

    private final Map<ViewportFilter, V> map;

    public ViewportFilterMap(Map<ViewportFilter, V> map) {
        this.map = map;
    }

    public V get(ViewportFilter filter) {
        return map.get(closestFilter(filter));
    }

    public ViewportFilter closestFilter(ViewportFilter filter) {
        if (map.containsKey(filter))
            return filter;
        FixedViewportFilter fixedViewportFilter = (FixedViewportFilter) filter;
        for (ViewportFilter existingFilter : map.keySet()) {
            if (existingFilter instanceof FixedViewportFilter existingFixedViewportFilter) {
                if (existingFixedViewportFilter.viewport().scale() == -1 &&
                    existingFixedViewportFilter.viewport().width() ==
                    fixedViewportFilter.viewport().width() &&
                    existingFixedViewportFilter.viewport().height() ==
                    fixedViewportFilter.viewport().height()) {
                    // Different scale.
                    return existingFixedViewportFilter;
                }
            }
        }
        return AnyViewportFilter.ANY_VIEWPORT_FILTER;
    }

    public Map<ViewportFilter, V> map() {
        return map;
    }

    public <B> ViewportFilterMapBuilder<B, V> builder(Function<V, B> elementToBuilder) {
        return new ViewportFilterMapBuilder<>(this, elementToBuilder);
    }

    public static class ViewportFilterMapBuilder<B, V> {

        /**
         * Each element is a builder (.build()).
         */
        private Map<ViewportFilter, B> map;

        public ViewportFilterMapBuilder() {
            this.map = new HashMap<>();
        }

        public ViewportFilterMapBuilder(ViewportFilterMap<V> viewportFilterMap, Function<V, B> elementToBuilder) {
            this.map = viewportFilterMap.map
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> elementToBuilder.apply(entry.getValue())
                    ));
        }

        public Map<ViewportFilter, B> map() {
            return map;
        }

        /**
         * elementBuilder takes a builder and a default value, returns the built element
         */
        public ViewportFilterMap<V> build(BiFunction<B, V, V> elementBuilder) {
            // Assumes that the default layout is not missing any property.
            V defaultValue = elementBuilder.apply(map.get(
                    AnyViewportFilter.ANY_VIEWPORT_FILTER), null);
            return new ViewportFilterMap<>(map.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> elementBuilder.apply(entry.getValue(), defaultValue)
            )));
        }

    }

}
