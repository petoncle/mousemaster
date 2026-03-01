package mousemaster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record MacroAliasRemap(Map<Key, Key> keyRemap) {

    static Map<String, MacroAliasRemap> of(
            String remapString, Map<String, KeyAlias> keyAliases,
            KeyResolver keyResolver, String propertyType) {
        Map<String, Map<Key, Key>> remapsByAlias = new LinkedHashMap<>();
        String[] tokens = remapString.split("\\s+");
        int i = 0;
        while (i < tokens.length) {
            String token = tokens[i];
            String[] equalsParts = token.split("=", 2);
            if (equalsParts.length != 2)
                throw new IllegalArgumentException(
                        "Invalid remap token in " + propertyType + ": " + token);
            String leftHandSide = equalsParts[0];
            Key firstTargetKey = keyResolver.resolve(equalsParts[1]);
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
                Key sourceKey = keyResolver.resolve(sourceKeyName);
                if (!alias.keys().contains(sourceKey))
                    throw new IllegalArgumentException(
                            "Remap source key " + sourceKeyName +
                            " is not in " + alias);
                remapsByAlias.computeIfAbsent(aliasName, k -> new LinkedHashMap<>())
                             .put(sourceKey, firstTargetKey);
                i++;
            }
            else {
                // Broadcast form: alias=targetKey [targetKey2 ...]
                String aliasName = leftHandSide;
                KeyAlias alias = keyAliases.get(aliasName);
                if (alias == null)
                    throw new IllegalArgumentException(
                            "Remap alias " + aliasName +
                            " does not exist");
                // Collect additional target keys (tokens without '=').
                List<Key> targetKeys = new ArrayList<>();
                targetKeys.add(firstTargetKey);
                i++;
                while (i < tokens.length && !tokens[i].contains("=")) {
                    targetKeys.add(keyResolver.resolve(tokens[i]));
                    i++;
                }
                Map<Key, Key> keyRemap = remapsByAlias.computeIfAbsent(
                        aliasName, k -> new LinkedHashMap<>());
                List<Key> aliasKeys = new ArrayList<>(alias.keys());
                if (targetKeys.size() == 1) {
                    // Single target: broadcast to all alias keys.
                    for (Key key : aliasKeys)
                        keyRemap.put(key, targetKeys.getFirst());
                }
                else {
                    // Multiple targets: zip positionally with alias keys.
                    if (targetKeys.size() != aliasKeys.size())
                        throw new IllegalArgumentException(
                                "Remap for alias " + aliasName + " has " +
                                targetKeys.size() + " target keys but alias has " +
                                aliasKeys.size() + " keys");
                    for (int j = 0; j < aliasKeys.size(); j++)
                        keyRemap.put(aliasKeys.get(j), targetKeys.get(j));
                }
            }
        }
        Map<String, MacroAliasRemap> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Key, Key>> entry : remapsByAlias.entrySet())
            result.put(entry.getKey(), new MacroAliasRemap(entry.getValue()));
        return result;
    }

}
