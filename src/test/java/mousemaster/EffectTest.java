package mousemaster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Effects: property parsing, mode inheritance, and the keyframe player. */
class EffectTest {

    private static Configuration parse(String... lines) {
        return ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
    }

    private static EffectConfiguration effect(String effectName, String... lines) {
        return parse(lines).modeMap()
                           .get(Mode.IDLE_MODE_NAME)
                           .effects()
                           .get(effectName);
    }

    @Test
    void parsesAnEffectWithDefaults() {
        EffectConfiguration blip = effect("blip",
                "idle-mode.effect.blip.layer1-shape=circle",
                "idle-mode.start-effect.blip=+n");
        assertEquals(250, blip.duration().toMillis());
        assertFalse(blip.loop());
        assertEquals(100, blip.areaWidth());
        assertEquals(100, blip.areaHeight());
        assertEquals(1, blip.layers().size());
        EffectLayer layer = blip.layers().getFirst();
        assertEquals(EffectShape.CIRCLE, layer.shape());
        assertEquals(16d, layer.base().get(EffectProperty.WIDTH));
        assertFalse(layer.filled());
    }

    @Test
    void parsesLayerAndKeyframeProperties() {
        EffectConfiguration blip = effect("blip",
                "idle-mode.effect.blip.duration-millis=300",
                "idle-mode.effect.blip.repeat=loop",
                "idle-mode.effect.blip.area=64x32",
                "idle-mode.effect.blip.layer1-shape=cross",
                "idle-mode.effect.blip.layer1-x=10",
                "idle-mode.effect.blip.layer1-y=-10",
                "idle-mode.effect.blip.layer1-size=24",
                "idle-mode.effect.blip.layer1-rotation=45",
                "idle-mode.effect.blip.layer1-color=#96A8FF",
                "idle-mode.effect.blip.layer1-opacity=0.5",
                "idle-mode.effect.blip.layer1-thickness=2",
                "idle-mode.effect.blip.layer1-keyframes=0 size=24 show | 50 hide | 80 show rotation=90 | 100 opacity=0",
                "idle-mode.effect.blip.layer2-shape=rect",
                "idle-mode.effect.blip.layer2-size=area",
                "idle-mode.effect.blip.layer2-filled=true",
                "idle-mode.start-effect.blip=+n");
        assertEquals(300, blip.duration().toMillis());
        assertTrue(blip.loop());
        assertEquals(64, blip.areaWidth());
        assertEquals(32, blip.areaHeight());
        assertEquals(2, blip.layers().size());
        EffectLayer cross = blip.layers().getFirst();
        assertEquals(EffectShape.CROSS, cross.shape());
        assertEquals(10d, cross.base().get(EffectProperty.X));
        assertEquals(-10d, cross.base().get(EffectProperty.Y));
        assertEquals(45d, cross.base().get(EffectProperty.ROTATION));
        assertEquals("#96A8FF", cross.base().get(EffectProperty.COLOR));
        assertEquals(0.5, cross.base().get(EffectProperty.OPACITY));
        assertEquals(2d, cross.base().get(EffectProperty.THICKNESS));
        assertEquals(4, cross.keyframes().size());
        assertEquals(Boolean.FALSE, cross.keyframes().get(1).values().get(EffectProperty.VISIBLE));
        assertEquals(90d, cross.keyframes().get(2).values().get(EffectProperty.ROTATION));
        EffectLayer background = blip.layers().get(1);
        assertTrue(background.sizeIsArea());
        assertTrue(background.filled());
    }

    @Test
    void aModeInheritsItsParentsEffects() {
        Configuration configuration = parse(
                "idle-mode.effect.blip.layer1-shape=dot",
                "idle-mode.start-effect.blip=+n",
                "idle-mode.to.other-mode=+e",
                "other-mode.effect=idle-mode.effect",
                "other-mode.effect.blip.layer1-color=#FF0000",
                "other-mode.start-effect.blip=+n",
                "other-mode.to.idle-mode=+q");
        EffectConfiguration inherited =
                configuration.modeMap().get("other-mode").effects().get("blip");
        assertEquals(EffectShape.DOT, inherited.layers().getFirst().shape());
        assertEquals("#FF0000", inherited.layers().getFirst().base().get(EffectProperty.COLOR));
    }

