package mousemaster;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record UnresolvedAliasComboPropertyValue(String text,
                                                Map<String, AliasRemap<String>> remapByName) {

    static final Pattern placeholderPattern = Pattern.compile("\\{(!?)([^}]*)}");

    String resolve(AliasResolution resolution) {
        return placeholderPattern.matcher(text).replaceAll(match -> {
            String name = match.group(2);
            List<Key> keys = match.group(1).isEmpty() ?
                    resolution.keysByAlias().getOrDefault(name, List.of()) :
                    Stream.ofNullable(resolution.negatedKeyByName().get(name)).toList();
            AliasRemap<String> remap = remapByName.get(name);
            return Matcher.quoteReplacement(keys.stream()
                                                .map(key -> remap == null ? key.hintLabel() :
                                                        remap.remap().getOrDefault(key,
                                                                key.hintLabel()))
                                                .collect(Collectors.joining()));
        });
    }

}
