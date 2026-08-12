package mousemaster;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Virtual keys that mousemaster presses and releases itself. They can be read in a combo
 * precondition (e.g. {@code _{isidling}}) but cannot be pressed from the configuration.
 */
public final class BuiltInVirtualKey {

    /** Pressed while the mouse is neither moving, wheeling, nor pressing a button. */
    public static final Key IS_IDLING = new Key("isidling", null, null);

    public static final Key IS_MOVING = new Key("ismoving", null, null);
    public static final Key IS_WHEELING = new Key("iswheeling", null, null);
    public static final Key IS_MOUSE_PRESSING = new Key("ismousepressing", null, null);
    public static final Key IS_LEFT_MOUSE_PRESSING =
            new Key("isleftmousepressing", null, null);
    public static final Key IS_MIDDLE_MOUSE_PRESSING =
            new Key("ismiddlemousepressing", null, null);
    public static final Key IS_RIGHT_MOUSE_PRESSING =
            new Key("isrightmousepressing", null, null);
    public static final Key IS_UNHANDLED_KEY_PRESSING =
            new Key("isunhandledkeypressing", null, null);

    public static final Key IS_WINDOWS = new Key("iswindows", null, null);
    public static final Key IS_MACOS = new Key("ismacos", null, null);

    public static final Set<Key> STATIC_KEYS =
            Set.of(IS_IDLING, IS_MOVING, IS_WHEELING, IS_MOUSE_PRESSING,
                    IS_LEFT_MOUSE_PRESSING, IS_MIDDLE_MOUSE_PRESSING,
                    IS_RIGHT_MOUSE_PRESSING, IS_UNHANDLED_KEY_PRESSING, IS_WINDOWS,
                    IS_MACOS);

    public static final Set<String> STATIC_KEY_NAMES =
            STATIC_KEYS.stream().map(Key::name).collect(Collectors.toUnmodifiableSet());

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
