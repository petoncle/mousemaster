package mousemaster;

import java.util.List;
import java.util.Map;

public record AliasResolution(Map<String, Key> negatedKeyByName,
                              Map<String, List<Key>> keysByAlias) {

    public AliasResolution(Map<String, Key> negatedKeyByName) {
        this(negatedKeyByName, Map.of());
    }

}
