package mousemaster;

import mousemaster.HintMeshKeys.HintMeshKeysBuilder;
import mousemaster.HintMeshStyle.HintMeshStyleBuilder;
import mousemaster.HintMeshType.HintMeshTypeBuilder;
import mousemaster.ViewportFilterMap.ViewportFilterMapBuilder;

public record HintMeshConfiguration(boolean enabled,
                                    boolean visible,
                                    boolean moveMouse,
                                    HintMeshType type,
                                    ViewportFilterMap<HintMeshKeys> keysByFilter,
                                    ViewportFilterMap<HintMeshStyle> styleByFilter,
                                    String modeAfterSelection,
                                    boolean savePositionAfterSelection) {

    public static class HintMeshConfigurationBuilder {
        private Boolean enabled;
        private Boolean visible;
        private Boolean moveMouse;
        private final HintMeshTypeBuilder type = new HintMeshTypeBuilder();
        private final ViewportFilterMapBuilder<HintMeshKeysBuilder, HintMeshKeys>
                keysByFilter = new ViewportFilterMapBuilder<>();
        private final ViewportFilterMapBuilder<HintMeshStyleBuilder, HintMeshStyle>
                styleByFilter = new ViewportFilterMapBuilder<>();
        private String modeAfterSelection;
        private Boolean savePositionAfterSelection;

        public HintMeshConfigurationBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public HintMeshConfigurationBuilder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public HintMeshConfigurationBuilder moveMouse(boolean moveMouse) {
            this.moveMouse = moveMouse;
            return this;
        }

        public HintMeshKeysBuilder keys(ViewportFilter filter) {
            return keysByFilter.map()
                               .computeIfAbsent(filter,
                                       filter1 -> new HintMeshKeysBuilder());
        }

        public HintMeshStyleBuilder style(ViewportFilter filter) {
            return styleByFilter.map()
                                .computeIfAbsent(filter, filter1 -> new HintMeshStyleBuilder());
        }

        public HintMeshConfigurationBuilder modeAfterSelection(
                String modeAfterSelection) {
            this.modeAfterSelection = modeAfterSelection;
            return this;
        }

        public HintMeshConfigurationBuilder savePositionAfterSelection(
                boolean savePositionAfterSelection) {
            this.savePositionAfterSelection = savePositionAfterSelection;
            return this;
        }

        public HintMeshTypeBuilder type() {
            return type;
        }

        public Boolean enabled() {
            return enabled;
        }

        public Boolean visible() {
            return visible;
        }

        public Boolean moveMouse() {
            return moveMouse;
        }

        public ViewportFilterMapBuilder<HintMeshKeysBuilder, HintMeshKeys> keysByFilter() {
            return keysByFilter;
        }

        public ViewportFilterMapBuilder<HintMeshStyleBuilder, HintMeshStyle> styleByFilter() {
            return styleByFilter;
        }

        public String modeAfterSelection() {
            return modeAfterSelection;
        }

        public Boolean savePositionAfterSelection() {
            return savePositionAfterSelection;
        }

        public HintMeshConfiguration build() {
            return new HintMeshConfiguration(enabled, visible, moveMouse,
                    type.build(),
                    keysByFilter.build(HintMeshKeysBuilder::build),
                    styleByFilter.build(HintMeshStyleBuilder::build),
                    modeAfterSelection,
                    savePositionAfterSelection);
        }

    }

}
