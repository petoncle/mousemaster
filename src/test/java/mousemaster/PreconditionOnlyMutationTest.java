package mousemaster;

import mousemaster.platform.ActiveAppFinder;
import mousemaster.platform.MouseController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A precondition-only mutation is applied and reverted as its key toggles, and the ticks in
 * between must leave neither the mutation nor the mutated mode stale.
 */
class PreconditionOnlyMutationTest {

    private final List<Mode> notifiedModes = new ArrayList<>();
    private ComboWatcher comboWatcher;

    private void load(String... lines) {
        Configuration configuration = ConfigurationParser.parse(List.of(lines),
                KeyboardLayout.keyboardLayout("00000409", null));
        ModeMap modeMap = configuration.modeMap();
        ActiveAppFinder noApp = () -> new App("test.exe");
        Clock clock = Instant::now;
        // A precondition key is only recorded as pressed if it is one of these, as in Mousemaster.
        Set<Key> unpressedPreconditionKeys = new HashSet<>();
        Set<Key> pressedPreconditionKeys = new HashSet<>();
        for (Mode mode : modeMap.modes()) {
            for (Combo combo : mode.comboMap().commandsByCombo().keySet()) {
                unpressedPreconditionKeys.addAll(
                        combo.precondition().keyPrecondition().unpressedKeySet());
                pressedPreconditionKeys.addAll(combo.precondition()
                                                    .keyPrecondition()
                                                    .pressedKeyPrecondition()
                                                    .allKeys());
            }
        }
        comboWatcher = new ComboWatcher(null, null, noApp, null, clock,
                unpressedPreconditionKeys, pressedPreconditionKeys,
                false, modeMap, configuration.initiallySetVariables(),
                configuration.virtualKeys(), configuration.initiallyPressedVirtualKeys());
        comboWatcher.setModeListeners(List.of(new ModeListener() {
            @Override
            public void modeChanged(Mode newMode) {
                notifiedModes.add(newMode);
            }

            @Override
            public void modeTimedOut() {
            }
        }));
        comboWatcher.modeChanged(modeMap.get(Mode.IDLE_MODE_NAME));
    }

    private void tick() {
        comboWatcher.update(0.01);
    }

    private void setIdling(boolean idling) {
        comboWatcher.setVirtualKeyPressed(BuiltInVirtualKey.IS_IDLING, idling);
        tick();
    }

    private boolean renderAsCursor() {
        return comboWatcher.getMutatedMode().indicator().renderAsCursor();
    }

    /** Sets the mouse and keyboard keys the way ModeController does, for a left click. */
    private void leftClick() {
        setMouseAndKeyboardKeys(new MouseState(null) {
            @Override
            public boolean moving() {
                return false;
            }

            @Override
            public boolean wheeling() {
                return false;
            }

            @Override
            public boolean leftPressing() {
                return true;
            }

            @Override
            public boolean middlePressing() {
                return false;
            }

            @Override
            public boolean rightPressing() {
                return false;
            }
        });
    }

    private void setMouseAndKeyboardKeys(MouseState mouseState) {
        comboWatcher.updateMouseAndKeyboardKeys(mouseState, new KeyboardState(null) {
            @Override
            public boolean pressingUnhandledKeyInCurrentMode() {
                return false;
            }
        });
    }

    private MouseManager mouseManager() {
        Screen screen = new Screen(new Rectangle(0, 0, 1920, 1080), 96, 1);
        return new MouseManager(new ScreenManager(() -> Set.of(screen)),
                (MouseController) Proxy.newProxyInstance(
                        MouseController.class.getClassLoader(),
                        new Class<?>[] {MouseController.class},
                        (proxy, method, args) -> null));
    }

    private String hexColor() {
        return comboWatcher.getMutatedMode().indicator().hexColor();
    }

    private int size() {
        return comboWatcher.getMutatedMode().indicator().size();
    }

    private double opacity() {
        return comboWatcher.getMutatedMode().indicator().opacity();
    }

    @Test
    void aMutationReachesAnIndicatorProperty() {
        load("idle-mode.indicator.color=#FF0000 | _{isidling} -> #00FF00");
        tick();
        assertEquals("#FF0000", hexColor());

        setIdling(true);
        assertEquals("#00FF00", hexColor());
    }

