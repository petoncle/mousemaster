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
                "idle-mode.ripple-indicator.fill-color=#00FF00").modeMap().get(Mode.IDLE_MODE_NAME);
        assertTrue(layer(mode, "ripple-indicator").enabled());
        assertEquals(26, layer(mode, "ripple-indicator").size());
        assertEquals(Color.parse("#00FF00"), layer(mode, "ripple-indicator").fillColor());
        assertEquals(40, layer(mode, "indicator").size());
    }

    @Test
    void aChildModeExtendsEachLayerByName() {
        Mode mode = parse("idle-mode.to.normal-mode=+leftshift",
                "idle-mode.ripple-indicator.size=40",
                "normal-mode=idle-mode",
                "normal-mode.ripple-indicator.fill-color=#00FF00").modeMap().get("normal-mode");
        assertEquals(40, layer(mode, "ripple-indicator").size());
        assertEquals(Color.parse("#00FF00"), layer(mode, "ripple-indicator").fillColor());
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
    void aLayerFollowsTheMouseUnlessItIsAnchored() {
        Mode mode = parse("idle-mode.indicator.size=40",
                "idle-mode.ripple-indicator.follow-mouse=false").modeMap().get(Mode.IDLE_MODE_NAME);
        assertTrue(layer(mode, "indicator").followMouse());
        assertFalse(layer(mode, "ripple-indicator").followMouse());
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

    @Test
    void aStrokeLengthIsAPercentOfThePerimeter() {
        Mode mode = parse("idle-mode.indicator.stroke-length-percent=-0.6",
                "idle-mode.indicator.stroke-anchor=middle").modeMap().get(Mode.IDLE_MODE_NAME);
        assertEquals(-0.6, layer(mode, "indicator").stroke().lengthPercent());
        assertEquals(StrokeAnchor.MIDDLE, layer(mode, "indicator").stroke().anchor());
        assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.indicator.stroke-length-percent=60%"));
    }

    @Test
    void theOutlinesAreGone() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.indicator.inner-outline-thickness=1"));
        assertTrue(e.getMessage().contains("stroke"), e.getMessage());
        e = assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.indicator.color=#FF0000"));
        assertTrue(e.getMessage().contains("fill-color"), e.getMessage());
    }

    @Test
    void aLayerIsAShapeInABoxOfItsSizeAndAspectRatio() {
        Mode mode = parse("idle-mode.indicator.shape=rectangle",
                "idle-mode.indicator.aspect-ratio=2",
                "idle-mode.indicator.border-radius=4",
                "idle-mode.path-indicator.shape=path",
                "idle-mode.path-indicator.points=0,0 10,0 5,8").modeMap().get(Mode.IDLE_MODE_NAME);
        assertEquals(IndicatorShape.RECTANGLE, layer(mode, "indicator").shape());
        assertEquals(2, layer(mode, "indicator").aspectRatio());
        assertEquals(4, layer(mode, "indicator").borderRadius());
        assertEquals(IndicatorShape.PATH, layer(mode, "path-indicator").shape());
        assertEquals(List.of(new Point(0, 0), new Point(10, 0), new Point(5, 8)),
                layer(mode, "path-indicator").points());
        assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.indicator.points=0,0 10,0"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.indicator.edge-count=6"));
        assertTrue(e.getMessage().contains("shape"), e.getMessage());
    }
}
