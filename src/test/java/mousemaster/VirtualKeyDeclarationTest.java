package mousemaster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A virtual key is declared one per line, stating whether it starts pressed or released. */
class VirtualKeyDeclarationTest {

    private static Configuration parse(String... lines) {
        return ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
    }

    private static IllegalArgumentException parseFailure(String... lines) {
        return assertThrows(IllegalArgumentException.class, () -> parse(lines));
    }

    private static boolean hasVirtualKey(Configuration configuration, String name) {
        return configuration.virtualKeys().contains(new Key(name, null, null));
    }

    @Test
    void aDeclaredKeyIsPressedByAMacroAndReadByACombo() {
        Configuration configuration = parse("virtual-key.flag=released",
                "idle-mode.macro.x=+a -> #flag",
                "idle-mode.indicator.color=#FF0000 | _{flag} -> #00FF00");
        assertTrue(hasVirtualKey(configuration, "flag"));
        assertFalse(configuration.initiallyPressedVirtualKeys()
                                 .contains(new Key("flag", null, null)));
    }

    @Test
    void anUndeclaredKeyIsRejected() {
        IllegalArgumentException e = parseFailure("virtual-key.flag=released",
                "idle-mode.macro.x=+a -> #flga");
        assertTrue(e.getMessage().contains("flga"), e.getMessage());
    }

    @Test
    void aDeclarationExemptsAKeyThatIsOnlyRead() {
        Configuration configuration = parse("virtual-key.never=released",
                "idle-mode.to.other-mode=+never",
                "other-mode.to.idle-mode=+esc");
        assertTrue(hasVirtualKey(configuration, "never"));
    }

    @Test
    void aDeclarationSaysWhetherTheKeyStartsPressed() {
        Configuration configuration = parse("virtual-key.flag=pressed",
                "idle-mode.macro.x=+a -> ~flag",
                "idle-mode.indicator.color=#FF0000 | _{flag} -> #00FF00");
        assertTrue(configuration.initiallyPressedVirtualKeys()
                                .contains(new Key("flag", null, null)));
    }

    @Test
    void anInvalidStateIsRejected() {
        IllegalArgumentException e = parseFailure("virtual-key.flag=down",
                "idle-mode.macro.x=+a -> #flag");
        assertTrue(e.getMessage().contains("pressed or released"), e.getMessage());
    }

    @Test
    void declaringTheSameKeyTwiceIsRejected() {
        IllegalArgumentException e = parseFailure("virtual-keys=flag",
                "virtual-key.flag=pressed",
                "idle-mode.macro.x=+a -> #flag");
        assertTrue(e.getMessage().contains("declared twice"), e.getMessage());
    }

    @Test
    void theVirtualKeysLineStillWorks() {
        Configuration configuration = parse("virtual-keys=+flag other",
                "idle-mode.macro.x=+a -> #flag ~other");
        assertTrue(hasVirtualKey(configuration, "flag"));
        assertTrue(hasVirtualKey(configuration, "other"));
        assertTrue(configuration.initiallyPressedVirtualKeys()
                                .contains(new Key("flag", null, null)));
    }
}
