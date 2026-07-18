package mousemaster;

import java.util.Set;

/**
 * Variable names that mousemaster maintains automatically, as opposed to
 * user-defined variables set via set-variable. They can be referenced in combo
 * variable preconditions (e.g. {@code _{isidling}}) just like user variables,
 * but they cannot be set, unset, or cleared from the configuration.
 */
public final class BuiltInVariable {

    /**
     * Set while the mouse is idle: not moving, no mouse button pressed, not
     * wheeling, and no combo completed on the current update tick.
     */
    public static final String IS_IDLING = "isidling";

    public static final Set<String> NAMES = Set.of(IS_IDLING);

    private BuiltInVariable() {
    }

}
