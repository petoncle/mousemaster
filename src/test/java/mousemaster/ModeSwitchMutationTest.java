package mousemaster;

import org.junit.jupiter.api.Test;

import java.time.Instant;
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

    private static ComboWatcher comboWatcher(Configuration configuration) {
        ComboWatcher comboWatcher =
                new ComboWatcher(null, new HintManager(Map.of(), null, null, null, null,
                        () -> new App("test.exe"), new KeyRedactor(KeyRedaction.NONE), null),
                        () -> new App("test.exe"), null, Instant::now, Set.of(), Set.of(),
                        new KeyRedactor(KeyRedaction.NONE), configuration.modeMap(),
                        configuration.initiallySetVariables(), configuration.virtualKeys(),
                        configuration.initiallyPressedVirtualKeys(),
                        configuration.activeKeyboardLayout());
        comboWatcher.setModeListeners(List.of(new ModeListener() {
            @Override
            public void modeChanged(Mode newMode) {
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
