package mousemaster;

import mousemaster.platform.MouseController;
import mousemaster.platform.Overlay;
import mousemaster.platform.UiAutomation;
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
    private ModeMap modeMap;
    private AnimationPlayer animationPlayer;
    private IndicatorManager indicatorManager;
    private final List<IndicatorLayerConfiguration> drawn = new ArrayList<>();
    private IndicatorConfiguration drawnIndicator;
    private final List<Set<String>> anchored = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private void load(String... lines) {
        Configuration configuration = ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
        modeMap = configuration.modeMap();
        Set<Key> pressedPreconditionKeys = new HashSet<>();
        for (Mode mode : modeMap.modes())
            for (Combo combo : mode.comboMap().commandsByCombo().keySet())
                pressedPreconditionKeys.addAll(combo.precondition()
                                                    .keyPrecondition()
                                                    .pressedKeyPrecondition()
                                                    .allKeys());
        Overlay overlay = (Overlay) Proxy.newProxyInstance(
                Overlay.class.getClassLoader(), new Class<?>[] {Overlay.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setIndicator")) {
                        drawnIndicator = (IndicatorConfiguration) args[0];
                        drawn.add(drawnIndicator.layerByName().get("indicator"));
                        anchored.add((Set<String>) args[1]);
                    }
                    else if (method.getName().equals("hideIndicator"))
                        drawn.add(null);
                    return null;
                });
        ScreenManager screenManager = new ScreenManager(
                () -> Set.of(new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1)));
        HintManager hintManager = new HintManager(
                configuration.positionHistoryConfigurationByName(), screenManager,
                new MouseManager(screenManager, proxy(MouseController.class)), overlay,
                proxy(UiAutomation.class), () -> new App("test.exe"),
                new KeyRedactor(KeyRedaction.NONE), null);
        CommandRunner commandRunner = new CommandRunner(null, null, null) {
            @Override
            public boolean runningAtomicCommand() {
                return false;
            }
        };
        comboWatcher = new ComboWatcher(commandRunner, hintManager, () -> new App("test.exe"), null,
                () -> now, Set.of(), pressedPreconditionKeys, new KeyRedactor(KeyRedaction.NONE), modeMap,
                configuration.initiallySetVariables(), configuration.virtualKeys(),
                configuration.initiallyPressedVirtualKeys());
        animationPlayer = new AnimationPlayer(configuration.animationConfigurationByName());
        commandRunner.setAnimationPlayer(animationPlayer);
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
        comboWatcher.update(delta);
        comboWatcher.updateBuiltInVirtualKeys(
                new MouseState(new MouseManager(null, proxy(MouseController.class))),
                new KeyboardState(null) {
                    @Override
                    public boolean pressingUnhandledKeyInCurrentMode() {
                        return false;
                    }
                }, animationPlayer);
        indicatorManager.update();
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> null);
    }

    private double drawnSize() {
        return drawn.getLast().size();
    }

    private boolean hideCursorEnabled() {
        return animationPlayer.animate(comboWatcher.getMutatedMode(), "hideCursor")
                              .hideCursor()
                              .enabled();
    }

    private double size() {
        return comboWatcher.getMutatedMode().indicator().layerByName().get("indicator").size();
    }

    @Test
    void aLayerIsAnchoredWhenEnabledAndWhenItsKeyframesStartOver() {
        load("ripple-animation.duration-millis=100",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.indicator.enabled=true",
                "idle-mode.ripple-indicator.follow-mouse=false",
                "idle-mode.ripple-indicator.size=10 | _{rippleanimation} -> 0% 10; 100% 40");
        tick(0);
        assertEquals(Set.of("indicator", "ripple-indicator"), anchored.getLast());
        tick(0.01);
        assertEquals(Set.of(), anchored.getLast());
        tap("a");
        tick(0.01);
        assertEquals(Set.of("ripple-indicator"), anchored.getLast());
        tick(0.05);
        assertEquals(Set.of(), anchored.getLast());
        tap("a");
        tick(0.01);
        assertEquals(Set.of("ripple-indicator"), anchored.getLast());
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
        tick(0);
        assertEquals(26, size());
    }

    @Test
    void stoppingAnAnimationReleasesItsKey() {
        load("click-animation.duration-millis=100",
                "rest-animation.duration-millis=80",
                "idle-mode.click-animation.start=+a",
                "idle-mode.click-animation.stop=+b",
                "idle-mode.rest-animation.start=-clickanimation",
                "idle-mode.indicator.size=26 | _{restanimation} -> 30 | _{clickanimation} -> 42");
        tap("a");
        tick(0.01);
        tap("b");
        tick(0);
        assertEquals(30, size());
    }

    @Test
    void aWaitCanStopAnAnimationPartWay() {
        load("virtual-key.held=pressed",
                "click-animation.duration-millis=500",
                "rest-animation.duration-millis=80",
                "idle-mode.click-animation.start=+a",
                "idle-mode.click-animation.stop=_{clickanimation held} wait-250",
                "idle-mode.rest-animation.start=-clickanimation",
                "idle-mode.indicator.size=26 | _{restanimation} -> 30 | _{clickanimation} -> 42");
        tap("a");
        tick(0.2);
        assertEquals(42, size());
        tick(0.06);
        assertEquals(30, size());
    }

    @Test
    void stoppingAnAnimationThatIsNotRunningDoesNothing() {
        load("click-animation.duration-millis=100",
                "rest-animation.duration-millis=80",
                "idle-mode.click-animation.stop=+b",
                "idle-mode.rest-animation.start=-clickanimation",
                "idle-mode.indicator.size=26 | _{restanimation} -> 30");
        tap("b");
        tick(0);
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
        assertEquals(10, drawnSize(), 1e-9);
        tick(0.05);
        assertEquals(23, drawnSize(), 1e-9);
        tick(0.06);
        assertEquals(26, drawnSize(), 1e-9);
    }

    @Test
    void currentIsWhatWasDrawnWhenTheAnimationStarted() {
        load("ripple-animation.duration-millis=100",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.indicator.size=26 | _{rippleanimation} -> 100% 36");
        tick(0);
        tap("a");
        tick(0);
        assertEquals(26, drawnSize(), 1e-9);
        tick(0.05);
        assertEquals(31, drawnSize(), 1e-9);
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
        assertEquals(10, drawnSize(), 1e-9);
        tick(0.002);
        assertEquals(40, drawnSize(), 1e-9);
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
        assertEquals(15, drawnSize(), 1e-9);
        tick(0.1);
        assertEquals(20, drawnSize(), 1e-9);
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
        assertEquals(20, drawnSize(), 1e-9);
        tick(0.06);
        assertEquals(26, drawnSize(), 1e-9);
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
        assertEquals(20, drawnSize(), 1e-9);
        tap("b");
        tick(0);
        assertEquals(26, drawnSize(), 1e-9);
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
        assertEquals(25, drawnSize(), 1e-9);
        tick(0.05);
        assertEquals(25, drawnSize(), 1e-9);
        tick(0.05);
        assertEquals(15, drawnSize(), 1e-9);
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
    void keyframesCanHideTheCursorForPartOfAnAnimation() {
        load("click-animation.duration-millis=100",
                "idle-mode.click-animation.start=+a",
                "idle-mode.hide-cursor.enabled=false | _{clickanimation} -> 0% true; 50% false");
        tap("a");
        assertTrue(hideCursorEnabled());
        tick(0.06);
        assertFalse(hideCursorEnabled());
    }

    @Test
    void keyframesCanShowTheIndicatorForPartOfAnAnimation() {
        load("click-animation.duration-millis=100",
                "idle-mode.click-animation.start=+a",
                "idle-mode.indicator.enabled=false | _{clickanimation} -> 0% true; 50% false");
        tick(0);
        assertNull(drawn.getLast());
        tap("a");
        tick(0);
        assertNotNull(drawn.getLast());
        tick(0.06);
        assertNull(drawn.getLast());
    }

    @Test
    void aRemovedIndicatorAnimationPropertyPointsToKeyframes() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> load("idle-mode.indicator.transition-animation-duration-millis=80"));
        assertTrue(exception.getMessage().contains("keyframes"));
    }

    @Test
    void currentIsTheStateABranchTookOverFromEvenIfItWasNeverDrawn() {
        load("click-animation.duration-millis=100",
                "idle-mode.click-animation.start=+a",
                "idle-mode.indicator.fill-color=#FF0000 | _{clickanimation} -> 0% current | _{b} -> #00FF00");
        tick(0);
        comboWatcher.keyEvent(new KeyEvent.PressKeyEvent(now, Key.ofName("b")));
        tap("a");
        comboWatcher.keyEvent(new KeyEvent.ReleaseKeyEvent(now, Key.ofName("b")));
        tick(0);
        assertEquals(Color.parse("#00FF00"), drawn.getLast().fillColor());
    }

    @Test
    void currentIsTheLayersOwnValueWhenTheModeItCameFromHasNoSuchLayer() {
        load("ripple-animation.duration-millis=100",
                "ripple-animation.repeat=loop",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.to.other-mode=+b",
                "idle-mode.indicator.enabled=true",
                "other-mode.indicator.enabled=true",
                "idle-mode.ripple-indicator.size=20 | _{rippleanimation} -> 100% current");
        tick(0);
        tap("a");
        tick(0.01);
        comboWatcher.modeChanged(modeMap.get("other-mode"));
        tick(0.01);
        comboWatcher.modeChanged(modeMap.get(Mode.IDLE_MODE_NAME));
        tick(0.05);
        assertEquals(20, drawnIndicator.layerByName().get("ripple-indicator").size(), 1e-9);
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
