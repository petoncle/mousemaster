package mousemaster;

import java.util.List;

/**
 * Unlike a grid, it does not necessarily have fixed-size cells.
 *
 * decoration = the whole-area decoration (the grid drawn as one big cell).
 * subDecoration = the per-cell decoration tiled into each cell (its own
 * subDecoration is the next depth: subsubdecoration).
 */
public record HintMesh(boolean visible, List<Hint> hints, int prefixLength,
                       List<Key> selectedKeySequence,
                       ViewportFilterMap<HintMeshStyle> styleByFilter,
                       Rectangle backgroundArea, HintMesh decoration,
                       HintMesh subDecoration) {

    public HintMeshBuilder builder() {
        return new HintMeshBuilder(this);
    }

    public static class HintMeshBuilder {
        private boolean visible;
        private List<Hint> hints;
        private int prefixLength;
        private List<Key> selectedKeySequence = List.of();
        private ViewportFilterMap<HintMeshStyle> styleByFilter;
        private Rectangle backgroundArea;
        private HintMesh decoration;
        private HintMesh subDecoration;

        public HintMeshBuilder() {
        }

        public HintMeshBuilder(HintMesh hintMesh) {
            this.visible = hintMesh.visible;
            this.hints = hintMesh.hints;
            this.prefixLength = hintMesh.prefixLength;
            this.selectedKeySequence = hintMesh.selectedKeySequence;
            this.styleByFilter = hintMesh.styleByFilter;
            this.backgroundArea = hintMesh.backgroundArea;
            this.decoration = hintMesh.decoration;
            this.subDecoration = hintMesh.subDecoration;
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

        public ViewportFilterMap<HintMeshStyle> styleByFilter() {
            return styleByFilter;
        }

        public HintMeshBuilder visible(boolean visible) {
            this.visible = visible;
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
                ViewportFilterMap<HintMeshStyle> styleByFilter) {
            this.styleByFilter = styleByFilter;
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

        public HintMesh build() {
            return new HintMesh(visible, hints, prefixLength, selectedKeySequence,
                    styleByFilter, backgroundArea, decoration, subDecoration);
        }
    }

}
