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

/**
 * Effects written the way a configuration would write them (a click ripple, a spinner, an
 * orbit, a keycast, a typewriter, a marquee...), driven through ComboWatcher and
 * IndicatorManager the way AnimationTest does it, checking what the overlay is asked to draw.
 */
class IndicatorEffectsTest {

    private Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private ComboWatcher comboWatcher;
    private ModeMap modeMap;
    private AnimationPlayer animationPlayer;
    private IndicatorManager indicatorManager;
    private IndicatorConfiguration drawnIndicator;
    private boolean hidden;
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
                        hidden = false;
                        anchored.add((Set<String>) args[1]);
                    }
                    else if (method.getName().equals("hideIndicator")) {
                        drawnIndicator = null;
                        hidden = true;
                    }
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
                configuration.initiallyPressedVirtualKeys(),
                configuration.activeKeyboardLayout());
        animationPlayer = new AnimationPlayer(configuration.animationConfigurationByName());
        commandRunner.setAnimationPlayer(animationPlayer);
        indicatorManager = new IndicatorManager(overlay, animationPlayer);
        comboWatcher.setModeListeners(List.of(indicatorManager));
        comboWatcher.modeChanged(modeMap.get(Mode.IDLE_MODE_NAME));
    }

    private void press(String keyName) {
        comboWatcher.keyEvent(new KeyEvent.PressKeyEvent(now, Key.ofName(keyName)));
    }

    private void release(String keyName) {
        comboWatcher.keyEvent(new KeyEvent.ReleaseKeyEvent(now, Key.ofName(keyName)));
    }

    private void tap(String keyName) {
        press(keyName);
        release(keyName);
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

    private IndicatorLayerConfiguration drawn(String layerName) {
        assertNotNull(drawnIndicator, "nothing is drawn");
        IndicatorLayerConfiguration layer = drawnIndicator.layerByName().get(layerName);
        assertNotNull(layer, layerName + " is not drawn");
        assertTrue(layer.enabled(), layerName + " is drawn disabled");
        return layer;
    }

    private static String[] ripple() {
        // Three rings, 40ms apart: the stagger is a hold at the start of each ring's timeline.
        List<String> lines = new ArrayList<>(List.of(
                "ripple-animation.duration-millis=400",
                "idle-mode.ripple-animation.start=+a",
                "idle-mode.indicator.enabled=false"));
        int[] holdPercents = {0, 10, 20};
        for (int i = 0; i < 3; i++) {
            String ring = "idle-mode.ring" + (i + 1) + "-indicator.";
            String hold = holdPercents[i] == 0 ? "" : holdPercents[i] + "% 10; ";
            String holdOpacity = holdPercents[i] == 0 ? "" : holdPercents[i] + "% 0.9; ";
            lines.add(ring + "enabled=false | _{rippleanimation} -> true");
            lines.add(ring + "follow-mouse=false");
            lines.add(ring + "fill-opacity=0");
            lines.add(ring + "stroke-thickness=2");
            lines.add(ring + "size=10 | _{rippleanimation} -> 0% 10; " + hold + "100% 60");
            lines.add(ring + "stroke-opacity=1 | _{rippleanimation} -> 0% 0.9; " + holdOpacity + "100% 0");
        }
        return lines.toArray(new String[0]);
    }

    @Test
    void aRippleIsThreeRingsStaggeredByAHoldAndAnchoredAtTheClick() {
        load(ripple());
        tick(0);
        assertTrue(hidden);
        tap("a");
        tick(0);
        assertEquals(10, drawn("ring1-indicator").size(), 1e-9);
        assertEquals(Set.of("ring1-indicator", "ring2-indicator", "ring3-indicator"),
                anchored.getLast());
        tick(0.2);
        assertEquals(35, drawn("ring1-indicator").size(), 1e-9);
        assertEquals(10 + 50 * (0.5 - 0.1) / 0.9, drawn("ring2-indicator").size(), 1e-6);
        assertEquals(10 + 50 * (0.5 - 0.2) / 0.8, drawn("ring3-indicator").size(), 1e-6);
        assertEquals(0.45, drawn("ring1-indicator").stroke().opacity(), 1e-9);
        tick(0.2);
        assertEquals(60, drawn("ring1-indicator").size(), 1e-9);
        tick(0.01);
        assertTrue(hidden, "the rings are gone with their animation");
    }

    @Test
    void clickingAgainMidRippleStartsItOverAndReanchorsIt() {
        load(ripple());
        tick(0);
        tap("a");
        tick(0.2);
        assertEquals(35, drawn("ring1-indicator").size(), 1e-9);
        tap("a");
        tick(0);
        assertEquals(10, drawn("ring1-indicator").size(), 1e-9);
        assertEquals(Set.of("ring1-indicator", "ring2-indicator", "ring3-indicator"),
                anchored.getLast());
    }

    @Test
    void aSpinnerLoopsWhileItsKeyIsHeldAndStopsOnRelease() {
        load("spin-animation.duration-millis=1000",
                "spin-animation.repeat=loop",
                "idle-mode.spin-animation.start=+b",
                "idle-mode.spin-animation.stop=-b",
                "idle-mode.indicator.enabled=false",
                "idle-mode.arc-indicator.enabled=false | _{spinanimation} -> true",
                "idle-mode.arc-indicator.fill-opacity=0",
                "idle-mode.arc-indicator.stroke-thickness=3",
                "idle-mode.arc-indicator.stroke-length-percent=0.25",
                "idle-mode.arc-indicator.stroke-cap=round",
                "idle-mode.arc-indicator.rotation=0 | _{spinanimation} -> 0% 0; 100% 360");
        tick(0);
        press("b");
        tick(0.25);
        assertEquals(90, drawn("arc-indicator").rotation(), 1e-9);
        assertEquals(0.25, drawn("arc-indicator").stroke().lengthPercent(), 1e-9);
        tick(1.0);
        assertEquals(90, drawn("arc-indicator").rotation(), 1e-9);
        release("b");
        tick(0.01);
        assertTrue(hidden, "the spinner is gone once b is released");
    }

    @Test
    void anOrbitIsADistanceAtAnAngleOnATimeline() {
        load("orbit-animation.duration-millis=1000",
                "orbit-animation.repeat=loop",
                "idle-mode.orbit-animation.start=+c",
                "idle-mode.indicator.enabled=false",
                "idle-mode.dot-indicator.enabled=false | _{orbitanimation} -> true",
                "idle-mode.dot-indicator.size=6",
                "idle-mode.dot-indicator.distance=30",
                "idle-mode.dot-indicator.angle=0 | _{orbitanimation} -> 0% 0; 100% 360",
                "idle-mode.dot-indicator.rotation=0 | _{orbitanimation} -> 0% 0; 100% -360");
        tick(0);
        tap("c");
        tick(0.25);
        assertEquals(30, drawn("dot-indicator").distance(), 1e-9);
        assertEquals(90, drawn("dot-indicator").angle(), 1e-9);
        assertEquals(-90, drawn("dot-indicator").rotation(), 1e-9);
        assertEquals(0, drawn("dot-indicator").x(), 1e-9);
        assertEquals(0, drawn("dot-indicator").y(), 1e-9);
        tick(1.0);
        assertEquals(90, drawn("dot-indicator").angle(), 1e-9);
    }

    @Test
    void aKeycastShowsTheKeyForTheLifeOfItsAnimation() {
        load("key-alias.move=i j k l",
                "keycast-animation.duration-millis=800",
                "idle-mode.keycast-animation.start=+move",
                "idle-mode.indicator.enabled=false",
                "idle-mode.keycast-indicator.enabled=false | _{keycastanimation} -> true",
                "idle-mode.keycast-indicator.shape=rectangle",
                "idle-mode.keycast-indicator.text= | +move -> <b>{move}</b>",
                "idle-mode.keycast-indicator.fill-opacity=0.9 | _{keycastanimation} -> 0% 0.9; 60% 0.9; 100% 0");
        tick(0);
        assertTrue(hidden);
        tap("j");
        tick(0);
        assertEquals("<b>J</b>", drawn("keycast-indicator").text());
        tick(0.4);
        assertEquals("<b>J</b>", drawn("keycast-indicator").text(), "the key stays after its release");
        tick(0.24);
        assertEquals(0.45, drawn("keycast-indicator").fillOpacity(), 1e-9, "80%: halfway from 0.9 to 0");
        tap("l");
        tick(0);
        assertEquals("<b>L</b>", drawn("keycast-indicator").text(), "another key starts over");
        assertEquals(0.9, drawn("keycast-indicator").fillOpacity(), 1e-9);
        tick(0.8);
        tick(0.01);
        assertTrue(hidden);
    }

    @Test
    void aKeycastShowsTheTypedCharacter() {
        load("key-alias.move=questionmark j",
                "keycast-animation.duration-millis=800",
                "idle-mode.keycast-animation.start=+move",
                "idle-mode.indicator.enabled=false",
                "idle-mode.keycast-indicator.enabled=false | _{keycastanimation} -> true",
                "idle-mode.keycast-indicator.shape=rectangle",
                "idle-mode.keycast-indicator.text= | +move -> <b>{move}</b>");
        tick(0);
        press("leftshift");
        tap("/");
        tick(0);
        assertEquals("<b>?</b>", drawn("keycast-indicator").text());
    }

    @Test
    void aKeycastRemapsTheTypedCharacter() {
        load("key-alias.move=questionmark j",
                "keycast-animation.duration-millis=800",
                "idle-mode.keycast-animation.start=+move",
                "idle-mode.indicator.enabled=false",
                "idle-mode.keycast-indicator.enabled=false | _{keycastanimation} -> true",
                "idle-mode.keycast-indicator.shape=rectangle",
                "idle-mode.keycast-indicator.text= | +move -> move.questionmark=help -> <b>{move}</b>");
        tick(0);
        press("leftshift");
        tap("/");
        tick(0);
        assertEquals("<b>help</b>", drawn("keycast-indicator").text());
    }

    @Test
    void aKeycastShowsTheKeyItselfWithoutShift() {
        load("key-alias.move=/ questionmark",
                "keycast-animation.duration-millis=800",
                "idle-mode.keycast-animation.start=+move",
                "idle-mode.indicator.enabled=false",
                "idle-mode.keycast-indicator.enabled=false | _{keycastanimation} -> true",
                "idle-mode.keycast-indicator.shape=rectangle",
                "idle-mode.keycast-indicator.text= | +move -> <b>{move}</b>");
        tick(0);
        tap("/");
        tick(0);
        assertEquals("<b>/</b>", drawn("keycast-indicator").text());
        press("leftshift");
        tap("/");
        tick(0);
        assertEquals("<b>?</b>", drawn("keycast-indicator").text());
    }

    @Test
    void aTypewriterStepsItsTextAndDrawsTheLastKeyframe() {
        load("type-animation.duration-millis=300",
                "idle-mode.type-animation.start=+a",
                "idle-mode.indicator.enabled=false",
                "idle-mode.t-indicator.shape=text",
                "idle-mode.t-indicator.enabled=false | _{typeanimation} -> true",
                "idle-mode.t-indicator.text=C | _{typeanimation} -> 0% C; 33% Co; 66% Cop; 100% Copy");
        tick(0);
        tap("a");
        tick(0);
        assertEquals("C", drawn("t-indicator").text());
        tick(0.1);
        assertEquals("Co", drawn("t-indicator").text());
        tick(0.15);
        assertEquals("Cop", drawn("t-indicator").text());
        tick(0.05);
        assertEquals("Copy", drawn("t-indicator").text(), "the last keyframe is drawn once");
        tick(0.01);
        assertTrue(hidden);
    }

    @Test
    void theEndOfATypewriterCanHoldTheWordWithASecondAnimation() {
        load("type-animation.duration-millis=300",
                "hold-animation.duration-millis=1500",
                "idle-mode.type-animation.start=+a",
                "idle-mode.hold-animation.start=-typeanimation",
                "idle-mode.indicator.enabled=false",
                "idle-mode.t-indicator.shape=text",
                "idle-mode.t-indicator.enabled=false | _{typeanimation | holdanimation} -> true",
                "idle-mode.t-indicator.text=C | _{typeanimation} -> 0% C; 50% Co; 100% Copy | _{holdanimation} -> Copy",
                "idle-mode.t-indicator.fill-opacity=1 | _{holdanimation} -> 0% 1; 80% 1; 100% 0");
        tick(0);
        tap("a");
        tick(0.3);
        tick(0.01);
        assertEquals("Copy", drawn("t-indicator").text());
        assertTrue(animationPlayer.running("holdanimation"));
        tick(1.0);
        assertEquals("Copy", drawn("t-indicator").text());
        assertEquals(1, drawn("t-indicator").fillOpacity(), 1e-9);
        tick(0.35);
        assertEquals(1 - (1.35 - 1.2) / 0.3, drawn("t-indicator").fillOpacity(), 1e-9);
        tick(0.15);
        tick(0.01);
        assertTrue(hidden);
    }

    @Test
    void aColorKeyframeIsMixedNotStepped() {
        load("fade-animation.duration-millis=100",
                "idle-mode.fade-animation.start=+a",
                "idle-mode.indicator.enabled=true",
                "idle-mode.indicator.fill-color=#FF0000 | _{fadeanimation} -> 0% #FF0000; 100% #0000FF");
        tick(0);
        tap("a");
        tick(0.05);
        Color mid = drawn("indicator").fillColor();
        assertNotEquals(Color.parse("#FF0000"), mid);
        assertNotEquals(Color.parse("#0000FF"), mid);
        tick(0.05);
        assertEquals(Color.parse("#0000FF"), drawn("indicator").fillColor());
    }

    @Test
    void aShapeKeyframeJumps() {
        load("morph-animation.duration-millis=100",
                "idle-mode.morph-animation.start=+a",
                "idle-mode.indicator.enabled=true",
                "idle-mode.indicator.shape=circle | _{morphanimation} -> 0% circle; 50% star");
        tick(0);
        tap("a");
        tick(0.01);
        assertEquals(IndicatorShape.CIRCLE, drawn("indicator").shape());
        tick(0.03);
        assertEquals(IndicatorShape.CIRCLE, drawn("indicator").shape(), "40%: still the first keyframe");
        tick(0.01);
        assertEquals(IndicatorShape.STAR, drawn("indicator").shape(), "50%: the keyframe is reached");
    }

    @Test
    void aMarqueeMovesTheDashOffsetOnATimeline() {
        load("marquee-animation.duration-millis=500",
                "marquee-animation.repeat=loop",
                "idle-mode.marquee-animation.start=+a",
                "idle-mode.marquee-animation.stop=-a",
                "idle-mode.indicator.enabled=false",
                "idle-mode.dash-indicator.enabled=false | _{marqueeanimation} -> true",
                "idle-mode.dash-indicator.fill-opacity=0",
                "idle-mode.dash-indicator.stroke-thickness=2",
                "idle-mode.dash-indicator.stroke-dash-length=6",
                "idle-mode.dash-indicator.stroke-dash-gap=4",
                "idle-mode.dash-indicator.stroke-dash-offset=0 | _{marqueeanimation} -> 0% 0; 100% 10");
        tick(0);
        press("a");
        tick(0.25);
        assertEquals(5, drawn("dash-indicator").stroke().dashOffset(), 1e-9);
        assertEquals(6, drawn("dash-indicator").stroke().dashLength(), 1e-9);
        tick(0.5);
        assertEquals(5, drawn("dash-indicator").stroke().dashOffset(), 1e-9);
        release("a");
        tick(0.01);
        assertTrue(hidden);
    }

    @Test
    void aLeadingWaitOfZeroStartsAnAnimationOnEnteringTheMode() {
        load("pulse-animation.duration-millis=100",
                "pulse-animation.repeat=2",
                "idle-mode.to.normal-mode=+leftshift",
                "normal-mode.pulse-animation.start=#{*}-0",
                "normal-mode.indicator.enabled=true",
                "normal-mode.indicator.size=26 | _{pulseanimation} -> 0% 20; 50% 32; 100% 20");
        tick(0);
        comboWatcher.modeChanged(modeMap.get("normal-mode"));
        tick(0);
        tick(0.025);
        assertTrue(animationPlayer.running("pulseanimation"));
        assertEquals(26, drawn("indicator").size(), 1e-9);
        tick(0.125);
        assertEquals(32, drawn("indicator").size(), 1e-9, "second cycle, at its peak");
        tick(0.05);
        assertEquals(20, drawn("indicator").size(), 1e-9, "the last frame of the last cycle");
        tick(0.01);
        assertFalse(animationPlayer.running("pulseanimation"), "two cycles, then rest");
        assertEquals(26, drawn("indicator").size(), 1e-9);
    }

    @Test
    void oneAnimationCanDriveTwoPropertiesWithTheirOwnKeyframes() {
        load("pop-animation.duration-millis=200",
                "idle-mode.pop-animation.start=+a",
                "idle-mode.indicator.enabled=true",
                "idle-mode.indicator.size=26 | _{popanimation} -> 0% 26; 30% 40; 100% 26",
                "idle-mode.indicator.fill-opacity=0.2 | _{popanimation} -> 0% 1; 100% 0.2");
        tick(0);
        tap("a");
        tick(0.06);
        assertEquals(40, drawn("indicator").size(), 1e-9);
        assertEquals(1 - 0.8 * 0.3, drawn("indicator").fillOpacity(), 1e-9);
    }

    @Test
    void severalShapesCanBeShownWhileAKeyIsHeld() {
        load("idle-mode.indicator.enabled=false",
                "idle-mode.star-indicator.enabled=false | _{a} -> true",
                "idle-mode.star-indicator.shape=star",
                "idle-mode.star-indicator.x=-50",
                "idle-mode.arrow-indicator.enabled=false | _{a} -> true",
                "idle-mode.arrow-indicator.shape=path",
                "idle-mode.arrow-indicator.points=0,0 10,0 5,8",
                "idle-mode.arrow-indicator.x=50",
                "idle-mode.cross-indicator.enabled=false | _{a} -> true",
                "idle-mode.cross-indicator.shape=cross",
                "idle-mode.cross-indicator.y=50");
        tick(0);
        assertTrue(hidden);
        press("a");
        tick(0);
        assertEquals(IndicatorShape.STAR, drawn("star-indicator").shape());
        assertEquals(IndicatorShape.PATH, drawn("arrow-indicator").shape());
        assertEquals(3, drawn("arrow-indicator").points().size());
        assertEquals(IndicatorShape.CROSS, drawn("cross-indicator").shape());
        assertEquals(50, drawn("cross-indicator").y(), 1e-9);
        release("a");
        tick(0);
        assertTrue(hidden);
    }

    @Test
    void layersBelongToTheirModeAndVanishOnAModeSwitch() {
        List<String> lines = new ArrayList<>(List.of(ripple()));
        lines.add("idle-mode.to.normal-mode=+leftshift");
        lines.add("normal-mode.indicator.enabled=false");
        load(lines.toArray(new String[0]));
        tick(0);
        tap("a");
        tick(0.1);
        assertEquals(22.5, drawn("ring1-indicator").size(), 1e-9);
        comboWatcher.modeChanged(modeMap.get("normal-mode"));
        tick(0);
        assertTrue(hidden, "the rings are the idle-mode's, not the normal-mode's");
    }

    @Test
    void aRippleCanBeDrawnOverAnotherLayerThatFollowsTheMouse() {
        List<String> lines = new ArrayList<>(List.of(ripple()));
        lines.set(2, "idle-mode.indicator.enabled=true");
        lines.add("idle-mode.indicator.z=1");
        load(lines.toArray(new String[0]));
        tick(0);
        assertTrue(drawn("indicator").followMouse());
        assertFalse(drawnIndicator.layerByName().get("ring1-indicator").enabled());
        tap("a");
        tick(0.1);
        assertTrue(drawn("indicator").followMouse());
        assertFalse(drawn("ring1-indicator").followMouse());
        assertEquals(1, drawn("indicator").z());
        assertEquals(0, drawn("ring1-indicator").z());
    }
}
