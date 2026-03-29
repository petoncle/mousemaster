package mousemaster;

import java.util.List;

/**
 * Stored as {@link Command.MutateMode#newPropertyValue()} when the combo property
 * value string contains alias names that must be resolved to key labels at combo completion time.
 */
public record UnresolvedAliasComboPropertyValue(List<String> aliasNames) {}
