package mousemaster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** hint is the former name of overlay. */
class OverlayTest {

    private static HintMeshConfiguration overlay(String modeName, String... lines) {
        return ConfigurationParser.parse(List.of(lines),
                                          KeyboardLayout.keyboardLayout("00000409", null))
                                  .modeMap().get(modeName).hintMesh();
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
    void aReferenceCanMixOverlayAndHint() {
        for (String reference : List.of("other-grid-mode.overlay=grid-mode.hint",
                "other-grid-mode.hint=grid-mode.overlay")) {
            HintMeshConfiguration overlay = overlay("other-grid-mode",
                    "idle-mode.to.grid-mode=+g",
                    "grid-mode.to.other-grid-mode=+o",
                    "grid-mode.overlay.grid-max-row-count=3",
                    reference);
            assertEquals(3, ((HintMeshType.GridHintMesh) overlay.type())
                    .layout(ScreenFilter.AnyScreenFilter.ANY_SCREEN_FILTER).maxRowCount(), reference);
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

}
