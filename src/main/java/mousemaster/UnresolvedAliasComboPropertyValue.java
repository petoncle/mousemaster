package mousemaster;

import java.util.List;
import java.util.Map;

/**
 * Stored as {@link Command.MutateMode#newPropertyValue()} when the combo property
 * value string contains alias names that must be resolved to key labels at combo completion time.
 * An optional {@code remapByAliasName} maps alias keys to arbitrary string values
 * (instead of the default {@link Key#hintLabel()}).
 */
public record UnresolvedAliasComboPropertyValue(List<String> aliasNames,
                                                Map<String, AliasRemap<String>> remapByAliasName) {}
