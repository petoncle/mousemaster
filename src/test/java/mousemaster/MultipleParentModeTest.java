package mousemaster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A mode can extend several modes: the first one to define a property keeps it. */
class MultipleParentModeTest {

    private static Mode mode(String modeName, String... lines) {
        return ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null)).modeMap().get(modeName);
    }

    private static List<String> macroNames(Mode mode) {
        return mode.comboMap().commandsByCombo().values().stream()
                   .flatMap(List::stream)
                   .filter(Command.MacroCommand.class::isInstance)
                   .map(command -> ((Command.MacroCommand) command).macro().name())
                   .distinct().sorted().toList();
    }

    @Test
    void eachParentContributesItsOwnProperties() {
        Mode mode = mode("normal-mode", "virtual-keys=flag",
                "idle-mode.to.normal-mode=+leftshift",
                "_a-mode.indicator.size=42",
                "_b-mode.macro.x=+a -> #flag",
                "normal-mode=_a-mode _b-mode");
        assertEquals(42, mode.indicator().size());
        assertEquals(List.of("x"), macroNames(mode));
    }

    @Test
    void theFirstParentToDefineAPropertyKeepsIt() {
        assertEquals(42, mode("normal-mode", "idle-mode.to.normal-mode=+leftshift",
                "_a-mode.indicator.size=42",
                "_b-mode.indicator.size=26",
                "normal-mode=_a-mode _b-mode").indicator().size());
        assertEquals(26, mode("normal-mode", "idle-mode.to.normal-mode=+leftshift",
                "_a-mode.indicator.size=42",
                "_b-mode.indicator.size=26",
                "normal-mode=_b-mode _a-mode").indicator().size());
    }

    @Test
    void theModeItselfBeatsItsParents() {
        assertEquals(10, mode("normal-mode", "idle-mode.to.normal-mode=+leftshift",
                "_a-mode.indicator.size=42",
                "_b-mode.indicator.size=26",
                "normal-mode=_a-mode _b-mode",
                "normal-mode.indicator.size=10").indicator().size());
    }

    @Test
    void aSharedGrandparentIsAppliedOnce() {
        Mode mode = mode("normal-mode", "virtual-keys=flag",
                "idle-mode.to.normal-mode=+leftshift",
                "_grandparent-mode.macro.x=+a -> #flag",
                "_a-mode=_grandparent-mode",
                "_b-mode=_grandparent-mode",
                "normal-mode=_a-mode _b-mode");
        assertEquals(List.of("x"), macroNames(mode));
        assertEquals(1, mode.comboMap().commandsByCombo().values().stream()
                            .flatMap(List::stream).count());
    }

    @Test
    void aParentThatIsNotDefinedIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mode("normal-mode", "idle-mode.to.normal-mode=+leftshift",
                        "_a-mode.indicator.size=42",
                        "normal-mode=_a-mode _missing-mode"));
        assertTrue(e.getMessage().contains("_missing-mode"), e.getMessage());
    }

    @Test
    void aPropertyReferenceStillShadowsEveryParent() {
        Mode mode = mode("normal-mode", "virtual-keys=flag",
                "idle-mode.to.normal-mode=+leftshift",
                "_a-mode.macro.x=+a -> #flag",
                "_b-mode.macro.y=+b -> #flag",
                "_c-mode.macro.z=+c -> #flag",
                "normal-mode=_a-mode _b-mode",
                "normal-mode.macro=_c-mode.macro");
        assertEquals(List.of("z"), macroNames(mode));
    }
}
