package mousemaster;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ViewportFilterMap<V> {

    private final Map<ViewportFilter, V> map;

    public ViewportFilterMap(Map<ViewportFilter, V> map) {
        this.map = map;
    }

    public V get(ViewportFilter filter) {
        V layout = map.get(filter);
        if (layout != null)
            return layout;
        return map.get(
                ViewportFilter.AnyViewportFilter.ANY_VIEWPORT_FILTER);
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
                    ViewportFilter.AnyViewportFilter.ANY_VIEWPORT_FILTER), null);
            return new ViewportFilterMap<>(map.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> elementBuilder.apply(entry.getValue(), defaultValue)
            )));
        }

    }

}
