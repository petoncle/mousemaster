package mousemaster;

import mousemaster.IndicatorLayerConfiguration.IndicatorLayerConfigurationBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

public record IndicatorConfiguration(boolean renderAsCursor,
                                     Map<String, IndicatorLayerConfiguration> layerByName) {

    public static class IndicatorConfigurationBuilder {

        private Boolean renderAsCursor;
        private final Map<String, IndicatorLayerConfigurationBuilder> layerByName =
                new LinkedHashMap<>();

        public IndicatorConfigurationBuilder renderAsCursor(boolean renderAsCursor) {
            this.renderAsCursor = renderAsCursor;
            return this;
        }

        public Boolean renderAsCursor() {
            return renderAsCursor;
        }

        public Map<String, IndicatorLayerConfigurationBuilder> layerByName() {
            return layerByName;
        }

        public IndicatorLayerConfigurationBuilder layer(String name) {
            return layerByName.computeIfAbsent(name,
                    name_ -> new IndicatorLayerConfigurationBuilder());
        }

        public void extend(IndicatorConfigurationBuilder parent) {
            if (renderAsCursor == null) renderAsCursor = parent.renderAsCursor;
            for (Map.Entry<String, IndicatorLayerConfigurationBuilder> entry : parent.layerByName.entrySet())
                layer(entry.getKey()).extend(entry.getValue());
        }

        public IndicatorConfiguration build() {
            Map<String, IndicatorLayerConfiguration> builtLayerByName = new LinkedHashMap<>();
            for (Map.Entry<String, IndicatorLayerConfigurationBuilder> entry : layerByName.entrySet())
                builtLayerByName.put(entry.getKey(), entry.getValue().build());
            return new IndicatorConfiguration(renderAsCursor, builtLayerByName);
        }
    }
}
