package mousemaster;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Only the virtual key moves before a macro's first other move have their state applied in the
 * command batch.
 */
class LeadingVirtualKeyMoveTest {

    private static final Set<Key> virtualKeys =
            Set.of(new Key("flag", null, null), new Key("other", null, null));

    private static Macro macro(String... lines) {
        Configuration configuration = ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
        return configuration.modeMap()
                            .get(Mode.IDLE_MODE_NAME)
                            .comboMap()
                            .commandsByCombo()
                            .values()
                            .stream()
                            .flatMap(List::stream)
                            .filter(Command.MacroCommand.class::isInstance)
                            .map(command -> ((Command.MacroCommand) command).macro())
                            .findFirst()
                            .orElseThrow();
    }

    private static List<String> leadingMoves(Macro macro) {
        return macro.leadingVirtualKeyMoves(virtualKeys).stream().map(Object::toString).toList();
    }

    @Test
    void leadingRunStopsAtTheFirstOtherMove() {
        Macro macro = macro("virtual-keys=flag other",
                "idle-mode.macro.x=+a -> #flag ~other +b ~flag");
        assertEquals(List.of("#flag", "~other"), leadingMoves(macro));
    }

    @Test
    void wholeOutputCanBeLeading() {
        Macro macro = macro("virtual-keys=flag other",
                "idle-mode.macro.x=+a -> #flag ~other");
        assertEquals(List.of("#flag", "~other"), leadingMoves(macro));
    }

    @Test
    void aVirtualKeyMoveAfterAnotherMoveIsNotLeading() {
        Macro macro = macro("virtual-keys=flag", "idle-mode.macro.x=+a -> +b #flag");
        assertEquals(List.of(), leadingMoves(macro));
    }

    @Test
    void aRealKeyMoveIsNeverLeading() {
        Macro macro = macro("virtual-keys=flag", "idle-mode.macro.x=+a -> #b #flag");
        assertEquals(List.of(), leadingMoves(macro));
    }

    @Test
    void noVirtualKeysDeclaredMeansNoLeadingMoves() {
        Macro macro = macro("idle-mode.macro.x=+a -> #b");
        assertEquals(List.of(), macro.leadingVirtualKeyMoves(Set.of()));
    }
}
