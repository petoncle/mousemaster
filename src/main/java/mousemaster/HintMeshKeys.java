package mousemaster;

import java.util.List;
import java.util.Set;

public record HintMeshKeys(List<Key> selectionKeys, int rowKeyOffset, Set<Key> undoKeys) {

    public HintMeshKeysBuilder builder() {
        return new HintMeshKeysBuilder(this);
    }

    public static class HintMeshKeysBuilder {

        private List<Key> selectionKeys;
        private Integer rowKeyOffset;
        private Set<Key> undoKeys;

        public HintMeshKeysBuilder() {

        }

        public HintMeshKeysBuilder(HintMeshKeys keys) {
            this.selectionKeys = keys.selectionKeys();
            this.rowKeyOffset = keys.rowKeyOffset();
            this.undoKeys = keys.undoKeys();
        }

        public HintMeshKeysBuilder selectionKeys(List<Key> selectionKeys) {
            this.selectionKeys = selectionKeys;
            return this;
        }

        public HintMeshKeysBuilder rowKeyOffset(Integer rowKeyOffset) {
            this.rowKeyOffset = rowKeyOffset;
            return this;
        }

        public HintMeshKeysBuilder undoKeys(Set<Key> undoKeys) {
            this.undoKeys = undoKeys;
            return this;
        }

        public List<Key> selectionKeys() {
            return selectionKeys;
        }

        public Integer rowKeyOffset() {
            return rowKeyOffset;
        }

        public Set<Key> undoKeys() {
            return undoKeys;
        }

        public HintMeshKeys build(HintMeshKeys defaultKeys) {
            return new HintMeshKeys(
                    selectionKeys == null ? defaultKeys.selectionKeys : selectionKeys,
                    rowKeyOffset == null ? defaultKeys.rowKeyOffset : rowKeyOffset,
                    undoKeys == null ? defaultKeys.undoKeys : undoKeys
            );
        }

    }

}
