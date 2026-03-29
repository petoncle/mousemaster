package mousemaster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public record AliasRemap<T>(Map<Key, T> remap) {

    static Map<String, AliasRemap<Key>> keyValues(
            String remapString, Map<String, KeyAlias> keyAliases,
            KeyResolver keyResolver, String propertyType) {
        Map<String, Map<Key, Key>> remapsByAlias =
                parseRemap(remapString, keyAliases, keyResolver,
                        keyResolver::resolve, propertyType);
        Map<String, AliasRemap<Key>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Key, Key>> entry : remapsByAlias.entrySet())
            result.put(entry.getKey(), new AliasRemap<>(entry.getValue()));
        return result;
    }

    private static <T> Map<String, Map<Key, T>> parseRemap(
            String remapString, Map<String, KeyAlias> keyAliases,
            KeyResolver keyResolver, Function<String, T> targetValueParser,
            String propertyType) {
        Map<String, Map<Key, T>> remapsByAlias = new LinkedHashMap<>();
        String[] tokens = remapString.split("\\s+");
        int i = 0;
        while (i < tokens.length) {
            String token = tokens[i];
            String[] equalsParts = token.split("=", 2);
            if (equalsParts.length != 2)
                throw new IllegalArgumentException(
                        "Invalid remap token in " + propertyType + ": " + token);
            String leftHandSide = equalsParts[0];
            T firstTargetValue = targetValueParser.apply(equalsParts[1]);
            int dotIndex = leftHandSide.indexOf('.');
            if (dotIndex >= 0) {
                // Per-key form: alias.sourceKey=targetValue
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
                             .put(sourceKey, firstTargetValue);
                i++;
            }
            else {
                // Broadcast or positional: alias=value1 [value2 ...]
                String aliasName = leftHandSide;
                KeyAlias alias = keyAliases.get(aliasName);
                if (alias == null)
                    throw new IllegalArgumentException(
                            "Remap alias " + aliasName +
                            " does not exist");
                List<T> targetValues = new ArrayList<>();
                targetValues.add(firstTargetValue);
                i++;
                while (i < tokens.length && !tokens[i].contains("=")) {
                    targetValues.add(targetValueParser.apply(tokens[i]));
                    i++;
                }
                Map<Key, T> keyRemap = remapsByAlias.computeIfAbsent(
                        aliasName, k -> new LinkedHashMap<>());
                List<Key> aliasKeys = new ArrayList<>(alias.keys());
                if (targetValues.size() == 1) {
                    for (Key key : aliasKeys)
                        keyRemap.put(key, targetValues.getFirst());
                }
                else {
                    if (targetValues.size() != aliasKeys.size())
                        throw new IllegalArgumentException(
                                "Remap for alias " + aliasName + " has " +
                                targetValues.size() + " values but alias has " +
                                aliasKeys.size() + " keys");
                    for (int j = 0; j < aliasKeys.size(); j++)
                        keyRemap.put(aliasKeys.get(j), targetValues.get(j));
                }
            }
        }
        return remapsByAlias;
    }

    static Map<String, AliasRemap<String>> stringValues(
            String remapString, Map<String, KeyAlias> keyAliases,
            KeyResolver keyResolver, String propertyType) {
        Map<String, Map<Key, String>> remapsByAlias =
                parseRemap(remapString, keyAliases, keyResolver,
                        Function.identity(), propertyType);
        Map<String, AliasRemap<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Key, String>> entry : remapsByAlias.entrySet())
            result.put(entry.getKey(), new AliasRemap<>(entry.getValue()));
        return result;
    }

}