    @Test
    void anUndefinedEffectReferenceIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parse("idle-mode.start-effect.ghost=+n"));
        assertTrue(exception.getMessage().contains("ghost"));
    }

    @Test
    void aLayerWithoutAShapeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.effect.blip.layer1-size=10"));
    }

    @Test
    void nonConsecutiveLayerNumbersAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.effect.blip.layer2-shape=dot"));
    }

    @Test
    void nonIncreasingKeyframesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.effect.blip.layer1-shape=dot",
                        "idle-mode.effect.blip.layer1-keyframes=50 size=1 | 50 size=2"));
    }

    @Test
    void thePlayerInterpolatesBetweenKeyframes() {
        EffectConfiguration blip = effect("blip",
                "idle-mode.effect.blip.duration-millis=200",
                "idle-mode.effect.blip.layer1-shape=circle",
                "idle-mode.effect.blip.layer1-size=10",
                "idle-mode.effect.blip.layer1-keyframes=0 size=10 opacity=1 | 100 size=40 opacity=0",
                "idle-mode.start-effect.blip=+n");
        EffectManager.EffectPlayer player = new EffectManager.EffectPlayer(blip, null);
        player.advance(0.1); // 50% of the 200ms cycle.
        EffectFrame frame = player.frame();
        EffectFrame.ResolvedEffectLayer layer = frame.layers().getFirst();
        assertEquals(25, layer.width(), 1e-9);
        assertEquals(0.5, layer.opacity(), 1e-9);
        assertFalse(player.done());
        player.advance(0.11);
        assertTrue(player.done());
    }

    @Test
    void theBaseValueActsAsTheImplicitFirstKeyframe() {
        EffectConfiguration blip = effect("blip",
                "idle-mode.effect.blip.duration-millis=100",
                "idle-mode.effect.blip.layer1-shape=dot",
                "idle-mode.effect.blip.layer1-size=10",
                "idle-mode.effect.blip.layer1-keyframes=100 size=20",
                "idle-mode.start-effect.blip=+n");
        EffectManager.EffectPlayer player = new EffectManager.EffectPlayer(blip, null);
        player.advance(0.05);
        assertEquals(15, player.frame().layers().getFirst().width(), 1e-9);
    }

    @Test
    void aLoopingPlayerWrapsAround() {
        EffectConfiguration pulse = effect("pulse",
                "idle-mode.effect.pulse.duration-millis=100",
                "idle-mode.effect.pulse.repeat=loop",
                "idle-mode.effect.pulse.layer1-shape=rect",
                "idle-mode.effect.pulse.layer1-keyframes=0 rotation=0 | 100 rotation=90",
                "idle-mode.start-effect.pulse=+n");
        EffectManager.EffectPlayer player = new EffectManager.EffectPlayer(pulse, null);
        player.advance(0.125); // 125% wraps to 25%.
        assertFalse(player.done());
        assertEquals(22.5, player.frame().layers().getFirst().rotation(), 1e-9);
    }

    @Test
    void axisRotationsInterpolateWithSignedDirection() {
        EffectConfiguration flip = effect("flip",
                "idle-mode.effect.flip.duration-millis=100",
                "idle-mode.effect.flip.layer1-shape=rect",
                "idle-mode.effect.flip.layer1-rotation-x=10",
                "idle-mode.effect.flip.layer1-keyframes=0 rotation-y=0 | 100 rotation-y=-360",
                "idle-mode.start-effect.flip=+n");
        assertEquals(10d, flip.layers().getFirst().base().get(EffectProperty.ROTATION_X));
        EffectManager.EffectPlayer player = new EffectManager.EffectPlayer(flip, null);
        player.advance(0.025); // 25%: spinning backward toward -360.
        EffectFrame.ResolvedEffectLayer layer = player.frame().layers().getFirst();
        assertEquals(-90, layer.rotationY(), 1e-9);
        assertEquals(10, layer.rotationX(), 1e-9);
    }

    @Test
    void aLayersSpeedRunsItsTimelineFasterThanTheCycle() {
        EffectConfiguration spin = effect("spin",
                "idle-mode.effect.spin.duration-millis=100",
                "idle-mode.effect.spin.layer1-shape=dot",
                "idle-mode.effect.spin.layer1-speed=2",
                "idle-mode.effect.spin.layer1-keyframes=0 size=0 | 100 size=100",
                "idle-mode.start-effect.spin=+n");
        EffectManager.EffectPlayer player = new EffectManager.EffectPlayer(spin, null);
        player.advance(0.06); // 60% of the cycle, 120% of the layer timeline: wraps to 20%.
        assertEquals(20, player.frame().layers().getFirst().width(), 1e-9);
    }

    @Test
    void aKeyframeEasingShapesItsSegment() {
        EffectConfiguration grow = effect("grow",
                "idle-mode.effect.grow.duration-millis=100",
                "idle-mode.effect.grow.layer1-shape=dot",
                "idle-mode.effect.grow.layer1-keyframes=0 size=0 | 100 size=100 easing=2",
                "idle-mode.start-effect.grow=+n");
        EffectManager.EffectPlayer player = new EffectManager.EffectPlayer(grow, null);
        player.advance(0.05); // 50% with a quadratic segment: t^2 = 0.25.
        assertEquals(25, player.frame().layers().getFirst().width(), 1e-9);
    }

    @Test
    void aHiddenLayerIsNotResolved() {
        EffectConfiguration wink = effect("wink",
                "idle-mode.effect.wink.duration-millis=100",
                "idle-mode.effect.wink.layer1-shape=cross",
                "idle-mode.effect.wink.layer1-keyframes=0 show | 50 hide",
                "idle-mode.start-effect.wink=+n");
        EffectManager.EffectPlayer player = new EffectManager.EffectPlayer(wink, null);
        player.advance(0.06);
        assertTrue(player.frame().layers().isEmpty());
    }

    @Test
    void dashesAreParsedAsLengthsAndTheOffsetAnimates() {
        EffectConfiguration marquee = effect("marquee",
                "idle-mode.effect.marquee.duration-millis=100",
                "idle-mode.effect.marquee.layer1-shape=circle",
                "idle-mode.effect.marquee.layer1-dash=6,4",
                "idle-mode.effect.marquee.layer1-keyframes=0 dash-offset=0 | 100 dash-offset=10",
                "idle-mode.start-effect.marquee=+n");
        EffectManager.EffectPlayer player = new EffectManager.EffectPlayer(marquee, null);
        player.advance(0.05);
        EffectFrame.ResolvedEffectLayer layer = player.frame().layers().getFirst();
        assertEquals(6, layer.dashLength(), 1e-9);
        assertEquals(4, layer.dashGap(), 1e-9);
        assertEquals(5, layer.dashOffset(), 1e-9);
    }

    @Test
    void keyframePositionsCanBeWrittenInMilliseconds() {
        EffectConfiguration blink = effect("blink",
                "idle-mode.effect.blink.layer1-shape=dot",
                "idle-mode.effect.blink.layer1-keyframes=0 hide | 150ms show | 75 hide",
                "idle-mode.effect.blink.duration-millis=200", // written after the keyframes
                "idle-mode.start-effect.blink=+n");
        EffectLayer layer = blink.layers().getFirst();
        assertEquals(75, layer.keyframes().get(1).percent(), 1e-9);
        assertFalse(layer.keyframes().get(1).inMillis());
        // 150ms -> 75% is not before 75%: rejected as not increasing.
        assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.effect.blink.layer1-shape=dot",
                        "idle-mode.effect.blink.layer1-keyframes=150ms show | 75 hide",
                        "idle-mode.effect.blink.duration-millis=200"));
        // Beyond the duration is rejected too.
        assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.effect.blink.layer1-shape=dot",
                        "idle-mode.effect.blink.layer1-keyframes=300ms show",
                        "idle-mode.effect.blink.duration-millis=200"));
    }

    @Test
    void textLayersCarryTheirTextSettingsAndAnimateTheirFontSize() {
        EffectConfiguration toast = effect("toast",
                "idle-mode.effect.toast.duration-millis=100",
                "idle-mode.effect.toast.layer1-shape=text",
                "idle-mode.effect.toast.layer1-text=Copied to clipboard",
                "idle-mode.effect.toast.layer1-font-name=Segoe UI",
                "idle-mode.effect.toast.layer1-font-weight=bold",
                "idle-mode.effect.toast.layer1-font-italic=true",
                "idle-mode.effect.toast.layer1-text-align=left",
                "idle-mode.effect.toast.layer1-background-color=#202020",
                "idle-mode.effect.toast.layer1-keyframes=0 font-size=10 | 100 font-size=20",
                "idle-mode.start-effect.toast=+n");
        EffectLayer layer = toast.layers().getFirst();
        assertEquals(EffectShape.TEXT, layer.shape());
        assertEquals(new EffectText("Copied to clipboard", "Segoe UI", FontWeight.BOLD, true,
                EffectText.Align.LEFT), layer.text());
        EffectManager.EffectPlayer player = new EffectManager.EffectPlayer(toast, null);
        player.advance(0.05);
        EffectFrame.ResolvedEffectLayer resolved = player.frame().layers().getFirst();
        assertEquals(15, resolved.fontSize(), 1e-9);
        assertEquals("#202020", resolved.backgroundHexColor());
        assertNull(resolved.outlineHexColor());
        // A text layer needs its text; text settings need a text layer.
        assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.effect.toast.layer1-shape=text"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.effect.toast.layer1-shape=dot",
                        "idle-mode.effect.toast.layer1-text=oops"));
    }

    @Test
    void badValuesAreConfigurationErrorsThatNameTheKeyAndTheExpectedForm() {
        IllegalArgumentException outOfRange = assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.effect.blip.layer1-shape=polygon",
                        "idle-mode.effect.blip.layer1-edge-count=1001"));
        assertTrue(outOfRange.getMessage().contains("edge-count value 1001") &&
                   outOfRange.getMessage().contains("between 3 and 1000"), outOfRange.getMessage());
        IllegalArgumentException notANumber = assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.effect.blip.layer1-shape=dot",
                        "idle-mode.effect.blip.layer1-keyframes=50 opacity=high"));
        assertTrue(notANumber.getMessage().contains("opacity value high") &&
                   notANumber.getMessage().contains("keyframe token opacity=high") &&
                   !notANumber.getMessage().contains("For input string"), notANumber.getMessage());
        IllegalArgumentException notABoolean = assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.effect.blip.layer1-shape=dot",
                        "idle-mode.effect.blip.layer1-filled=yes"));
        assertTrue(notABoolean.getMessage().contains("expected true or false"), notABoolean.getMessage());
    }

    @Test
    void rangeEdgesAreAcceptedAndOneStepOutsideIsRejected() {
        EffectConfiguration edges = effect("edges",
                "idle-mode.effect.edges.layer1-shape=polygon",
                "idle-mode.effect.edges.layer1-edge-count=1000",
                "idle-mode.effect.edges.layer1-opacity=0",
                "idle-mode.effect.edges.layer1-arc-length=-360",
                "idle-mode.effect.edges.layer1-keyframes=0 size=0 | 100 scale=100",
                "idle-mode.start-effect.edges=+n");
        assertEquals(1000d, edges.layers().getFirst().base().get(EffectProperty.EDGE_COUNT));
        for (String outside : new String[]{"edge-count=1001", "edge-count=2", "opacity=1.001",
                "opacity=-0.001", "arc-length=361", "scale=100.5", "opacity=NaN", "font-size=0.5"})
            assertThrows(IllegalArgumentException.class,
                    () -> parse("idle-mode.effect.edges.layer1-shape=polygon",
                            "idle-mode.effect.edges.layer1-" + outside), outside);
    }

    @Test
    void emptyAndDanglingKeyframesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.effect.blip.layer1-shape=dot",
                        "idle-mode.effect.blip.layer1-keyframes=50"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("idle-mode.effect.blip.layer1-shape=dot",
                        "idle-mode.effect.blip.layer1-keyframes=50 size=1 |"));
    }

    @Test
    void aOneShotEndsOnItsFinalValueAndIsGoneRightAfter() {
        EffectConfiguration blip = effect("blip",
                "idle-mode.effect.blip.duration-millis=100",
                "idle-mode.effect.blip.layer1-shape=dot",
                "idle-mode.effect.blip.layer1-keyframes=0 size=50 | 100 size=60",
                "idle-mode.start-effect.blip=+n");
        EffectManager.EffectPlayer player = new EffectManager.EffectPlayer(blip, null);
        player.advance(0.0999);
        assertEquals(59.99, player.frame().layers().getFirst().width(), 1e-6);
        player.advance(0.0002);
        assertTrue(player.done());
    }

}
