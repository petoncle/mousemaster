package mousemaster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A property that references itself around a loop is never built, so it must not parse. */
class PropertyReferenceCycleTest {

    private static IllegalArgumentException parseFailure(String... lines) {
        return assertThrows(IllegalArgumentException.class,
                () -> ConfigurationParser.parse(List.of(lines),
                        KeyboardLayout.keyboardLayout("00000409", null)));
    }

    @Test
    void aReferenceCycleIsRejected() {
        IllegalArgumentException e = parseFailure("virtual-keys=flag",
                "idle-mode.macro=_a-mode.macro",
                "_a-mode.macro=_b-mode.macro",
                "_b-mode.macro=_a-mode.macro",
                "_a-mode.macro.x=+a -> #flag");
        assertTrue(e.getMessage().contains("cycle"), e.getMessage());
    }

    @Test
    void aModeInheritanceCycleIsRejected() {
        IllegalArgumentException e = parseFailure("idle-mode.to.a-mode=+leftshift",
                "idle-mode.to.b-mode=+leftctrl",
                "a-mode=b-mode",
                "b-mode=a-mode");
        assertTrue(e.getMessage().contains("cycle"), e.getMessage());
    }

    @Test
    void aReferenceToAModeWithNoPropertiesIsRejected() {
        IllegalArgumentException e = parseFailure("virtual-keys=flag",
                "idle-mode.macro=_missing-mode.macro",
                "idle-mode.macro.x=+a -> #flag");
        assertTrue(e.getMessage().contains("_missing-mode"), e.getMessage());
    }

    @Test
    void anInheritanceOfAModeWithNoPropertiesIsRejected() {
        IllegalArgumentException e = parseFailure("idle-mode.to.a-mode=+leftshift",
                "a-mode=_missing-mode");
        assertTrue(e.getMessage().contains("_missing-mode"), e.getMessage());
    }

    @Test
    void aReferenceChainIsNotACycle() {
        Configuration configuration = ConfigurationParser.parse(List.of("virtual-keys=flag",
                        "idle-mode.macro=_a-mode.macro",
                        "_a-mode.macro=_b-mode.macro",
                        "_b-mode.macro.x=+a -> #flag"),
                KeyboardLayout.keyboardLayout("00000409", null));
        assertFalse(configuration.modeMap()
                                 .get(Mode.IDLE_MODE_NAME)
                                 .comboMap()
                                 .commandsByCombo()
                                 .isEmpty(), "the chained macro must reach idle-mode");
    }
}
