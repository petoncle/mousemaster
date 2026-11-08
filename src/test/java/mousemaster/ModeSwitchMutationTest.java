package mousemaster;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModeSwitchMutationTest {

    @Test
    void aValueStaysInAModeWhereTheSameComboGivesTheSameValue() {
        Configuration configuration = ConfigurationParser.parse(List.of(
                        "idle-mode.to.one-mode=+f1",
                        "idle-mode.to.other-value-mode=+f2",
                        "_base-mode.text-indicator.text= | +isidling -> shown",
                        "one-mode=_base-mode",
                        "one-mode.to.two-mode=+f3",
                        "two-mode=_base-mode",
                        "other-value-mode.text-indicator.text= | +isidling -> other"),
                KeyboardLayout.keyboardLayout("00000409", null));
        ModeMap modeMap = configuration.modeMap();
        ComboWatcher comboWatcher = comboWatcher(configuration);
        comboWatcher.modeChanged(modeMap.get("one-mode"));
        comboWatcher.setVirtualKeyPressed(BuiltInVirtualKey.IS_IDLING, true);
        assertEquals("shown", text(comboWatcher));
        comboWatcher.modeChanged(modeMap.get("two-mode"));
        assertEquals("shown", text(comboWatcher));
        comboWatcher.modeChanged(modeMap.get("other-value-mode"));
        assertEquals("", text(comboWatcher));
        comboWatcher.modeChanged(modeMap.get("one-mode"));
        assertEquals("", text(comboWatcher));
    }

    @Test
    void aTypeStaysInAModeWhereTheSameComboGivesTheSameType() {
        Configuration configuration = ConfigurationParser.parse(List.of(
                        "idle-mode.to.one-mode=+f1",
                        "one-mode.overlay.selection-keys=a b",
                        "one-mode.overlay.type=grid | +isidling -> ui-accessibility",
                        "one-mode.to.two-mode=+f3",
                        "two-mode.overlay.selection-keys=a b",
                        "two-mode.overlay.type=grid | +isidling -> ui-accessibility"),
                KeyboardLayout.keyboardLayout("00000409", null));
        ModeMap modeMap = configuration.modeMap();
        ComboWatcher comboWatcher = comboWatcher(configuration);
        comboWatcher.modeChanged(modeMap.get("one-mode"));
        comboWatcher.setVirtualKeyPressed(BuiltInVirtualKey.IS_IDLING, true);
        comboWatcher.modeChanged(modeMap.get("two-mode"));
        assertEquals(HintMeshType.HintMeshTypeType.UI_ACCESSIBILITY,
                comboWatcher.getMutatedMode().hintMesh().type().type());
    }

    @Test
    void theModeASwitchLeavesGetsTheKeysTheSwitchSets() {
        Configuration configuration = ConfigurationParser.parse(List.of(
                        "virtual-key.held=released",
                        "idle-mode.to.one-mode=+f1",
                        "one-mode.text-indicator.text= | _{held} -> held",
                        "one-mode.macro.setheld=+f2-250 -> #held",
                        "one-mode.to.two-mode=+f2-250",
                        "two-mode.to.idle-mode=+f3"),
                KeyboardLayout.keyboardLayout("00000409", null));
        ComboWatcher comboWatcher = comboWatcher(configuration);
        comboWatcher.modeChanged(configuration.modeMap().get("one-mode"));
        notifiedModes.clear();
        comboWatcher.keyEvent(new KeyEvent.PressKeyEvent(now, Key.ofName("f2")));
        now = now.plusMillis(300);
        comboWatcher.update(0.3);
        assertEquals(List.of("one-mode held", "two-mode"), notifiedModes);
    }

    private final List<String> notifiedModes = new ArrayList<>();
    private Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private ComboWatcher comboWatcher;

    private ComboWatcher comboWatcher(Configuration configuration) {
        ModeMap modeMap = configuration.modeMap();
        CommandRunner commandRunner = new CommandRunner(null, null, null) {
            @Override
            public boolean mouseJumping() {
                return false;
            }

            @Override
            public void run(Command command, Key eventKey) {
                if (command instanceof Command.SwitchMode switchMode)
                    comboWatcher.modeChanged(modeMap.get(switchMode.modeName()));
            }
        };
        comboWatcher =
                new ComboWatcher(commandRunner, new HintManager(Map.of(), null, null, null, null,
                        () -> new App("test.exe"), new KeyRedactor(KeyRedaction.NONE), null),
                        () -> new App("test.exe"), null, () -> now, Set.of(), Set.of(),
                        new KeyRedactor(KeyRedaction.NONE), modeMap,
                        configuration.initiallySetVariables(), configuration.virtualKeys(),
                        configuration.initiallyPressedVirtualKeys(),
                        configuration.activeKeyboardLayout());
        comboWatcher.setModeListeners(List.of(new ModeListener() {
            @Override
            public void modeChanged(Mode newMode) {
                IndicatorLayerConfiguration textLayer =
                        newMode.indicator().layerByName().get("text-indicator");
                notifiedModes.add(textLayer == null ? newMode.name() :
                        newMode.name() + " " + textLayer.text());
            }

            @Override
            public void modeTimedOut() {
            }
        }));
        return comboWatcher;
    }

    private static String text(ComboWatcher comboWatcher) {
        return comboWatcher.getMutatedMode().indicator().layerByName().get("text-indicator")
                           .text();
    }

}