    @Test
    void mutationFollowsItsVariableAcrossTicks() {
        load("idle-mode.indicator.render-as-cursor=false | _{isidling} -> true");
        tick();
        assertFalse(renderAsCursor());

        setIdling(true);
        assertTrue(renderAsCursor());
        for (int i = 0; i < 5; i++)
            tick();
        assertTrue(renderAsCursor(), "a tick must not revert an applied mutation");

        setIdling(false);
        assertFalse(renderAsCursor());
        for (int i = 0; i < 5; i++)
            tick();
        assertFalse(renderAsCursor(), "a tick must not re-apply a reverted mutation");
    }

    /** ismousepressing and isleftmousepressing are set one after the other, so mutating on
     *  both must reach the listeners once, with everything the click changed. */
    @Test
    void aClickNotifiesOnceWithEveryMutationApplied() {
        load("idle-mode.indicator.size=26 | +ismousepressing -> 50",
                "idle-mode.indicator.color=#FF0000 | +isleftmousepressing -> #00FF00");
        notifiedModes.clear();

        leftClick();
        assertEquals(1, notifiedModes.size(), "notified " + notifiedModes.size() + " times");
        assertEquals(50, notifiedModes.getFirst().indicator().size());
        assertEquals("#00FF00", notifiedModes.getFirst().indicator().hexColor());
    }

    /** A hint mode's click presses and releases within one iteration, and the mouse state is
     *  read once an iteration, so the press has to outlive its release to be seen at all. */
    @Test
    void aClickReleasedWithinOneIterationIsStillPressing() {
        load("idle-mode.indicator.size=26 | _{ismousepressing} -> 50",
                "idle-mode.indicator.color=#FF0000 | _{isleftmousepressing} -> #00FF00",
                "idle-mode.indicator.opacity=1 | _{isidling} -> 0.5");
        MouseManager mouseManager = mouseManager();
        MouseState mouseState = new MouseState(mouseManager);
        setMouseAndKeyboardKeys(mouseState);
        assertEquals(0.5, opacity());

        mouseManager.pressLeft();
        mouseManager.releaseLeft();
        setMouseAndKeyboardKeys(mouseState);
        assertEquals(50, size());
        assertEquals("#00FF00", hexColor());
        assertEquals(1, opacity(), "a click is not idling");

        mouseManager.update(0.01);
        setMouseAndKeyboardKeys(mouseState);
        assertEquals(26, size());
        assertEquals("#FF0000", hexColor());
        assertEquals(0.5, opacity());
    }

    @Test
    void ticksWithoutAChangeDoNotNotifyListeners() {
        load("idle-mode.indicator.render-as-cursor=false | _{isidling} -> true");
        tick();
        notifiedModes.clear();
        for (int i = 0; i < 5; i++)
            tick();
        assertEquals(List.of(), notifiedModes);

        setIdling(true);
        assertEquals(1, notifiedModes.size());
        assertTrue(notifiedModes.getFirst().indicator().renderAsCursor());
    }

    /** A key precondition is refreshed declaratively too, so the key events that neither
     *  press nor release it leave the mutation alone. */
    @Test
    void aKeyPreconditionMutationFollowsThatKeyOnly() {
        load("idle-mode.indicator.render-as-cursor=false | _{leftshift} -> true");
        Instant now = Instant.now();
        assertFalse(renderAsCursor());

        comboWatcher.keyEvent(new KeyEvent.PressKeyEvent(now, Key.leftshift));
        assertTrue(renderAsCursor());

        notifiedModes.clear();
        comboWatcher.keyEvent(new KeyEvent.PressKeyEvent(now, Key.ofName("a")));
        comboWatcher.keyEvent(new KeyEvent.ReleaseKeyEvent(now, Key.ofName("a")));
        assertTrue(renderAsCursor());
        assertEquals(List.of(), notifiedModes, "an unrelated key must not rebuild the mode");

        comboWatcher.keyEvent(new KeyEvent.ReleaseKeyEvent(now, Key.leftshift));
        assertFalse(renderAsCursor());
    }
}
