package mousemaster;

import mousemaster.platform.ActiveAppFinder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A macro that only presses and releases virtual keys waits on its own clock: several play at
 * once, pressing one again starts it over, and a reset plays what is left of it.
 */
class VirtualKeyMacroTest {

    private ComboWatcher comboWatcher;
    private MacroPlayer macroPlayer;
    private Map<String, Macro> macroByName;

    private void load(String... lines) {
        Configuration configuration = ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
        ModeMap modeMap = configuration.modeMap();
        Set<Key> pressedPreconditionKeys = new HashSet<>();
        for (Mode mode : modeMap.modes())
            for (Combo combo : mode.comboMap().commandsByCombo().keySet())
                pressedPreconditionKeys.addAll(combo.precondition()
                                                    .keyPrecondition()
                                                    .pressedKeyPrecondition()
                                                    .allKeys());
        comboWatcher = new ComboWatcher(null, null, () -> new App("test.exe"), null,
                Instant::now, Set.of(), pressedPreconditionKeys, new KeyRedactor(KeyRedaction.NONE), modeMap,
                configuration.initiallySetVariables(), configuration.virtualKeys(),
                configuration.initiallyPressedVirtualKeys());
        comboWatcher.setModeListeners(List.of());
        comboWatcher.modeChanged(modeMap.get(Mode.IDLE_MODE_NAME));
        macroPlayer = new MacroPlayer(Instant::now, comboWatcher, null, null,
                new KeyRedactor(KeyRedaction.NONE));
        macroByName = modeMap.get(Mode.IDLE_MODE_NAME)
                             .comboMap()
                             .commandsByCombo()
                             .values()
                             .stream()
                             .flatMap(List::stream)
                             .filter(Command.MacroCommand.class::isInstance)
                             .map(command -> ((Command.MacroCommand) command).macro())
                             .collect(java.util.stream.Collectors.toMap(Macro::name,
                                     macro -> macro));
    }

    private void submit(String macroName) {
        macroPlayer.submit(macroByName.get(macroName).resolve(new AliasResolution(Map.of())));
    }

    private Color color() {
        return comboWatcher.getMutatedMode().indicator().color();
    }

    private int size() {
        return comboWatcher.getMutatedMode().indicator().size();
    }

    @Test
    void oneMacroDoesNotHoldBackAnother() {
        load("virtual-keys=flag other",
                "idle-mode.macro.x=+a -> #flag wait-100 ~flag",
                "idle-mode.macro.y=+b -> #other wait-100 ~other",
                "idle-mode.indicator.color=#FF0000 | _{flag} -> #00FF00",
                "idle-mode.indicator.size=26 | _{other} -> 42");
        submit("x");
        submit("y");
        macroPlayer.update(0.01);
        assertEquals(Color.parse("#00FF00"), color());
        assertEquals(42, size(), "the second macro played while the first was waiting");

        macroPlayer.update(0.1);
        assertEquals(Color.parse("#FF0000"), color());
        assertEquals(26, size());
    }

    @Test
    void pressingTheSameMacroAgainStartsItOver() {
        load("virtual-keys=flag",
                "idle-mode.macro.x=+a -> #flag wait-100 ~flag",
                "idle-mode.indicator.color=#FF0000 | _{flag} -> #00FF00");
        submit("x");
        macroPlayer.update(0.01);
        macroPlayer.update(0.05);
        submit("x");
        macroPlayer.update(0.06);
        assertEquals(Color.parse("#00FF00"), color(), "the first release must not end the second press");

        macroPlayer.update(0.1);
        assertEquals(Color.parse("#FF0000"), color());
    }

    @Test
    void aResetReleasesWhatIsStillPressed() {
        load("virtual-keys=flag",
                "idle-mode.macro.x=+a -> #flag wait-100 ~flag",
                "idle-mode.indicator.color=#FF0000 | _{flag} -> #00FF00");
        submit("x");
        macroPlayer.update(0.01);
        assertEquals(Color.parse("#00FF00"), color());

        macroPlayer.reset();
        assertEquals(Color.parse("#FF0000"), color());
    }
}
