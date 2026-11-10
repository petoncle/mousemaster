package mousemaster;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unlike a grid, it does not necessarily have fixed-size cells.
 *
 * decoration = the whole-area decoration (the grid drawn as one big cell).
 * subDecoration = the per-cell decoration tiled into each cell (its own
 * subDecoration is the next depth: subsubdecoration).
 */
public record HintMesh(boolean visible, boolean excludeFromCapture, List<Hint> hints,
                       int prefixLength, List<Key> selectedKeySequence,
                       ScreenFilterMap<HintMeshStyle> styleByFilter,
                       Rectangle area, Rectangle backgroundArea, HintMesh decoration,
                       HintMesh subDecoration, List<Hint> savedHints,
                       Map<List<Key>, Point> hotspotByKeySequence) {

    public HintMeshBuilder builder() {
        return new HintMeshBuilder(this);
    }

    public Point hotspot(Hint hint) {
        return hotspotByKeySequence.getOrDefault(hint.keySequence(),
                new Point(hint.centerX(), hint.centerY()));
    }

    public HintMesh withTransitionsOf(HintMesh other) {
        Map<ScreenFilter, HintMeshStyle> newStyleByFilter = new HashMap<>();
        styleByFilter.map().forEach((filter, style) -> newStyleByFilter.put(filter,
                style.withTransitionsOf(other.styleByFilter.get(filter))));
        return builder()
                .styleByFilter(new ScreenFilterMap<>(newStyleByFilter))
                .decoration(decoration == null || other.decoration == null ? decoration :
                        decoration.withTransitionsOf(other.decoration))
                .subDecoration(subDecoration == null || other.subDecoration == null ?
                        subDecoration : subDecoration.withTransitionsOf(other.subDecoration))
                .build();
    }

    public static class HintMeshBuilder {
        private boolean visible;
        private boolean excludeFromCapture;
        private List<Hint> hints;
        private int prefixLength;
        private List<Key> selectedKeySequence = List.of();
        private ScreenFilterMap<HintMeshStyle> styleByFilter;
        private Rectangle area;
        private Rectangle backgroundArea;
        private HintMesh decoration;
        private HintMesh subDecoration;
        private List<Hint> savedHints = List.of();
        private Map<List<Key>, Point> hotspotByKeySequence = Map.of();

        public HintMeshBuilder() {
        }

        public HintMeshBuilder(HintMesh hintMesh) {
            this.visible = hintMesh.visible;
            this.excludeFromCapture = hintMesh.excludeFromCapture;
            this.hints = hintMesh.hints;
            this.prefixLength = hintMesh.prefixLength;
            this.selectedKeySequence = hintMesh.selectedKeySequence;
            this.styleByFilter = hintMesh.styleByFilter;
            this.area = hintMesh.area;
            this.backgroundArea = hintMesh.backgroundArea;
            this.decoration = hintMesh.decoration;
            this.subDecoration = hintMesh.subDecoration;
            this.savedHints = hintMesh.savedHints;
            this.hotspotByKeySequence = hintMesh.hotspotByKeySequence;
        }


        public boolean visible() {
            return visible;
        }

        public List<Hint> hints() {
            return hints;
        }

        public int prefixLength() {
            return prefixLength;
        }

        public List<Key> selectedKeySequence() {
            return selectedKeySequence;
        }

        public ScreenFilterMap<HintMeshStyle> styleByFilter() {
            return styleByFilter;
        }

        public HintMeshBuilder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public HintMeshBuilder excludeFromCapture(boolean excludeFromCapture) {
            this.excludeFromCapture = excludeFromCapture;
            return this;
        }

        public HintMeshBuilder hints(List<Hint> hints) {
            this.hints = hints;
            return this;
        }

        public HintMeshBuilder prefixLength(int prefixLength) {
            this.prefixLength = prefixLength;
            return this;
        }

        public HintMeshBuilder selectedKeySequence(List<Key> selectedKeySequence) {
            this.selectedKeySequence = selectedKeySequence;
            return this;
        }

        public HintMeshBuilder styleByFilter(
                ScreenFilterMap<HintMeshStyle> styleByFilter) {
            this.styleByFilter = styleByFilter;
            return this;
        }

        public Rectangle area() {
            return area;
        }

        public HintMeshBuilder area(Rectangle area) {
            this.area = area;
            return this;
        }

        public Rectangle backgroundArea() {
            return backgroundArea;
        }

        public HintMeshBuilder backgroundArea(Rectangle backgroundArea) {
            this.backgroundArea = backgroundArea;
            return this;
        }

        public HintMesh decoration() {
            return decoration;
        }

        public HintMeshBuilder decoration(HintMesh decoration) {
            this.decoration = decoration;
            return this;
        }

        public HintMesh subDecoration() {
            return subDecoration;
        }

        public HintMeshBuilder subDecoration(HintMesh subDecoration) {
            this.subDecoration = subDecoration;
            return this;
        }

        public HintMeshBuilder savedHints(List<Hint> savedHints) {
            this.savedHints = savedHints;
            return this;
        }

        public HintMeshBuilder hotspotByKeySequence(Map<List<Key>, Point> hotspotByKeySequence) {
            this.hotspotByKeySequence = hotspotByKeySequence;
            return this;
        }

        public HintMesh build() {
            return new HintMesh(visible, excludeFromCapture, hints, prefixLength,
                    selectedKeySequence, styleByFilter, area, backgroundArea, decoration,
                    subDecoration, savedHints, hotspotByKeySequence);
        }
    }

}
