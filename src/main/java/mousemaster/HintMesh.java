package mousemaster;

import java.util.List;

/**
 * Unlike a grid, it does not necessarily have fixed-size cells.
 */
public record HintMesh(boolean visible, List<Hint> hints, List<Key> focusedKeySequence,
                       HintMeshStyle style) {

    public HintMeshBuilder builder() {
        return new HintMeshBuilder(this);
    }

    public static class HintMeshBuilder {
        private boolean visible;
        private List<Hint> hints;
        private List<Key> focusedKeySequence = List.of();
        private HintMeshStyle.HintMeshStyleBuilder style;

        public HintMeshBuilder() {
        }

        public HintMeshBuilder(HintMesh hintMesh) {
            this.visible = hintMesh.visible;
            this.hints = hintMesh.hints;
            this.focusedKeySequence = hintMesh.focusedKeySequence;
            this.style = hintMesh.style.builder();
        }


        public boolean visible() {
            return visible;
        }

        public List<Hint> hints() {
            return hints;
        }

        public List<Key> focusedKeySequence() {
            return focusedKeySequence;
        }

        public HintMeshStyle.HintMeshStyleBuilder style() {
            return style;
        }

        public HintMeshBuilder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public HintMeshBuilder hints(List<Hint> hints) {
            this.hints = hints;
            return this;
        }

        public HintMeshBuilder focusedKeySequence(List<Key> focusedKeySequence) {
            this.focusedKeySequence = focusedKeySequence;
            return this;
        }

        public HintMeshBuilder style(HintMeshStyle style) {
            this.style = style.builder();
            return this;
        }

        public HintMesh build() {
            return new HintMesh(visible, hints, focusedKeySequence, style.build());
        }
    }

}
