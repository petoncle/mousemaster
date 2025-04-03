package mousemaster;

import java.util.List;

/**
 * Unlike a grid, it does not necessarily have fixed-size cells.
 */
public record HintMesh(boolean visible, List<Hint> hints, int prefixLength,
                       List<Key> selectedKeySequence,
                       ViewportFilterMap<HintMeshStyle> styleByFilter) {

    public HintMeshBuilder builder() {
        return new HintMeshBuilder(this);
    }

    public static class HintMeshBuilder {
        private boolean visible;
        private List<Hint> hints;
        private int prefixLength;
        private List<Key> selectedKeySequence = List.of();
        private ViewportFilterMap<HintMeshStyle> styleByFilter;

        public HintMeshBuilder() {
        }

        public HintMeshBuilder(HintMesh hintMesh) {
            this.visible = hintMesh.visible;
            this.hints = hintMesh.hints;
            this.prefixLength = hintMesh.prefixLength;
            this.selectedKeySequence = hintMesh.selectedKeySequence;
            this.styleByFilter = hintMesh.styleByFilter;
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

        public HintMesh build() {
            return new HintMesh(visible, hints, prefixLength, selectedKeySequence,
                    styleByFilter);
        }
    }

}
