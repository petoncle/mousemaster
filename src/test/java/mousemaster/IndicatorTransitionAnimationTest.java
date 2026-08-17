package mousemaster;

import mousemaster.platform.Overlay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The indicator eases to the indicator it is transitioned to, over that one's duration and
 * with that one's switch-at, so each direction is animated on its own terms. Here the
 * transition into idle-mode switches at the start, and the one into normal-mode at the end.
 */
class IndicatorTransitionAnimationTest {

    private final List<IndicatorConfiguration> shown = new ArrayList<>();
    private ModeMap modeMap;
    private IndicatorManager indicatorManager;

    @BeforeEach
    void load() {
        modeMap = ConfigurationParser.parse(List.of(
                "idle-mode.to.normal-mode=+leftshift",
                "normal-mode.indicator.size=20",
                "normal-mode.indicator.color=#FF0000",
                "normal-mode.indicator.edge-count=4",
                "normal-mode.indicator.transition-animation-duration-millis=100",
                "idle-mode.indicator.size=40",
                "idle-mode.indicator.color=#00FF00",
                "idle-mode.indicator.edge-count=31",
                "idle-mode.indicator.transition-animation-duration-millis=200",
                "normal-mode.indicator.transition-animation-switch-at=end"),
                KeyboardLayout.keyboardLayout("00000409", null)).modeMap();
        Overlay overlay = (Overlay) Proxy.newProxyInstance(
                Overlay.class.getClassLoader(), new Class<?>[] {Overlay.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setIndicator"))
                        shown.add((IndicatorConfiguration) args[0]);
                    return null;
                });
        indicatorManager = new IndicatorManager(overlay);
        // The indicator that appears first is not transitioned to.
        changeMode("normal-mode");
        shown.clear();
    }

    private void changeMode(String modeName) {
        indicatorManager.modeChanged(modeMap.get(modeName));
    }

    private void tick(double delta) {
        indicatorManager.update(delta);
    }

    private List<String> colorsAndSizes() {
        return shown.stream().map(i -> i.hexColor() + " " + i.size()).toList();
    }

    private List<Integer> sizes() {
        return shown.stream().map(IndicatorConfiguration::size).toList();
    }

    private ModeMap sameSizeWithOvershoot() {
        return sameSizeWithOvershoot(2);
    }

    /** Both modes have the same size, so the swell is the whole size animation. */
    private ModeMap sameSizeWithOvershoot(double overshoot) {
        return ConfigurationParser.parse(List.of(
                "idle-mode.to.normal-mode=+leftshift",
                "normal-mode.indicator.size=20",
                "normal-mode.indicator.transition-animation-duration-millis=200",
                "normal-mode.indicator.transition-animation-easing=1",
                "normal-mode.indicator.inner-outline-thickness=1",
                "normal-mode.indicator.color=#FF0000",
                "normal-mode.indicator.transition-animation-switch-at=end",
                "idle-mode.indicator.color=#00FF00",
                "idle-mode.indicator.size=20",
                "idle-mode.indicator.transition-animation-overshoot=" + overshoot,
                "idle-mode.indicator.transition-animation-duration-millis=100",
                "idle-mode.indicator.transition-animation-easing=1",
                "idle-mode.indicator.inner-outline-thickness=1"),
                KeyboardLayout.keyboardLayout("00000409", null)).modeMap();
    }

    /** Switching at the start takes the new color from the very first frame. */
    @Test
    void theSizeEasesToTheNewIndicatorsSize() {
        changeMode(Mode.IDLE_MODE_NAME);
        tick(0.1);
        tick(0.1);
        tick(0.1);
        assertEquals(List.of("#00FF00 20", "#00FF00 30", "#00FF00 40", "#00FF00 40"), colorsAndSizes());
    }

    /** Morphing from 4 edges to 31 stays on even counts, so the polygon's start angle, which
     *  depends on the parity, does not rock the shape as it rounds off. */
    @Test
    void theEdgeCountMorphsWithoutChangingParity() {
        changeMode(Mode.IDLE_MODE_NAME);
        tick(0.1);
        tick(0.1);
        assertEquals(List.of(4, 18, 31),
                shown.stream().map(IndicatorConfiguration::edgeCount).toList());
    }

    /** Two indicators of the same size still pulse: the swell rises over the 100ms of the
     *  indicator it enters and falls over the 200ms of the one it left. */
    @Test
    void theSizeSwellsThenComesBack() {
        ModeMap sameSize = sameSizeWithOvershoot();
        indicatorManager.modeChanged(sameSize.get("normal-mode"));
        shown.clear();

        indicatorManager.modeChanged(sameSize.get(Mode.IDLE_MODE_NAME));
        tick(0.1);
        tick(0.1);
        tick(0.1);
        assertEquals(List.of(20, 40, 30, 20), sizes());
    }

    /** An overshoot below 1 implodes the indicator instead of swelling it. */
    @Test
    void theSizeShrinksThenComesBackWhenTheOvershootIsBelowOne() {
        ModeMap sameSize = sameSizeWithOvershoot(0.5);
        indicatorManager.modeChanged(sameSize.get("normal-mode"));
        shown.clear();

        indicatorManager.modeChanged(sameSize.get(Mode.IDLE_MODE_NAME));
        tick(0.1);
        tick(0.1);
        tick(0.1);
        assertEquals(List.of(20, 10, 15, 20), sizes());
    }

