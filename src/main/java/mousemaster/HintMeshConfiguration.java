package mousemaster;

import mousemaster.HintMeshKeys.HintMeshKeysBuilder;
import mousemaster.HintMeshStyle.HintMeshStyleBuilder;
import mousemaster.HintMeshType.HintMeshTypeBuilder;
import mousemaster.ViewportFilterMap.ViewportFilterMapBuilder;

import java.util.List;

public record HintMeshConfiguration(boolean enabled,
                                    boolean visible,
                                    HintMouseMovement mouseMovement,
                                    HintMeshType type,
                                    ViewportFilterMap<HintMeshKeys> keysByFilter,
                                    ViewportFilterMap<HintMeshStyle> styleByFilter,
                                    String modeAfterSelection,
                                    boolean eatUnusedSelectionKeys,
                                    List<Combo> selectCombos) {

    public static class HintMeshConfigurationBuilder {
        private Boolean enabled;
        private Boolean visible;
        private HintMouseMovement mouseMovement;
        private final HintMeshTypeBuilder type = new HintMeshTypeBuilder();
        private final ViewportFilterMapBuilder<HintMeshKeysBuilder, HintMeshKeys>
                keysByFilter = new ViewportFilterMapBuilder<>();
        private final ViewportFilterMapBuilder<HintMeshStyleBuilder, HintMeshStyle>
                styleByFilter = new ViewportFilterMapBuilder<>();
        private String modeAfterSelection;
        private Boolean eatUnusedSelectionKeys;
        private List<Combo> selectCombos;

        public HintMeshConfigurationBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public HintMeshConfigurationBuilder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public HintMeshConfigurationBuilder mouseMovement(HintMouseMovement mouseMovement) {
            this.mouseMovement = mouseMovement;
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

        public HintMeshConfigurationBuilder eatUnusedSelectionKeys(
                boolean eatUnusedSelectionKeys) {
            this.eatUnusedSelectionKeys = eatUnusedSelectionKeys;
            return this;
        }

        public HintMeshConfigurationBuilder selectCombos(List<Combo> selectCombos) {
            this.selectCombos = selectCombos;
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

        public HintMouseMovement mouseMovement() {
            return mouseMovement;
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

        public Boolean eatUnusedSelectionKeys() {
            return eatUnusedSelectionKeys;
        }

        public List<Combo> selectCombos() {
            return selectCombos;
        }

        public HintMeshConfiguration build() {
            return new HintMeshConfiguration(enabled, visible, mouseMovement,
                    type.build(),
                    keysByFilter.build(HintMeshKeysBuilder::build),
                    styleByFilter.build(HintMeshStyleBuilder::build),
                    modeAfterSelection,
                    eatUnusedSelectionKeys,
                    selectCombos);
        }

    }

}
