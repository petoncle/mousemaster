package mousemaster;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record MacroAliasRemap(Map<Key, Key> keyRemap) {

    static Map<String, MacroAliasRemap> of(
            String remapString, Map<String, KeyAlias> keyAliases,
            KeyResolver keyResolver, Set<String> layoutDependentAliasNames,
            String propertyType) {
        Map<String, Map<Key, Key>> remapsByAlias = new LinkedHashMap<>();
        for (String token : remapString.split("\\s+")) {
            String[] equalsParts = token.split("=", 2);
            if (equalsParts.length != 2)
                throw new IllegalArgumentException(
                        "Invalid remap token in " + propertyType + ": " + token);
            String leftHandSide = equalsParts[0];
            Key targetKey = keyResolver.resolve(equalsParts[1]);
            int dotIndex = leftHandSide.indexOf('.');
            if (dotIndex >= 0) {
                // Per-key form: alias.sourceKey=targetKey
                String aliasName = leftHandSide.substring(0, dotIndex);
                String sourceKeyName = leftHandSide.substring(dotIndex + 1);
                KeyAlias alias = keyAliases.get(aliasName);
                if (alias == null)
                    throw new IllegalArgumentException(
                            "Remap alias " + aliasName +
                            " does not exist");
                if (layoutDependentAliasNames.contains(aliasName))
                    throw new IllegalArgumentException(
                            "Layout-dependent alias " + aliasName +
                            " cannot be used in remap");
                Key sourceKey = keyResolver.resolve(sourceKeyName);
                if (!alias.keys().contains(sourceKey))
                    throw new IllegalArgumentException(
                            "Source key " + sourceKeyName +
                            " is not in alias " + aliasName);
                remapsByAlias.computeIfAbsent(aliasName, k -> new LinkedHashMap<>())
                             .put(sourceKey, targetKey);
            }
            else {
                // Broadcast form: alias=targetKey
                String aliasName = leftHandSide;
                KeyAlias alias = keyAliases.get(aliasName);
                if (alias == null)
                    throw new IllegalArgumentException(
                            "Remap alias " + aliasName +
                            " does not exist");
                if (layoutDependentAliasNames.contains(aliasName))
                    throw new IllegalArgumentException(
                            "Layout-dependent alias " + aliasName +
                            " cannot be used in remap");
                Map<Key, Key> keyRemap = remapsByAlias.computeIfAbsent(
                        aliasName, k -> new LinkedHashMap<>());
                for (Key key : alias.keys())
                    keyRemap.put(key, targetKey);
            }
        }
        Map<String, MacroAliasRemap> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Key, Key>> entry : remapsByAlias.entrySet())
            result.put(entry.getKey(), new MacroAliasRemap(entry.getValue()));
        return result;
    }

}