    /** A click while the indicator is still swollen swells on from the size it has: dropping
     *  back to the resting size for a frame would jump the whole indicator. */
    @Test
    void aSwellStartsFromTheSizeTheLastOneReached() {
        ModeMap sameSize = sameSizeWithOvershoot();
        indicatorManager.modeChanged(sameSize.get("normal-mode"));
        indicatorManager.modeChanged(sameSize.get(Mode.IDLE_MODE_NAME));
        tick(0.05);
        assertEquals(30, shown.getLast().size(), "half way up the swell");
        shown.clear();

        indicatorManager.modeChanged(sameSize.get("normal-mode"));
        indicatorManager.modeChanged(sameSize.get(Mode.IDLE_MODE_NAME));
        assertEquals(List.of(30, 30), sizes());
    }

    /** The swell scales the indicator, so a thickness stays in proportion with the size
     *  instead of looking thinner the bigger the indicator gets. */
    @Test
    void theOutlineThicknessSwellsWithTheSize() {
        ModeMap sameSize = sameSizeWithOvershoot();
        indicatorManager.modeChanged(sameSize.get("normal-mode"));
        shown.clear();

        indicatorManager.modeChanged(sameSize.get(Mode.IDLE_MODE_NAME));
        shown.clear();

        tick(0.1);
        assertEquals(List.of(2d),
                shown.stream().map(i -> i.innerOutline().thickness()).toList());
    }

    /** A transition that interrupts another takes over the color it was holding, so a mode
     *  that switches more often than a transition lasts still gets there. */
    @Test
    void anInterruptedSwitchAtTheEndDoesNotHoldTheOldColorOn() {
        ModeMap modes = ConfigurationParser.parse(List.of(
                "idle-mode.to.normal-mode=+leftshift",
                "normal-mode.indicator.size=20",
                "normal-mode.indicator.color=#FF0000",
                "normal-mode.indicator.transition-animation-duration-millis=200",
                "normal-mode.indicator.transition-animation-switch-at=end",
                "normal-mode.to.other-mode=+leftctrl",
                "other-mode.indicator=normal-mode.indicator",
                "other-mode.indicator.size=24",
                "idle-mode.indicator.size=20",
                "idle-mode.indicator.color=#00FF00"),
                KeyboardLayout.keyboardLayout("00000409", null)).modeMap();
        indicatorManager.modeChanged(modes.get(Mode.IDLE_MODE_NAME));
        indicatorManager.modeChanged(modes.get("normal-mode"));
        tick(0.1);
        assertEquals("#00FF00", shown.getLast().hexColor(), "still holding the old color");
        shown.clear();

        indicatorManager.modeChanged(modes.get("other-mode"));
        assertEquals(List.of("#FF0000 20"), colorsAndSizes());
    }

    /** The color comes back with the indicator: what switches at the end waits for the swell,
     *  which outlasts the 200ms the sizes and opacities are eased over. */
    @Test
    void theSwitchAtTheEndWaitsForTheSwell() {
        ModeMap sameSize = sameSizeWithOvershoot();
        indicatorManager.modeChanged(sameSize.get("normal-mode"));
        indicatorManager.modeChanged(sameSize.get(Mode.IDLE_MODE_NAME));
        indicatorManager.modeChanged(sameSize.get("normal-mode"));
        shown.clear();

        tick(0.1);
        tick(0.1);
        tick(0.1);
        assertEquals(List.of("#00FF00 40", "#00FF00 30", "#FF0000 20"), colorsAndSizes());
    }

    /** A click is over before the next tick, so the swell must run on beyond the transition
     *  that started it, all the way through its fall. */
    @Test
    void theSizeStillSwellsWhenTheTransitionIsUndoneAtOnce() {
        ModeMap sameSize = sameSizeWithOvershoot();
        indicatorManager.modeChanged(sameSize.get("normal-mode"));
        indicatorManager.modeChanged(sameSize.get(Mode.IDLE_MODE_NAME));
        indicatorManager.modeChanged(sameSize.get("normal-mode"));
        shown.clear();

        tick(0.1);
        tick(0.1);
        tick(0.1);
        assertEquals(List.of(40, 30, 20), sizes());
    }

    /** Going back takes the 100ms of normal-mode, not the 200ms of idle-mode, and its
     *  switch at the end keeps the previous color until the transition is over. */
    @Test
    void eachDirectionHasItsOwnDuration() {
        changeMode(Mode.IDLE_MODE_NAME);
        tick(0.2);
        shown.clear();

        changeMode("normal-mode");
        tick(0.05);
        tick(0.05);
        assertEquals(List.of("#00FF00 40", "#00FF00 30", "#FF0000 20"), colorsAndSizes());
    }

    /** Interrupting a transition halfway eases from the indicator it had reached, not from
     *  the one it was heading for. */
    @Test
    void anInterruptedTransitionDoesNotSnap() {
        changeMode(Mode.IDLE_MODE_NAME);
        tick(0.1);
        shown.clear();

        changeMode("normal-mode");
        tick(0.05);
        tick(0.05);
        assertEquals(List.of("#00FF00 30", "#00FF00 25", "#FF0000 20"), colorsAndSizes());
    }
}
