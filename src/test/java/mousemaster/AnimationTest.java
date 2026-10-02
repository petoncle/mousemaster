package mousemaster;

import mousemaster.platform.Overlay;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AnimationTest {

    private Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private ComboWatcher comboWatcher;
    private AnimationPlayer animationPlayer;
    private IndicatorManager indicatorManager;
    private final List<IndicatorConfiguration> drawn = new ArrayList<>();

    private void load(String... lines) {
        Configuration configuration = ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
        ModeMap modeMap = configuration.modeMap();
        Set<Key> pressedPreconditionKeys = new HashSet<>();
        for (Mode mode : modeMap.modes())
            for (Combo combo : mode.comboMap().commandsByCombo().keySet())
                pressedPreconditionKeys.addAll(combo.precondition()
                                                    .keyPrecondition()
                                                    .pressedKeyPrecondition()
                                                    .allKeys());
        CommandRunner commandRunner = new CommandRunner(null, null, null) {
            @Override
            public boolean runningAtomicCommand() {
                return false;
            }
        };
        comboWatcher = new ComboWatcher(commandRunner, null, () -> new App("test.exe"), null,
                () -> now, Set.of(), pressedPreconditionKeys, new KeyRedactor(KeyRedaction.NONE), modeMap,
                configuration.initiallySetVariables(), configuration.virtualKeys(),
                configuration.initiallyPressedVirtualKeys());
        animationPlayer = new AnimationPlayer(() -> now, comboWatcher,
                configuration.animationConfigurationByName());
        commandRunner.setAnimationPlayer(animationPlayer);
        Overlay overlay = (Overlay) Proxy.newProxyInstance(
                Overlay.class.getClassLoader(), new Class<?>[] {Overlay.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setIndicator"))
                        drawn.add((IndicatorConfiguration) args[0]);
                    return null;
                });
        indicatorManager = new IndicatorManager(overlay, animationPlayer);
        comboWatcher.setModeListeners(List.of(indicatorManager));
        comboWatcher.modeChanged(modeMap.get(Mode.IDLE_MODE_NAME));
    }

    private void tap(String keyName) {
        Key key = Key.ofName(keyName);
        comboWatcher.keyEvent(new KeyEvent.PressKeyEvent(now, key));
        comboWatcher.keyEvent(new KeyEvent.ReleaseKeyEvent(now, key));
    }

    private void tick(double delta) {
        now = now.plusNanos((long) (delta * 1e9));
        animationPlayer.update(delta);
        indicatorManager.update(delta);
    }

    private int drawnSize() {
        return drawn.getLast().size();
    }

    private int size() {
        return comboWatcher.getMutatedMode().indicator().size();
    }

    @Test
    void anAnimationReadsAsPressedForItsDuration() {
        load("ripple-animation.duration-millis=100",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.indicator.size=26 | _{rippleanimation} -> 42");
        tap("a");
        assertEquals(42, size());

        tick(0.1);
        assertEquals(26, size());
    }

    @Test
    void startingARunningAnimationStartsItOver() {
        load("ripple-animation.duration-millis=100",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.indicator.size=26 | _{rippleanimation} -> 42");
        tap("a");
        tick(0.06);
        tap("a");
        tick(0.06);
        assertEquals(42, size());

        tick(0.05);
        assertEquals(26, size());
    }

    @Test
    void stoppingAnAnimationEndsIt() {
        load("ripple-animation.duration-millis=100",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.ripple-animation.stop=+b",
                "idle-mode.indicator.size=26 | _{rippleanimation} -> 42");
        tap("a");
        tick(0.01);
        tap("b");
        assertEquals(26, size());
    }

    @Test
    void anAnimationNeedsADuration() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> load("idle-mode.ripple-animation.start=+a"));
        assertTrue(exception.getMessage().contains("ripple-animation.duration-millis"));
    }

    @Test
    void anAnimationHasNoOtherProperty() {
        assertThrows(IllegalArgumentException.class,
                () -> load("ripple-animation.duration=100"));
        assertThrows(IllegalArgumentException.class,
                () -> load("ripple-animation.duration-millis=100",
                        "idle-mode.ripple-animation.restart=+a"));
    }

    @Test
    void keyframesFollowTheAnimation() {
        load("ripple-animation.duration-millis=100",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.indicator.size=26 | _{rippleanimation} -> 0% 10; 100% 36");
        tick(0);
        tap("a");
        tick(0);
        assertEquals(10, drawnSize());
        tick(0.05);
        assertEquals(23, drawnSize());
        tick(0.06);
        assertEquals(26, drawnSize());
    }

    @Test
    void currentIsWhatWasDrawnWhenTheAnimationStarted() {
        load("ripple-animation.duration-millis=100",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.indicator.size=26 | _{rippleanimation} -> 100% 36");
        tick(0);
        tap("a");
        tick(0);
        assertEquals(26, drawnSize());
        tick(0.05);
        assertEquals(31, drawnSize());
    }

    @Test
    void keyframesAtTheSamePercentJump() {
        load("ripple-animation.duration-millis=100",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.indicator.size=26 | _{rippleanimation} -> 0% 10; 50% 10; 50% 40; 100% 40");
        tick(0);
        tap("a");
        tick(0);
        tick(0.049);
        assertEquals(10, drawnSize());
        tick(0.002);
        assertEquals(40, drawnSize());
    }

    @Test
    void keyframesCanBePlacedInMilliseconds() {
        load("ripple-animation.duration-millis=200",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.indicator.size=26 | _{rippleanimation} -> 0% 10; 50ms 20; 100% 20");
        tick(0);
        tap("a");
        tick(0);
        tick(0.025);
        assertEquals(15, drawnSize());
        tick(0.1);
        assertEquals(20, drawnSize());
    }

    @Test
    void anAnimationRepeatsItsCycles() {
        load("ripple-animation.duration-millis=100",
                "ripple-animation.repeat=2",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.indicator.size=26 | _{rippleanimation} -> 0% 10; 100% 30");
        tick(0);
        tap("a");
        tick(0.15);
        assertEquals(20, drawnSize());
        tick(0.06);
        assertEquals(26, drawnSize());
    }

    @Test
    void aLoopRunsUntilItIsStopped() {
        load("ripple-animation.duration-millis=100",
                "ripple-animation.repeat=loop",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.ripple-animation.stop=+b",
                "idle-mode.indicator.size=26 | _{rippleanimation} -> 0% 10; 100% 30");
        tick(0);
        tap("a");
        tick(10.05);
        assertEquals(20, drawnSize());
        tap("b");
        tick(0);
        assertEquals(26, drawnSize());
    }

    @Test
    void anAlternatingAnimationPlaysEveryOtherCycleBackward() {
        load("ripple-animation.duration-millis=100",
                "ripple-animation.repeat=loop",
                "ripple-animation.direction=alternate",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.indicator.size=26 | _{rippleanimation} -> 0% 10; 100% 30");
        tick(0);
        tap("a");
        tick(0.075);
        assertEquals(25, drawnSize());
        tick(0.05);
        assertEquals(25, drawnSize());
        tick(0.05);
        assertEquals(15, drawnSize());
    }

    @Test
    void theEndOfAnAnimationCanStartAnother() {
        load("virtual-key.held=pressed",
                "burst-animation.duration-millis=250",
                "fade-animation.duration-millis=250",
                "cut-animation.duration-millis=40",
                "idle-mode.burst-animation.start=+a",
                "idle-mode.fade-animation.start=^{held} -burstanimation",
                "idle-mode.cut-animation.start=_{held} -burstanimation",
                "idle-mode.indicator.size=26 | _{burstanimation} -> 42 | _{fadeanimation} -> 30 | _{cutanimation} -> 10");
        tap("a");
        tick(0.25);
        assertEquals(10, size());
        tick(0.04);
        assertEquals(26, size());
    }

    @Test
    void keyframesGoForward() {
        assertThrows(IllegalArgumentException.class,
                () -> load("ripple-animation.duration-millis=200",
                        "idle-mode.indicator.size=26 | _{rippleanimation} -> 50% 10; 20% 20"));
        assertThrows(IllegalArgumentException.class,
                () -> load("ripple-animation.duration-millis=200",
                        "idle-mode.indicator.size=26 | _{rippleanimation} -> 50ms 10; 20ms 20"));
    }

    @Test
    void keyframesNeedABranchOnOneAnimation() {
        assertThrows(IllegalArgumentException.class,
                () -> load("idle-mode.indicator.size=0% 10; 100% 36"));
        assertThrows(IllegalArgumentException.class,
                () -> load("idle-mode.indicator.size=26 | +a -> 0% 10; 100% 36"));
        assertThrows(IllegalArgumentException.class,
                () -> load("a-animation.duration-millis=100",
                        "b-animation.duration-millis=100",
                        "idle-mode.indicator.size=26 | _{aanimation banimation} -> 0% 10; 100% 36"));
    }

}
