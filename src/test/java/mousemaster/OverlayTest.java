package mousemaster;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OverlayTest {

    private static HintMeshConfiguration overlay(String modeName, String... lines) {
        return ConfigurationParser.parse(List.of(lines),
                                          KeyboardLayout.keyboardLayout("00000409", null))
                                  .modeMap().get(modeName).hintMesh();
    }

    @Test
    void anOverlayCanBeExcludedFromCapture() {
        assertEquals(false, overlay("grid-mode", "idle-mode.to.grid-mode=+g",
                "grid-mode.overlay.selection-keys=a b c d").excludeFromCapture());
        assertEquals(true, overlay("grid-mode", "idle-mode.to.grid-mode=+g",
                "grid-mode.overlay.exclude-from-capture=true").excludeFromCapture());
    }

    @Test
    void hintIsOverlay() {
        assertEquals(
                overlay("grid-mode", "idle-mode.to.grid-mode=+g",
                        "grid-mode.overlay.grid-max-row-count=3",
                        "grid-mode.overlay.selection-keys=a b c d"),
                overlay("grid-mode", "idle-mode.to.grid-mode=+g",
                        "grid-mode.hint.grid-max-row-count=3",
                        "grid-mode.hint.selection-keys=a b c d"));
    }

    @Test
    void theDeprecatedOverlayKeysAreTheirNewKeys() {
        assertEquals(
                overlay("grid-mode", "idle-mode.to.grid-mode=+g",
                        "grid-mode.overlay.area=active-window",
                        "grid-mode.overlay.area-center=mouse",
                        "grid-mode.overlay.box-horizontal-padding=4",
                        "grid-mode.overlay.subdecoration-box-border-color=#FF0000",
                        "grid-mode.overlay.subdecoration-box-framed=true"),
                overlay("grid-mode", "idle-mode.to.grid-mode=+g",
                        "grid-mode.overlay.grid-area=active-window",
                        "grid-mode.overlay.active-screen-grid-area-center=mouse",
                        "grid-mode.overlay.cell-horizontal-padding=4",
                        "grid-mode.overlay.subgrid-border-color=#FF0000",
                        "grid-mode.overlay.subgrid-closed=true"));
    }

    @Test
    void aReferenceCanMixOverlayAndHint() {
        for (String reference : List.of("other-grid-mode.overlay=grid-mode.hint",
                "other-grid-mode.hint=grid-mode.overlay")) {
            HintMeshConfiguration overlay = overlay("other-grid-mode",
                    "idle-mode.to.grid-mode=+g",
                    "grid-mode.to.other-grid-mode=+o",
                    "grid-mode.overlay.grid-max-row-count=3",
                    reference);
            assertEquals(3, overlay.type().layout(ScreenFilter.AnyScreenFilter.ANY_SCREEN_FILTER)
                    .maxRowCount(), reference);
        }
    }

    @Test
    void theBorderOfTheBackgroundIsOffUntilItHasAnOpacity() {
        Mode mode = ConfigurationParser.parse(List.of("idle-mode.to.grid-mode=+g",
                        "grid-mode.overlay.background-border-thickness=3",
                        "grid-mode.overlay.background-border-opacity=0 | _{isidling} -> 1"),
                KeyboardLayout.keyboardLayout("00000409", null)).modeMap().get("grid-mode");
        HintMeshStyle style = mode.hintMesh().styleByFilter().map().values().iterator().next();
        assertEquals(3, style.backgroundBorderThickness());
        assertEquals(Color.parse("#FFFFFF"), style.backgroundBorderColor());
        assertEquals(0, style.backgroundBorderOpacity());
        Command.MutateMode mutateMode = mode.comboMap().commandsByCombo().values().stream()
                                            .flatMap(List::stream)
                                            .filter(Command.MutateMode.class::isInstance)
                                            .map(Command.MutateMode.class::cast)
                                            .findFirst().orElseThrow();
        mode = mode.mutate(mutateMode.propertyPath(), mutateMode.newPropertyValue());
        assertEquals(1, mode.hintMesh().styleByFilter().map().values().iterator().next()
                            .backgroundBorderOpacity());
    }

    @Test
    void mouseMovementIsMouseFollowsTargetAndTargetFollowsOverlayCenter() {
        assertEquals(
                ConfigurationParser.parse(List.of("idle-mode.to.grid-mode=+g",
                        "grid-mode.overlay.mouse-follows-target=true | _{leftctrl} -> false",
                        "grid-mode.overlay.target-follows-overlay-center=true | _{leftctrl} -> false"),
                        KeyboardLayout.keyboardLayout("00000409", null)).modeMap().get("grid-mode"),
                ConfigurationParser.parse(List.of("idle-mode.to.grid-mode=+g",
                        "grid-mode.overlay.mouse-movement=mouse-follows-hint-grid-center | _{leftctrl} -> no-movement"),
                        KeyboardLayout.keyboardLayout("00000409", null)).modeMap().get("grid-mode"));
    }

    @Test
    void aTypeBranchGivesTheOverlayTheSettingsOfItsType() {
        HintMeshType grid = branchType("ui-vision", "grid");
        assertEquals(HintMeshType.HintMeshTypeType.GRID, grid.type());
        assertEquals(3, grid.layout(ScreenFilter.AnyScreenFilter.ANY_SCREEN_FILTER).maxRowCount());
        HintMeshType ui = branchType("grid", "ui-accessibility");
        assertEquals(HintMeshType.HintMeshTypeType.UI_ACCESSIBILITY, ui.type());
        assertEquals(UiHintOrder.LEFT_TO_RIGHT, ui.order());
        assertEquals(HintGridAreaSizeSource.ACTIVE_WINDOW, ui.area().size().source());
    }

    private static HintMeshType branchType(String type, String branchType) {
        Configuration configuration = ConfigurationParser.parse(List.of(
                        "idle-mode.overlay.type=" + type + " | _{isidling} -> " + branchType,
                        "idle-mode.overlay.selection-keys=a b c d",
                        "idle-mode.overlay.area=active-window",
                        "idle-mode.overlay.ui-order=left-to-right",
                        "idle-mode.overlay.grid-max-row-count=3"),
                KeyboardLayout.keyboardLayout("00000409", null));
        ComboWatcher comboWatcher =
                new ComboWatcher(null, null, () -> new App("test.exe"), null, Instant::now,
                        Set.of(), Set.of(), new KeyRedactor(KeyRedaction.NONE),
                        configuration.modeMap(),
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
        comboWatcher.modeChanged(configuration.modeMap().get(Mode.IDLE_MODE_NAME));
        comboWatcher.setVirtualKeyPressed(BuiltInVirtualKey.IS_IDLING, true);
        comboWatcher.update(0.01);
        return comboWatcher.getMutatedMode().hintMesh().type();
    }

}
