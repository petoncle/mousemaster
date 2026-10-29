package mousemaster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NoneComboTest {

    private static Mode mode(String modeName, String... lines) {
        return ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null)).modeMap().get(modeName);
    }

    private static boolean has(Mode mode, Class<? extends Command> commandType) {
        return mode.comboMap().commandsByCombo().values().stream()
                   .flatMap(List::stream)
                   .anyMatch(commandType::isInstance);
    }

    @Test
    void noneRemovesTheParentCombos() {
        Mode mode = mode("normal-mode", "idle-mode.to.normal-mode=+leftshift",
                "_a-mode.press.middle=+m",
                "_a-mode.press.left=+l",
                "normal-mode=_a-mode",
                "normal-mode.press.middle=none");
        assertFalse(has(mode, Command.PressMiddle.class));
        assertTrue(has(mode, Command.PressLeft.class));
    }

    @Test
    void theFirstParentToSetNoneKeepsIt() {
        assertFalse(has(mode("normal-mode", "idle-mode.to.normal-mode=+leftshift",
                "_a-mode.press.middle=+m",
                "_b-mode.press.middle=none",
                "normal-mode=_b-mode _a-mode"), Command.PressMiddle.class));
    }

    @Test
    void hintSelectNoneSelectsNothing() {
        assertFalse(has(mode("hint-mode", "idle-mode.to.hint-mode=+leftshift",
                "hint-mode.to.idle-mode=+esc",
                "hint-mode.hint.type=grid",
                "hint-mode.hint.selection-keys=a b c d",
                "hint-mode.select=none"), Command.SelectHintKey.class));
    }

    @Test
    void hintSelectNoneNeedsNoSelectionKeys() {
        assertFalse(has(mode("hint-mode", "idle-mode.to.hint-mode=+leftshift",
                "hint-mode.to.idle-mode=+esc",
                "hint-mode.hint.type=grid",
                "hint-mode.select=none"), Command.SelectHintKey.class));
    }

}
