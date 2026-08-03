package mousemaster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** isidling is a virtual key mousemaster presses itself: readable, but not writable. */
class IsIdlingBuiltInVirtualKeyTest {

    private static Configuration parse(String... lines) {
        return ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
    }

    @Test
    void isidlingIsDeclaredWithoutAVirtualKeysLine() {
        Configuration configuration =
                parse("idle-mode.indicator.render-as-cursor=false | _{isidling} -> true");
        assertTrue(configuration.virtualKeys().contains(BuiltInVirtualKey.IS_IDLING));
        assertFalse(configuration.initiallyPressedVirtualKeys()
                                 .contains(BuiltInVirtualKey.IS_IDLING));
    }

    @Test
    void isidlingIsNotAVariable() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("some-mode.set-variable.isidling=+a"));
        assertTrue(e.getMessage().contains("isidling"), e.getMessage());
    }

    @Test
    void isidlingCannotBePressedByAMacro() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.macro.x=+a -> #isidling"));
        assertTrue(e.getMessage().contains("built-in"), e.getMessage());
    }
}
