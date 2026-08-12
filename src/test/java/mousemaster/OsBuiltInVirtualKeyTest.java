package mousemaster;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OsBuiltInVirtualKeyTest {

    private static Configuration parse(String... lines) {
        return ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
    }

    @Test
    void theRunningOneIsPressedAndTheOtherIsNot() {
        Configuration configuration = parse(
                "idle-mode.mouse.initial-velocity=200 | _{iswindows} -> 1 | _{ismacos} -> 2");
        ComboWatcher comboWatcher =
                new ComboWatcher(null, null, () -> new App("test.exe"), null,
                        (Clock) Instant::now, Set.of(), Set.of(), false,
                        configuration.modeMap(), configuration.initiallySetVariables(),
                        configuration.virtualKeys(),
                        configuration.initiallyPressedVirtualKeys());
        comboWatcher.setModeListeners(List.of(new ModeListener() {
            @Override
            public void modeChanged(Mode newMode) {
            }

            @Override
            public void modeTimedOut() {
            }
        }));
        comboWatcher.modeChanged(configuration.modeMap().get(Mode.IDLE_MODE_NAME));
        assertEquals(Os.macos ? 2 : 1,
                comboWatcher.getMutatedMode().mouse().velocity().initialVelocity());
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
