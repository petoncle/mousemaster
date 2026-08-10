package mousemaster;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static mousemaster.ScreenFilter.*;

public class ScreenFilterMap<V> {

    private final Map<ScreenFilter, V> map;

    public ScreenFilterMap(Map<ScreenFilter, V> map) {
        this.map = map;
    }

    public V get(ScreenFilter filter) {
        return map.get(closestFilter(filter));
    }

    public ScreenFilter closestFilter(ScreenFilter filter) {
        if (map.containsKey(filter))
            return filter;
        FixedScreenFilter fixedFilter = (FixedScreenFilter) filter;
        FixedScreenFilter resolutionFilter = new FixedScreenFilter(
                fixedFilter.width(), fixedFilter.height(), -1);
        if (map.containsKey(resolutionFilter))
            return resolutionFilter;
        FixedScreenFilter scaleFilter =
                new FixedScreenFilter(-1, -1, fixedFilter.scale());
        if (map.containsKey(scaleFilter))
            return scaleFilter;
        return AnyScreenFilter.ANY_SCREEN_FILTER;
    }

    public Map<ScreenFilter, V> map() {
        return map;
    }

    public <B> ScreenFilterMapBuilder<B, V> builder(Function<V, B> elementToBuilder) {
        return new ScreenFilterMapBuilder<>(this, elementToBuilder);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;

        ScreenFilterMap<?> that = (ScreenFilterMap<?>) o;
        return map.equals(that.map);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    public static class ScreenFilterMapBuilder<B, V> {

        /**
         * Each element is a builder (.build()).
         */
        private Map<ScreenFilter, B> map;

        public ScreenFilterMapBuilder() {
            this.map = new HashMap<>();
        }

        public ScreenFilterMapBuilder(ScreenFilterMap<V> screenFilterMap, Function<V, B> elementToBuilder) {
            this.map = screenFilterMap.map
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> elementToBuilder.apply(entry.getValue())
                    ));
        }

        public Map<ScreenFilter, B> map() {
            return map;
        }

        /**
         * elementBuilder takes a builder and a default value, returns the built element
         */
        public ScreenFilterMap<V> build(BiFunction<B, V, V> elementBuilder) {
            // Assumes that the default layout is not missing any property.
            V defaultValue = elementBuilder.apply(map.get(
                    AnyScreenFilter.ANY_SCREEN_FILTER), null);
            return new ScreenFilterMap<>(map.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> elementBuilder.apply(entry.getValue(), defaultValue)
            )));
        }

    }

}
