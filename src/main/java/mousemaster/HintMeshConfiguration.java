package mousemaster;

import mousemaster.HintMeshStyle.HintMeshStyleBuilder;
import mousemaster.HintMeshType.HintMeshTypeBuilder;
import mousemaster.ViewportFilterMap.ViewportFilterMapBuilder;

import java.util.List;
import java.util.Set;

public record HintMeshConfiguration(boolean enabled,
                                    boolean visible,
                                    boolean moveMouse,
                                    HintMeshType type,
                                    List<Key> selectionKeys, Set<Key> undoKeys,
                                    ViewportFilterMap<HintMeshStyle> styleByFilter,
                                    String modeAfterSelection,
                                    boolean swallowHintEndKeyPress,
                                    boolean savePositionAfterSelection) {

    public static class HintMeshConfigurationBuilder {
        private Boolean enabled;
        private Boolean visible;
        private Boolean moveMouse;
        private final HintMeshTypeBuilder type = new HintMeshTypeBuilder();
        private List<Key> selectionKeys;
        private Set<Key> undoKeys;
        private final ViewportFilterMapBuilder<HintMeshStyleBuilder, HintMeshStyle>
                styleByFilter = new ViewportFilterMapBuilder<>();
        private String modeAfterSelection;
        private Boolean swallowHintEndKeyPress;
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

        public HintMeshConfigurationBuilder selectionKeys(List<Key> selectionKeys) {
            this.selectionKeys = selectionKeys;
            return this;
        }

        public HintMeshConfigurationBuilder undoKeys(Set<Key> undoKeys) {
            this.undoKeys = undoKeys;
            return this;
        }

        public HintMeshConfigurationBuilder modeAfterSelection(
                String modeAfterSelection) {
            this.modeAfterSelection = modeAfterSelection;
            return this;
        }

        public HintMeshConfigurationBuilder swallowHintEndKeyPress(
                boolean swallowHintEndKeyPress) {
            this.swallowHintEndKeyPress = swallowHintEndKeyPress;
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

        public List<Key> selectionKeys() {
            return selectionKeys;
        }

        public Set<Key> undoKeys() {
            return undoKeys;
        }

        public ViewportFilterMapBuilder<HintMeshStyleBuilder, HintMeshStyle> styleByFilter() {
            return styleByFilter;
        }

        public HintMeshStyleBuilder style(ViewportFilter filter) {
            return styleByFilter.map()
                                .computeIfAbsent(filter, filter1 -> new HintMeshStyleBuilder());
        }

        public String modeAfterSelection() {
            return modeAfterSelection;
        }

        public Boolean swallowHintEndKeyPress() {
            return swallowHintEndKeyPress;
        }

        public Boolean savePositionAfterSelection() {
            return savePositionAfterSelection;
        }

        public HintMeshConfiguration build() {
            return new HintMeshConfiguration(enabled, visible, moveMouse,
                    type.build(),
                    selectionKeys, undoKeys,
                    styleByFilter.build(HintMeshStyleBuilder::build),
                    modeAfterSelection, swallowHintEndKeyPress,
                    savePositionAfterSelection);
        }

    }

}
