package mousemaster;

import mousemaster.IndicatorPosition.PointIndicatorPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The deprecated values are rewritten as their new names. */
class DeprecatedValueTest {

    private static Mode mode(String... lines) {
        return ConfigurationParser.parse(List.of(lines),
                                          KeyboardLayout.keyboardLayout("00000409", null))
                                  .modeMap().get("hint-mode");
    }

    @Test
    void screenCenterIsTheActiveScreenCenterInEveryKeyThatTakesIt() {
        Mode mode = mode("idle-mode.to.hint-mode=+h",
                "hint-mode.overlay.area-center=screen-center",
                "hint-mode.zoom.center=mouse | _{leftshift} -> screen-center",
                "hint-mode.indicator.position=screen-center",
                "hint-mode.text-indicator.area-center=screen-center");
        assertEquals(AreaCenter.ACTIVE_SCREEN_CENTER, mode.hintMesh().type().area().center());
        assertEquals(AreaCenter.MOUSE, mode.zoom().center());
        assertEquals(PointIndicatorPosition.ACTIVE_SCREEN_CENTER,
                mode.indicator().layerByName().get("indicator").position());
        assertEquals(AreaCenter.ACTIVE_SCREEN_CENTER,
                mode.indicator().layerByName().get("text-indicator").area().center());
    }

}
