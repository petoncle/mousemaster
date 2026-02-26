package mousemaster;

import java.util.List;
import java.util.Map;

public record AliasResolution(Map<String, Key> keyByAliasName,
                              Map<String, Key> negatedKeyByName,
                              Map<String, List<Key>> keysByTapExpandedFromAlias) {

    public AliasResolution(Map<String, Key> keyByAliasName,
                           Map<String, Key> negatedKeyByName) {
        this(keyByAliasName, negatedKeyByName, Map.of());
    }

}
