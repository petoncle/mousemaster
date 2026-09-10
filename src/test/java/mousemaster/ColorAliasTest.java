package mousemaster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ColorAliasTest {

    private Configuration parse(String... lines) {
        return ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
    }

    @Test
    void aColorAliasIsUsableByHintAndIndicator() {
        Configuration configuration = parse(
                "color-alias.screen-gradient=across-screen per-pixel center-to-edge #F97316 #3B82F6",
                "idle-mode.hint.box-color=screen-gradient",
                "idle-mode.indicator.color=screen-gradient",
                "idle-mode.indicator.inner-outline-color=screen-gradient",
                "idle-mode.indicator.shadow-color=screen-gradient");
        Mode mode = configuration.modeMap().get(Mode.IDLE_MODE_NAME);
        GradientColor expected =
                GradientColor.parse("across-screen per-pixel center-to-edge #F97316 #3B82F6");
        assertEquals(expected, mode.hintMesh().styleByFilter().map().values().iterator().next().boxColor());
        assertEquals(expected, mode.indicator().color());
        assertEquals(expected, mode.indicator().innerOutline().color());
        assertEquals(expected, mode.indicator().shadow().color());
    }

    @Test
    void anUndefinedAliasIsStillRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.indicator.color=no-such-alias"));
    }
}
