package mousemaster;

import mousemaster.IndicatorLayerConfiguration.IndicatorLayerConfigurationBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

public record IndicatorConfiguration(boolean renderAsCursor, boolean excludeFromCapture,
                                     Map<String, IndicatorLayerConfiguration> layerByName) {

    public boolean enabled() {
        for (IndicatorLayerConfiguration layer : layerByName.values())
            if (layer.enabled())
                return true;
        return false;
    }

    public static class IndicatorConfigurationBuilder {

        private Boolean renderAsCursor;
        private Boolean excludeFromCapture;
        private final Map<String, IndicatorLayerConfigurationBuilder> layerByName =
                new LinkedHashMap<>();

        public IndicatorConfigurationBuilder renderAsCursor(boolean renderAsCursor) {
            this.renderAsCursor = renderAsCursor;
            return this;
        }

        public Boolean renderAsCursor() {
            return renderAsCursor;
        }

        public IndicatorConfigurationBuilder excludeFromCapture(boolean excludeFromCapture) {
            this.excludeFromCapture = excludeFromCapture;
            return this;
        }

        public Boolean excludeFromCapture() {
            return excludeFromCapture;
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
            if (excludeFromCapture == null) excludeFromCapture = parent.excludeFromCapture;
            for (Map.Entry<String, IndicatorLayerConfigurationBuilder> entry : parent.layerByName.entrySet())
                layer(entry.getKey()).extend(entry.getValue());
        }

        public IndicatorConfiguration build() {
            Map<String, IndicatorLayerConfiguration> builtLayerByName = new LinkedHashMap<>();
            for (Map.Entry<String, IndicatorLayerConfigurationBuilder> entry : layerByName.entrySet())
                builtLayerByName.put(entry.getKey(), entry.getValue().build());
            return new IndicatorConfiguration(renderAsCursor, excludeFromCapture,
                    builtLayerByName);
        }
    }
}
