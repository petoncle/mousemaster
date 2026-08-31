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
        changeMode(modeMap.get(modeName));
    }

    /** The loop renders once a tick, after the mode changes of that iteration. */
    private void changeMode(Mode mode) {
        indicatorManager.modeChanged(mode);
        tick(0);
    }

    private void tick(double delta) {
        indicatorManager.update(delta);
    }

    private List<String> colorsAndSizes() {
        return shown.stream().map(i -> i.color() + " " + i.size()).toList();
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
        changeMode(modes.get(Mode.IDLE_MODE_NAME));
        changeMode(modes.get("normal-mode"));
        tick(0.1);
        assertEquals(Color.parse("#00FF00"), shown.getLast().color(), "still holding the old color");
        shown.clear();

        changeMode(modes.get("other-mode"));
        assertEquals(List.of("#FF0000 20"), colorsAndSizes());
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
