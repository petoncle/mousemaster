package mousemaster;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stored as {@link Command.MutateMode#newPropertyValue()} when the combo property
 * value string contains alias or negated names that must be resolved to key labels
 * at combo completion time.
 * Names in {@code nameNegatedSet} are resolved via {@link AliasResolution#negatedKeyByName()};
 * the rest are resolved via {@link AliasResolution#keysByAlias()}.
 * An optional {@code remapByName} maps keys to arbitrary string values
 * (instead of the default {@link Key#hintLabel()}).
 */
public record UnresolvedAliasComboPropertyValue(List<String> names,
                                                Set<String> nameNegatedSet,
                                                Map<String, AliasRemap<String>> remapByName) {}
