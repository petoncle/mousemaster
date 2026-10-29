package mousemaster;

import mousemaster.HintMeshKeys.HintMeshKeysBuilder;
import mousemaster.HintMeshStyle.HintMeshStyleBuilder;
import mousemaster.HintMeshType.HintMeshTypeBuilder;
import mousemaster.ScreenFilterMap.ScreenFilterMapBuilder;

import java.util.List;

public record HintMeshConfiguration(boolean enabled,
                                    boolean visible,
                                    boolean mouseFollowsSelection,
                                    boolean selectionFollowsOverlayCenter,
                                    HintMeshType type,
                                    ScreenFilterMap<HintMeshKeys> keysByFilter,
                                    ScreenFilterMap<HintMeshStyle> styleByFilter,
                                    String modeAfterSelection,
                                    String savedPositionHistory,
                                    boolean eatUnusedSelectionKeys,
                                    List<Combo> selectCombos,
                                    List<Combo> deselectCombos) {

    public static class HintMeshConfigurationBuilder {
        private Boolean enabled;
        private Boolean visible;
        private Boolean mouseFollowsSelection;
        private Boolean selectionFollowsOverlayCenter;
        private final HintMeshTypeBuilder type = new HintMeshTypeBuilder();
        private final ScreenFilterMapBuilder<HintMeshKeysBuilder, HintMeshKeys>
                keysByFilter = new ScreenFilterMapBuilder<>();
        private final ScreenFilterMapBuilder<HintMeshStyleBuilder, HintMeshStyle>
                styleByFilter = new ScreenFilterMapBuilder<>();
        private String modeAfterSelection;
        private String savedPositionHistory;
        private Boolean eatUnusedSelectionKeys;
        private List<Combo> selectCombos;
        private List<Combo> deselectCombos;

        public HintMeshConfigurationBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public HintMeshConfigurationBuilder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public HintMeshConfigurationBuilder mouseFollowsSelection(boolean mouseFollowsSelection) {
            this.mouseFollowsSelection = mouseFollowsSelection;
            return this;
        }

        public HintMeshConfigurationBuilder selectionFollowsOverlayCenter(
                boolean selectionFollowsOverlayCenter) {
            this.selectionFollowsOverlayCenter = selectionFollowsOverlayCenter;
            return this;
        }

        public HintMeshKeysBuilder keys(ScreenFilter filter) {
            return keysByFilter.map()
                               .computeIfAbsent(filter,
                                       filter1 -> new HintMeshKeysBuilder());
        }

        public HintMeshStyleBuilder style(ScreenFilter filter) {
            return styleByFilter.map()
                                .computeIfAbsent(filter, filter1 -> new HintMeshStyleBuilder());
        }

        public HintMeshConfigurationBuilder savedPositionHistory(String savedPositionHistory) {
            this.savedPositionHistory = savedPositionHistory;
            return this;
        }

        public String savedPositionHistory() {
            return savedPositionHistory;
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

        public HintMeshConfigurationBuilder deselectCombos(List<Combo> deselectCombos) {
            this.deselectCombos = deselectCombos;
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

        public Boolean mouseFollowsSelection() {
            return mouseFollowsSelection;
        }

        public Boolean selectionFollowsOverlayCenter() {
            return selectionFollowsOverlayCenter;
        }

        public ScreenFilterMapBuilder<HintMeshKeysBuilder, HintMeshKeys> keysByFilter() {
            return keysByFilter;
        }

        public ScreenFilterMapBuilder<HintMeshStyleBuilder, HintMeshStyle> styleByFilter() {
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

        public List<Combo> deselectCombos() {
            return deselectCombos;
        }

        public HintMeshConfiguration build() {
            return new HintMeshConfiguration(enabled, visible, mouseFollowsSelection,
                    selectionFollowsOverlayCenter,
                    type.build(),
                    keysByFilter.build(HintMeshKeysBuilder::build),
                    styleByFilter.build(HintMeshStyleBuilder::build),
                    modeAfterSelection,
                    savedPositionHistory,
                    eatUnusedSelectionKeys,
                    selectCombos,
                    deselectCombos);
        }

    }

}
