package mousemaster;

import java.util.List;
import java.util.Set;

public record HintMeshKeys(List<Key> selectionKeys, int rowKeyOffset) {

    public HintMeshKeysBuilder builder() {
        return new HintMeshKeysBuilder(this);
    }

    public static class HintMeshKeysBuilder {

        private List<Key> selectionKeys;
        private Integer rowKeyOffset;

        public HintMeshKeysBuilder() {

        }

        public HintMeshKeysBuilder(HintMeshKeys keys) {
            this.selectionKeys = keys.selectionKeys();
            this.rowKeyOffset = keys.rowKeyOffset();
        }

        public HintMeshKeysBuilder selectionKeys(List<Key> selectionKeys) {
            this.selectionKeys = selectionKeys;
            return this;
        }

        public HintMeshKeysBuilder rowKeyOffset(Integer rowKeyOffset) {
            this.rowKeyOffset = rowKeyOffset;
            return this;
        }

        public List<Key> selectionKeys() {
            return selectionKeys;
        }

        public Integer rowKeyOffset() {
            return rowKeyOffset;
        }

        public HintMeshKeys build(HintMeshKeys defaultKeys) {
            return new HintMeshKeys(
                    selectionKeys == null ? defaultKeys.selectionKeys : selectionKeys,
                    rowKeyOffset == null ? defaultKeys.rowKeyOffset : rowKeyOffset
            );
        }

    }

}
