package mousemaster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OsBuiltInVirtualKeyTest {

    private static Configuration parse(String... lines) {
        return ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
    }

    @Test
    void oneOfThemIsTheCurrentOs() {
        assertTrue(BuiltInVirtualKey.CURRENT_OS.equals(BuiltInVirtualKey.IS_WINDOWS) ||
                   BuiltInVirtualKey.CURRENT_OS.equals(BuiltInVirtualKey.IS_MACOS));
        assertEquals(Os.macos, BuiltInVirtualKey.CURRENT_OS.equals(BuiltInVirtualKey.IS_MACOS));
    }

    @Test
    void theyAreDeclaredWithoutAVirtualKeysLine() {
        Configuration configuration =
                parse("idle-mode.mouse.initial-velocity=200 | _{ismacos} -> 400");
        assertTrue(configuration.virtualKeys().contains(BuiltInVirtualKey.IS_MACOS));
    }

    @Test
    void theyCanGateACombo() {
        Configuration configuration = parse("idle-mode.to.other-mode=_{iswindows} +a",
                "other-mode.to.idle-mode=+esc");
        assertTrue(configuration.virtualKeys().contains(BuiltInVirtualKey.IS_WINDOWS));
    }

    @Test
    void theyAreNotVariables() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("some-mode.set-variable.ismacos=+a"));
        assertTrue(e.getMessage().contains("ismacos"), e.getMessage());
    }

    @Test
    void theyCannotBePressedByAMacro() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.macro.x=+a -> #iswindows"));
        assertTrue(e.getMessage().contains("built-in"), e.getMessage());
    }
}
