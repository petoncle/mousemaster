package mousemaster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyKeyTest {

    @Test
    void aKeyWithoutAModeIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ConfigurationParser.parse(List.of("idle-mode.to.normal-mode=+a",
                                "normal-mode.to.idle-mode=+b", "normal.to.idle-mode=+q"),
                        KeyboardLayout.keyboardLayout("00000409", null)));
        assertTrue(e.getMessage().contains("normal.to.idle-mode"), e.getMessage());
    }

    @Test
    void anUnknownCommandSuffixIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigurationParser.parse(List.of("idle-mode.press.foo=+a"),
                        KeyboardLayout.keyboardLayout("00000409", null)));
    }

    @Test
    void anUnknownLogLevelIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigurationParser.parse(List.of("logging.level=bogus",
                                "idle-mode.indicator.enabled=true"),
                        KeyboardLayout.keyboardLayout("00000409", null)));
    }

}
