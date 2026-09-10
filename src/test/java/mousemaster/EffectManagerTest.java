package mousemaster;

import mousemaster.platform.Overlay;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The effect manager's lifecycle: start, restart, stop, mode changes, and the overlay calls. */
class EffectManagerTest {

    private final List<List<EffectFrame>> frames = new ArrayList<>();
    private int hides;
    private final Overlay overlay = (Overlay) Proxy.newProxyInstance(
            Overlay.class.getClassLoader(), new Class<?>[]{Overlay.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "setEffects" -> {
                        @SuppressWarnings("unchecked")
                        List<EffectFrame> effectFrames = (List<EffectFrame>) args[0];
                        frames.add(effectFrames);
                    }
                    case "hideEffects" -> hides++;
                    default -> {
                    }
                }
                Class<?> returned = method.getReturnType();
                if (returned == boolean.class) return false;
                if (returned == int.class) return 0;
                if (returned == double.class) return 0d;
                if (returned == long.class) return 0L;
                return null;
            });

    private static Mode mode(String modeName, String... lines) {
        return ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null)).modeMap().get(modeName);
    }

    private static final String[] TWO_EFFECTS = {
            "idle-mode.effect.shot.duration-millis=100",
            "idle-mode.effect.shot.follow-mouse=false",
            "idle-mode.effect.shot.layer1-shape=dot",
            "idle-mode.effect.shot.layer1-keyframes=0 size=10 | 100 size=20",
            "idle-mode.start-effect.shot=+a",
            "idle-mode.effect.loop.duration-millis=100",
            "idle-mode.effect.loop.repeat=loop",
            "idle-mode.effect.loop.layer1-shape=dot",
            "idle-mode.start-effect.loop=+b",
            "idle-mode.stop-effect.loop=-b",
            "idle-mode.to.other-mode=+d",
            "other-mode.to.idle-mode=+e",
    };

    @Test
    void restartingAnEffectRestartsItsCycleAndReAnchorsIt() {
        EffectManager manager = new EffectManager(overlay);
        manager.modeChanged(mode("idle-mode", TWO_EFFECTS));
        manager.mouseMoved(100, 100);
        manager.startEffect("shot");
        manager.update(0.05);
        EffectFrame first = frames.getLast().getFirst();
        assertEquals(100, first.anchor().x(), 1e-9);
        assertEquals(15, first.layers().getFirst().width(), 1e-9);
        manager.mouseMoved(300, 300);
        manager.startEffect("shot");
        manager.update(0.01);
        EffectFrame restarted = frames.getLast().getFirst();
        assertEquals(300, restarted.anchor().x(), 1e-9);
        assertEquals(11, restarted.layers().getFirst().width(), 1e-9);
    }

    @Test
    void theOverlayIsHiddenOnceWhenTheLastEffectEndsAndNotTouchedWhileIdle() {
        EffectManager manager = new EffectManager(overlay);
        manager.modeChanged(mode("idle-mode", TWO_EFFECTS));
        manager.update(0.01);
        assertEquals(0, hides);
        assertTrue(frames.isEmpty());
        manager.startEffect("loop");
        manager.startEffect("shot");
        manager.update(0.05);
        assertEquals(2, frames.getLast().size());
        manager.update(0.06); // the one-shot is over, the loop stays
        assertEquals(1, frames.getLast().size());
        manager.stopEffect("loop");
        manager.update(0.01);
        assertEquals(1, hides);
        manager.update(0.01);
        assertEquals(1, hides, "hideEffects is not repeated while nothing runs");
        manager.stopEffect("loop"); // stopping a stopped effect is a no-op
        manager.startEffect("unknown"); // an unknown name is logged, not thrown
        manager.update(0.01);
        assertEquals(1, hides);
    }

    @Test
    void aModeChangeStopsLoopsButLetsOneShotsFinish() {
        EffectManager manager = new EffectManager(overlay);
        manager.modeChanged(mode("idle-mode", TWO_EFFECTS));
        manager.startEffect("loop");
        manager.startEffect("shot");
        manager.update(0.01);
        manager.modeChanged(mode("other-mode", TWO_EFFECTS));
        manager.update(0.01);
        assertEquals(1, frames.getLast().size());
        manager.update(0.1);
        assertEquals(1, hides);
    }

    @Test
    void resolutionDependsOnlyOnElapsedTime() {
        String[] lines = {
                "idle-mode.effect.d.duration-millis=300",
                "idle-mode.effect.d.repeat=loop",
                "idle-mode.effect.d.direction=alternate",
                "idle-mode.effect.d.easing=smootherstep",
                "idle-mode.effect.d.layer1-shape=arc",
                "idle-mode.effect.d.layer1-speed=1.5",
                "idle-mode.effect.d.layer1-keyframes=0 arc-length=0 color=#000000 | 40 arc-length=200 easing=2 | 100 arc-length=360 color=#FFFFFF",
                "idle-mode.effect.d.layer2-shape=dot",
                "idle-mode.effect.d.layer2-delay=70",
                "idle-mode.effect.d.layer2-keyframes=0 size=0 | 100 size=50",
                "idle-mode.start-effect.d=+n",
        };
        EffectConfiguration effect = mode("idle-mode", lines).effects().get("d");
        EffectManager.EffectPlayer coarse = new EffectManager.EffectPlayer(effect, null);
        EffectManager.EffectPlayer fine = new EffectManager.EffectPlayer(effect, null);
        coarse.advance(0.4);
        for (int i = 0; i < 40; i++)
            fine.advance(0.01);
        List<EffectFrame.ResolvedEffectLayer> a = coarse.frame().layers();
        List<EffectFrame.ResolvedEffectLayer> b = fine.frame().layers();
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).arcLength(), b.get(i).arcLength(), 1e-6);
            assertEquals(a.get(i).width(), b.get(i).width(), 1e-6);
            assertEquals(a.get(i).hexColor(), b.get(i).hexColor());
        }
    }

    @Test
    void aMutationOfTheCurrentModeDoesNotStopItsLoops() {
        // A property branch flipping (_{isleftmousepressing} -> ...) re-announces the same
        // mode to the listeners; only another mode stops the loops.
        EffectManager manager = new EffectManager(overlay);
        Mode idle = mode("idle-mode", TWO_EFFECTS);
        manager.modeChanged(idle);
        manager.startEffect("loop");
        manager.update(0.01);
        manager.modeChanged(mode("idle-mode", TWO_EFFECTS)); // same name, another instance: a mutation
        manager.update(0.01);
        assertEquals(1, frames.getLast().size(), "the loop survives a mutation of its own mode");
        manager.modeChanged(mode("other-mode", TWO_EFFECTS));
        manager.update(0.01);
        assertEquals(1, hides, "the loop stops on a switch to another mode");
    }

    @Test
    void anEffectStartedAfterItsComboSwitchedModeIsFoundInThePreviousMode() {
        // A combo that switches mode and starts an effect runs its start-effect after the
        // switch when it waits behind an atomic command (a hint selection moving the
        // mouse): the effect is then looked up in the mode the combo came from.
        EffectManager manager = new EffectManager(overlay);
        manager.modeChanged(mode("idle-mode", TWO_EFFECTS));
        manager.modeChanged(mode("other-mode", TWO_EFFECTS));
        manager.startEffect("shot");
        manager.update(0.01);
        assertEquals(1, frames.getLast().size(), "shot is idle-mode's, started from other-mode");
        manager.startEffect("nope");
        manager.update(0.01);
        assertEquals(1, frames.getLast().size(), "an unknown name is still ignored");
    }

    @Test
    void aTextLayerShowsTheKeyThatStartedTheEffectAndTheKeysThatRestartedIt() {
        // {key} is the key that completed the start-effect combo; {keys} the keys of
        // every start while the effect was running: a keycast that shows what was typed.
        EffectManager manager = new EffectManager(overlay);
        manager.modeChanged(mode("idle-mode",
                "idle-mode.effect.cast.duration-millis=500",
                "idle-mode.effect.cast.layer1-shape=text",
                "idle-mode.effect.cast.layer1-text=[{key}] {keys}",
                "idle-mode.start-effect.cast=+a"));
        manager.startEffect("cast", Key.ofName("a"));
        manager.update(0.01);
        assertEquals("[a] a", frames.getLast().getFirst().layers().getFirst().text().text());
        manager.startEffect("cast", Key.ofName("space"));
        manager.update(0.01);
        assertEquals("[space] a space",
                frames.getLast().getFirst().layers().getFirst().text().text());
        for (int i = 0; i < 6; i++) // a tick is clamped to 100ms: the one-shot ends here
            manager.update(0.1);
        // The effect has ended: the next start begins a new history.
        manager.startEffect("cast", Key.ofName("b"));
        manager.update(0.01);
        assertEquals("[b] b", frames.getLast().getFirst().layers().getFirst().text().text());
        manager.startEffect("cast"); // started without a key (a test, a future caller)
        manager.update(0.01);
        assertEquals("[b] b", frames.getLast().getFirst().layers().getFirst().text().text());
    }

    @Test
    void theOverlayIsOnlyRedrawnWhenSomethingChanged() {
        EffectManager manager = new EffectManager(overlay);
        manager.modeChanged(mode("idle-mode",
                "idle-mode.effect.hold.duration-millis=1000",
                "idle-mode.effect.hold.repeat=loop",
                "idle-mode.effect.hold.follow-mouse=false",
                "idle-mode.effect.hold.layer1-shape=dot",
                "idle-mode.effect.hold.layer1-keyframes=0 size=10 | 10 size=20 | 100 size=20",
                "idle-mode.start-effect.hold=+a",
                "idle-mode.effect.follow.repeat=loop",
                "idle-mode.effect.follow.layer1-shape=dot",
                "idle-mode.start-effect.follow=+b"));
        manager.mouseMoved(10, 10);
        manager.startEffect("hold");
        manager.update(0.05);
        manager.update(0.05);
        manager.update(0.05); // 150ms: the size holds at 20 from here on
        int drawn = frames.size();
        for (int i = 0; i < 10; i++)
            manager.update(0.05);
        assertEquals(drawn, frames.size(), "a held value is not redrawn");
        manager.mouseMoved(20, 20);
        manager.update(0.05);
        assertEquals(drawn, frames.size(), "an anchored effect ignores mouse moves");
        manager.startEffect("follow");
        manager.update(0.05);
        drawn = frames.size();
        manager.update(0.05);
        assertEquals(drawn, frames.size(), "still nothing changed");
        manager.mouseMoved(30, 30);
        manager.update(0.05);
        assertEquals(drawn + 1, frames.size(), "a following effect is redrawn when the mouse moves");
    }

    @Test
    void aStallAdvancesAnEffectByAtMost100ms() {
        EffectConfiguration shot = mode("idle-mode",
                "idle-mode.effect.shot.duration-millis=300",
                "idle-mode.effect.shot.layer1-shape=dot",
                "idle-mode.effect.shot.layer1-keyframes=0 size=0 | 100 size=300",
                "idle-mode.start-effect.shot=+c").effects().get("shot");
        EffectManager manager = new EffectManager(overlay);
        manager.modeChanged(mode("idle-mode",
                "idle-mode.effect.shot.duration-millis=300",
                "idle-mode.effect.shot.layer1-shape=dot",
                "idle-mode.effect.shot.layer1-keyframes=0 size=0 | 100 size=300",
                "idle-mode.start-effect.shot=+c"));
        manager.startEffect("shot");
        manager.update(1.0); // a one-second stall
        assertEquals(100, frames.getLast().getFirst().layers().getFirst().width(), 1e-9);
        assertEquals(1, frames.size());
        assertNotNull(shot);
    }

    @Test
    void aDisabledEffectIsDefinedButDoesNothing() {
        EffectManager manager = new EffectManager(overlay);
        manager.modeChanged(mode("idle-mode",
                "idle-mode.effect.off.enabled=false",
                "idle-mode.effect.off.layer1-shape=dot",
                "idle-mode.start-effect.off=+n"));
        manager.startEffect("off");
        manager.update(0.01);
        assertTrue(frames.isEmpty());
        assertEquals(0, hides);
    }

}
