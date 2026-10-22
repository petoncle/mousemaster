package mousemaster;

import java.util.Set;

/**
 * Pressing the modified key while one of the modifier keys is held types the typed key: on
 * us-qwerty, / with leftshift or rightshift types ?.
 */
public record ModifiedKeyPress(Key modifiedKey, Set<Key> modifierKeys, Key typedKey) {
}
