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

    public static final Key IS_WINDOWS = new Key("iswindows", null, null);
    public static final Key IS_MACOS = new Key("ismacos", null, null);

    public static final Set<Key> STATIC_KEYS = Set.of(IS_IDLING, IS_WINDOWS, IS_MACOS);

    public static final Set<String> STATIC_KEY_NAMES =
            Set.of(IS_IDLING.name(), IS_WINDOWS.name(), IS_MACOS.name());

    public static boolean isBuiltIn(String keyName) {
        return STATIC_KEY_NAMES.contains(keyName) || ScreenFilter.of(keyName) != null;
    }

    /** Pressed while the active screen matches the filter. */
    public static Key screenFilterKey(ScreenFilter screenFilter) {
        return new Key(screenFilter.toString(), null, null);
    }

    /** The filter a screen filter key is pressed on, or null if the key is not one. */
    public static ScreenFilter screenFilter(Key key) {
        return ScreenFilter.of(key.name());
    }

    private BuiltInVirtualKey() {
    }

}
