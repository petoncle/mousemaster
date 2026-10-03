package mousemaster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndicatorLayerTest {

    private static Configuration parse(String... lines) {
        return ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
    }

    private static IndicatorLayerConfiguration layer(Mode mode, String layerName) {
        return mode.indicator().layerByName().get(layerName);
    }

    @Test
    void aLayerTakesItsDefaultsFromTheDefaultIndicatorNotFromTheModeIndicator() {
        Mode mode = parse("idle-mode.indicator.size=40",
                "idle-mode.ripple-indicator.color=#00FF00").modeMap().get(Mode.IDLE_MODE_NAME);
        assertTrue(layer(mode, "ripple-indicator").enabled());
        assertEquals(26, layer(mode, "ripple-indicator").size());
        assertEquals(Color.parse("#00FF00"), layer(mode, "ripple-indicator").color());
        assertEquals(40, layer(mode, "indicator").size());
    }

    @Test
    void aChildModeExtendsEachLayerByName() {
        Mode mode = parse("idle-mode.to.normal-mode=+leftshift",
                "idle-mode.ripple-indicator.size=40",
                "normal-mode=idle-mode",
                "normal-mode.ripple-indicator.color=#00FF00").modeMap().get("normal-mode");
        assertEquals(40, layer(mode, "ripple-indicator").size());
        assertEquals(Color.parse("#00FF00"), layer(mode, "ripple-indicator").color());
        assertFalse(layer(mode, "indicator").enabled());
    }

    @Test
    void aLayerIsDrawnInTheOrderOfItsZ() {
        Mode mode = parse("idle-mode.indicator.size=40",
                "idle-mode.ripple-indicator.z=-1").modeMap().get(Mode.IDLE_MODE_NAME);
        assertEquals(0, layer(mode, "indicator").z());
        assertEquals(-1, layer(mode, "ripple-indicator").z());
    }

    @Test
    void aMutationReachesALayer() {
        Mode mode = parse("idle-mode.ripple-indicator.size=40 | _{isidling} -> 50")
                .modeMap().get(Mode.IDLE_MODE_NAME);
        Command.MutateMode mutateMode = mode.comboMap().commandsByCombo().values().stream()
                                            .flatMap(List::stream)
                                            .filter(Command.MutateMode.class::isInstance)
                                            .map(Command.MutateMode.class::cast)
                                            .findFirst().orElseThrow();
        mode = mode.mutate(mutateMode.propertyPath(), mutateMode.newPropertyValue());
        assertEquals(50, layer(mode, "ripple-indicator").size());
        assertEquals(26, layer(mode, "indicator").size());
    }

    @Test
    void renderAsCursorAndTheShadowBelongToTheIndicator() {
        assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.ripple-indicator.render-as-cursor=true"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.ripple-indicator.shadow-opacity=1"));
        assertEquals(1, parse("idle-mode.indicator.shadow-opacity=1").modeMap()
                .get(Mode.IDLE_MODE_NAME).indicator().shadow().opacity());
        assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.to.normal-mode=+leftshift",
                        "idle-mode.ripple-indicator.size=40",
                        "normal-mode.ripple-indicator=idle-mode.ripple-indicator"));
    }
}
