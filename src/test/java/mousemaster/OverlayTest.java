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
    void mouseFollowsHintGridCenterIsMouseFollowsOverlayCenter() {
        assertEquals(HintMouseMovement.MOUSE_FOLLOWS_OVERLAY_CENTER,
                overlay("grid-mode", "idle-mode.to.grid-mode=+g",
                        "grid-mode.overlay.mouse-movement=mouse-follows-hint-grid-center")
                        .mouseMovement());
    }

}
