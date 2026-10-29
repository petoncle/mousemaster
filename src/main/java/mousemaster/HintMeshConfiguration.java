package mousemaster;

import mousemaster.HintMeshKeys.HintMeshKeysBuilder;
import mousemaster.HintMeshStyle.HintMeshStyleBuilder;
import mousemaster.HintMeshType.HintMeshTypeBuilder;
import mousemaster.ScreenFilterMap.ScreenFilterMapBuilder;

public record HintMeshConfiguration(boolean enabled,
                                    boolean visible,
                                    boolean mouseFollowsTarget,
                                    boolean targetFollowsOverlayCenter,
                                    HintMeshType type,
                                    ScreenFilterMap<HintMeshKeys> keysByFilter,
                                    ScreenFilterMap<HintMeshStyle> styleByFilter,
                                    String modeAfterSelection,
                                    String savedPositionHistory,
                                    boolean eatUnusedSelectionKeys) {

    public static class HintMeshConfigurationBuilder {
        private Boolean enabled;
        private Boolean visible;
        private Boolean mouseFollowsTarget;
        private Boolean targetFollowsOverlayCenter;
        private final HintMeshTypeBuilder type = new HintMeshTypeBuilder();
        private final ScreenFilterMapBuilder<HintMeshKeysBuilder, HintMeshKeys>
                keysByFilter = new ScreenFilterMapBuilder<>();
        private final ScreenFilterMapBuilder<HintMeshStyleBuilder, HintMeshStyle>
                styleByFilter = new ScreenFilterMapBuilder<>();
        private String modeAfterSelection;
        private String savedPositionHistory;
        private Boolean eatUnusedSelectionKeys;

        public HintMeshConfigurationBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public HintMeshConfigurationBuilder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public HintMeshConfigurationBuilder mouseFollowsTarget(boolean mouseFollowsTarget) {
            this.mouseFollowsTarget = mouseFollowsTarget;
            return this;
        }

        public HintMeshConfigurationBuilder targetFollowsOverlayCenter(
                boolean targetFollowsOverlayCenter) {
            this.targetFollowsOverlayCenter = targetFollowsOverlayCenter;
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

        public HintMeshTypeBuilder type() {
            return type;
        }

        public Boolean enabled() {
            return enabled;
        }

        public Boolean visible() {
            return visible;
        }

        public Boolean mouseFollowsTarget() {
            return mouseFollowsTarget;
        }

        public Boolean targetFollowsOverlayCenter() {
            return targetFollowsOverlayCenter;
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

        public HintMeshConfiguration build() {
            return new HintMeshConfiguration(enabled, visible, mouseFollowsTarget,
                    targetFollowsOverlayCenter,
                    type.build(),
                    keysByFilter.build(HintMeshKeysBuilder::build),
                    styleByFilter.build(HintMeshStyleBuilder::build),
                    modeAfterSelection,
                    savedPositionHistory,
                    eatUnusedSelectionKeys);
        }

    }

}
