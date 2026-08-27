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
        assertEquals(16, layer.sizeWidth());
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
                "idle-mode.effect.blip.layer2-shape=square",
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
        assertEquals(10, cross.x());
        assertEquals(-10, cross.y());
        assertEquals(45, cross.rotation());
        assertEquals("#96A8FF", cross.hexColor());
        assertEquals(0.5, cross.opacity());
        assertEquals(2, cross.thickness());
        assertEquals(4, cross.keyframes().size());
        assertEquals(Boolean.FALSE, cross.keyframes().get(1).visible());
        assertEquals(90, cross.keyframes().get(2).rotation());
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
        assertEquals("#FF0000", inherited.layers().getFirst().hexColor());
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
        EffectManager.EffectPlayer player = new EffectManager.EffectPlayer(blip);
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
        EffectManager.EffectPlayer player = new EffectManager.EffectPlayer(blip);
        player.advance(0.05);
        assertEquals(15, player.frame().layers().getFirst().width(), 1e-9);
    }

    @Test
    void aLoopingPlayerWrapsAround() {
        EffectConfiguration pulse = effect("pulse",
                "idle-mode.effect.pulse.duration-millis=100",
                "idle-mode.effect.pulse.repeat=loop",
                "idle-mode.effect.pulse.layer1-shape=square",
                "idle-mode.effect.pulse.layer1-keyframes=0 rotation=0 | 100 rotation=90",
                "idle-mode.start-effect.pulse=+n");
        EffectManager.EffectPlayer player = new EffectManager.EffectPlayer(pulse);
        player.advance(0.125); // 125% wraps to 25%.
        assertFalse(player.done());
        assertEquals(22.5, player.frame().layers().getFirst().rotation(), 1e-9);
    }

    @Test
    void aHiddenLayerIsNotResolved() {
        EffectConfiguration wink = effect("wink",
                "idle-mode.effect.wink.duration-millis=100",
                "idle-mode.effect.wink.layer1-shape=cross",
                "idle-mode.effect.wink.layer1-keyframes=0 show | 50 hide",
                "idle-mode.start-effect.wink=+n");
        EffectManager.EffectPlayer player = new EffectManager.EffectPlayer(wink);
        player.advance(0.06);
        assertTrue(player.frame().layers().isEmpty());
    }

}
