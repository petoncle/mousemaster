package mousemaster;

import java.util.Set;

/**
 * Virtual keys that mousemaster presses and releases itself. They can be read in a combo
 * precondition (e.g. {@code _{isidling}}) but cannot be pressed from the configuration.
 */
public final class BuiltInVirtualKey {

    /**
     * Pressed while the mouse is idle: not moving, no mouse button pressed, not
     * wheeling, and no combo completed on the current update tick.
     */
    public static final Key IS_IDLING = new Key("isidling", null, null);

    public static final Set<Key> KEYS = Set.of(IS_IDLING);

    public static final Set<String> NAMES = Set.of(IS_IDLING.name());

    private BuiltInVirtualKey() {
    }

}
