package mousemaster;

import java.util.List;
import java.util.Set;

public record HintMeshKeys(List<Key> selectionKeys, Set<Key> undoKeys) {

    public HintMeshKeysBuilder builder() {
        return new HintMeshKeysBuilder(this);
    }

    public static class HintMeshKeysBuilder {

        private List<Key> selectionKeys;
        private Set<Key> undoKeys;

        public HintMeshKeysBuilder() {

        }

        public HintMeshKeysBuilder(HintMeshKeys keys) {
            this.selectionKeys = keys.selectionKeys();
            this.undoKeys = keys.undoKeys();
        }

        public HintMeshKeysBuilder selectionKeys(List<Key> selectionKeys) {
            this.selectionKeys = selectionKeys;
            return this;
        }

        public HintMeshKeysBuilder undoKeys(Set<Key> undoKeys) {
            this.undoKeys = undoKeys;
            return this;
        }

        public List<Key> selectionKeys() {
            return selectionKeys;
        }

        public Set<Key> undoKeys() {
            return undoKeys;
        }

        public HintMeshKeys build(HintMeshKeys defaultKeys) {
            return new HintMeshKeys(
                    selectionKeys == null ? defaultKeys.selectionKeys : selectionKeys,
                    undoKeys == null ? defaultKeys.undoKeys : undoKeys
            );
        }

    }

}
